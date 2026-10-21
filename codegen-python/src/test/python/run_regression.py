"""Execute Java-generated Python fixtures with real numeric inputs and NumPy.

Usage: uv run run_regression.py --before DIR --after DIR --report FILE [--service]
"""
import argparse
import ast
import builtins
import inspect
import json
import math
from pathlib import Path
import sys

import numpy as np
import importlib.util

assert importlib.util.find_spec("sympy") is None, "Numeric regression must run without SymPy installed"

COUNTS = {"programs": 0, "numeric_oracle_checks": 0, "numeric_baseline_comparisons": 0,
          "numeric_import_guards": 0,
          "domain_checks": 0, "before_errors": [], "before_array_defects": []}

def reference(function, value):
    if function == "abs":
        return abs(value)
    if function == "invert":
        return 1.0 / value
    if function == "fact":
        value = int(value) if int(value) == value else value
        return math.factorial(value)
    return getattr(math, function)(value)

def load(path, guard=False):
    text = path.read_text(encoding="utf-8-sig")
    tree = ast.parse(text, filename=str(path))
    main = next(node for node in tree.body if isinstance(node, ast.FunctionDef) and node.name == path.stem)
    imports = []
    def importing(name, *args, **kwargs):
        imports.append(name)
        if name == "sympy" or name.startswith("sympy.") or name == "numpy" or name.startswith("numpy."):
            raise ModuleNotFoundError("Blocked optional dependency: " + name)
        return builtins.__import__(name, *args, **kwargs)
    namespace = {"__builtins__": dict(vars(builtins), __import__=importing)} if guard else {}
    exec(compile(tree, str(path), "exec"), namespace)
    function = namespace[path.stem]
    return function, main, imports

def items(value):
    if isinstance(value, (tuple, list, np.ndarray)):
        for child in value:
            yield from items(child)
    else:
        yield value

def equal(actual, expected, exact=False):
    if isinstance(expected, (tuple, list, np.ndarray)):
        assert isinstance(actual, type(expected)), (type(actual), type(expected))
        assert len(actual) == len(expected), (actual, expected)
        if isinstance(expected, np.ndarray):
            assert actual.shape == expected.shape
            if exact:
                assert actual.dtype == expected.dtype, (actual.dtype, expected.dtype)
        for left, right in zip(actual, expected):
            equal(left, right, exact)
    elif expected is None:
        assert actual is None
    else:
        if exact:
            assert type(actual) is type(expected), (type(actual), type(expected))
            assert actual == expected, (actual, expected)
        else:
            assert abs(complex(actual) - complex(expected)) <= 2e-12 * max(1, abs(complex(expected))), (actual, expected)

def invoke(function, values):
    return function(**{name: values[name] for name in inspect.signature(function).parameters})

def expected_direct(name, values, main):
    array = name in {"intermediate", "independent_array", "names_array", "nested_array", "constant_array"}
    stem = name.removesuffix("_array").removesuffix("_scalar")
    a, b, g = (values.get(key) for key in ("alpha", "beta", "gamma"))
    coefficients = {}
    if stem.startswith("f_"):
        coefficients = {"result": {0: reference(stem[2:], values["x"])}}
    elif stem == "arithmetic":
        coefficients = {"result": {0: abs(values["x"]) + 2.0}}
    elif stem == "exp_only":
        coefficients = {"result": {0: reference("exp", values["x"])}}
    elif stem == "intermediate":
        m0, m3, m5 = 1.0, a, b + g
        if array:
            # Coefficients are read back from NumPy storage. In particular,
            # float32 inputs become float64 when stored in the numeric array.
            dtype = complex if any(np.iscomplexobj(v) for v in (m3, m5)) else float
            m0, m3, m5 = np.array([m0, m3, m5], dtype=dtype)
        coefficients = {"first": {0: 2.0, 2: m3 * m5 + g, 6: m0 + m3},
                        "second": {1: 3.0, 4: m3 - b * g}, "constant": {0: 7.0, 4: m0}}
    elif stem == "independent":
        coefficients = {"left": {0: a, 3: 2.0}, "right": {0: 1.0, 5: reference("sin", b)}}
    elif stem == "names":
        total = 0.0
        for key in sorted(values):
            total += values[key]
        coefficients = {"result": {0: reference("sin", total) + abs(values["abs"]), 3: values["pow"] ** 3.0}}
    elif stem == "nested":
        coefficients = {"result": {0: 1.0, 3: reference("exp", reference("sin", a+b)) + g**3.0},
                        "other": {0: reference("sqrt", b)}}
    elif stem == "constant":
        coefficients = {"constant": {3: 7.0}}
    elif stem == "custom_tuple":
        coefficients = {"result": {0: a, 3: b}}
    elif stem == "many_cse":
        total = a
        for _ in range(20): total += b
        coefficients = {"result": {0: total + a, 3: b}, "constant": {0: 7.0}}
    elif stem == "empty":
        return None
    else:
        raise AssertionError("Missing independent formula for " + name)
    returned = next(node.value for node in ast.walk(main) if isinstance(node, ast.Return))
    def result(node):
        if isinstance(node, ast.Tuple):
            return tuple(result(child) for child in node.elts)
        assert isinstance(node, ast.Name), ast.dump(node)
        if array:
            data = coefficients[node.id]
            dtype = complex if any(np.iscomplexobj(v) for v in data.values()) else float
            output = np.zeros(8, dtype=dtype)
            for index, value in data.items():
                output[index] = value
            return output
        variable, index = node.id.rsplit("_", 1)
        return coefficients[variable][int(index)]
    return result(returned)

def evaluate_body(main, values):
    """Reference interpreter for unchanged coefficient assignments from REST output.

    Calls are mapped independently to stdlib math, never to generated helpers.
    """
    env = dict(values)
    def evaluate(node):
        if isinstance(node, ast.Constant): return node.value
        if isinstance(node, ast.Name): return env[node.id]
        if isinstance(node, ast.UnaryOp) and isinstance(node.op, ast.USub): return -evaluate(node.operand)
        if isinstance(node, ast.Tuple): return tuple(evaluate(child) for child in node.elts)
        if isinstance(node, ast.BinOp):
            left, right = evaluate(node.left), evaluate(node.right)
            if isinstance(node.op, ast.Add): return left + right
            if isinstance(node.op, ast.Sub): return left - right
            if isinstance(node.op, ast.Mult): return left * right
            if isinstance(node.op, ast.Div): return left / right
            if isinstance(node.op, ast.Pow): return left ** right
        if isinstance(node, ast.Call):
            function = node.func.id if isinstance(node.func, ast.Name) else node.func.attr
            function = function.removeprefix("_gaalop_")
            operands = [evaluate(child) for child in node.args]
            if function == "pow": return pow(*operands)
            return reference(function, operands[0])
        raise AssertionError(ast.dump(node))
    for statement in main.body:
        if isinstance(statement, ast.Assign): env[statement.targets[0].id] = evaluate(statement.value)
        elif isinstance(statement, ast.Return): return evaluate(statement.value)
    raise AssertionError("REST output missing return")

def numeric_values(name, inputs):
    if not inputs:
        return [{}]
    function = name.removeprefix("f_").removeprefix("service_")
    if len(inputs) == 1 and inputs[0] == "x":
        if function == "fact": samples = [0, 1, 5, 3.0, np.int64(4), np.float32(4)]
        elif function == "invert": samples = [-2.0, .5, 2.0, np.float32(.5), np.float64(2)]
        elif function == "log": samples = [.5, 1.0, 2.0, np.float32(.5), np.float64(2)]
        elif function == "sqrt": samples = [0.0, .5, 4.0, np.float32(.5), np.float64(2)]
        else: samples = [-.5, 0.0, .5, np.float32(.25), np.float64(.75)]
        if function in {"abs", "arithmetic"}: samples.append(1 + 2j)
        return [{"x": value} for value in samples]
    samples = [(.3, .6, 1.2), (0.0, 0.0, 0.0), (-.3, .6, -.7),
               (np.float32(.3), np.float32(.6), np.float32(1.2)),
               (np.float32(.3), .6, np.float64(1.2))]
    # Complex arithmetic is supported; stdlib real math keeps its original domain.
    if name.startswith(("intermediate", "qca_multi", "cga_multi", "qca_mixed", "qga_mixed")):
        samples.append((.3 + .2j, .6 - .1j, 1.2 + .3j))
    return [{key: (sample[("alpha", "beta", "gamma").index(key)] if key in {"alpha", "beta", "gamma"} else sample[0]) for key in inputs} for sample in samples]

def normalize_expression(node):
    """Verify the REST coefficient math stayed unchanged apart from bindings."""
    class Normalize(ast.NodeTransformer):
        def visit_Call(self, item):
            self.generic_visit(item)
            function = item.func.id if isinstance(item.func, ast.Name) else item.func.attr
            function = function.removeprefix("_gaalop_")
            if function == "pow": return ast.BinOp(item.args[0], ast.Pow(), item.args[1])
            item.func = ast.Name(id=function, ctx=ast.Load())
            return item
    return ast.dump(Normalize().visit(ast.parse(ast.unparse(node))))

def check_program(before, after, service, strict_baseline):
    assert "sympy" not in after.read_text(encoding="utf-8-sig").lower(), after
    function, main, _ = load(after)
    name = after.stem
    inputs = list(inspect.signature(function).parameters)
    oracle = (lambda values: evaluate_body(main, values)) if service else (lambda values: expected_direct(name, values, main))
    old = None
    try:
        if before.exists(): old, old_main, _ = load(before)
    except (SyntaxError, NameError) as error:
        COUNTS["before_errors"].append({"name": name, "phase": "load", "error": str(error)})
    if old is not None:
        if not any(isinstance(node, ast.Return) for node in ast.walk(old_main)):
            COUNTS["before_errors"].append({"name": name, "phase": "load", "error": "Truncated output has no return"})
            old = None
        if service:
            old_assignments = [normalize_expression(node) for node in old_main.body if isinstance(node, ast.Assign)]
            new_assignments = [normalize_expression(node) for node in main.body if isinstance(node, ast.Assign)]
            assert old_assignments == new_assignments, "Changed coefficient expressions: " + name
    for values in numeric_values(name, inputs):
        actual = invoke(function, values)
        expected = oracle(values)
        equal(actual, expected)
        COUNTS["numeric_oracle_checks"] += 1
        if old is not None:
            try:
                previous = invoke(old, values)
            except Exception as error:
                COUNTS["before_errors"].append({"name": name, "phase": "numeric", "error": type(error).__name__ + ": " + str(error)})
            else:
                if (name.endswith("_array") or name == "intermediate") and not strict_baseline:
                    try: equal(actual, previous, exact=True)
                    except AssertionError:
                        COUNTS["before_array_defects"].append(name)
                        continue
                else:
                    equal(actual, previous, exact=True)
                COUNTS["numeric_baseline_comparisons"] += 1
        if "numpy" not in after.read_text(encoding="utf-8-sig"):
            guarded, _, imports = load(after, guard=True)
            equal(invoke(guarded, values), actual, exact=True)
            assert not any(module.startswith(("numpy", "sympy")) for module in imports)
            COUNTS["numeric_import_guards"] += 1
    COUNTS["programs"] += 1

def domain_checks(after):
    for name in ("f_fact", "service_fact"):
        path = after / (name + ".py")
        if not path.exists(): continue
        function, _, _ = load(path)
        for value in (-1, 2.5):
            try: function(value)
            except (TypeError, ValueError): pass
            else: raise AssertionError("Invalid factorial was accepted: " + str(value))
            COUNTS["domain_checks"] += 1

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--before", type=Path, required=True)
    parser.add_argument("--after", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--service", action="store_true")
    parser.add_argument("--strict-baseline", action="store_true", help="Require every baseline array result to match, including values, dtype and shape")
    args = parser.parse_args()
    for after in sorted(args.after.glob("*.py")):
        print("Checking", after.stem, flush=True)
        check_program(args.before / after.name, after, args.service, args.strict_baseline)
    domain_checks(args.after)
    report = {"python": sys.version, "numpy": np.__version__, "sympy_installed": False, **COUNTS}
    args.report.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps({key: value if not isinstance(value, list) else len(value) for key, value in report.items()}, indent=2))

if __name__ == "__main__":
    main()
