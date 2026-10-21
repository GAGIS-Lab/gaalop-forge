"""Run with python -S: generated scalar code must work without site packages."""
import ast
import importlib.util
import json
from pathlib import Path
import sys

assert importlib.util.find_spec("numpy") is None
assert importlib.util.find_spec("sympy") is None
passed = []
for directory in sys.argv[1:]:
    for path in sorted(Path(directory).glob("*.py")):
        code = path.read_text(encoding="utf-8-sig")
        if "import numpy" in code:
            continue
        tree = ast.parse(code)
        function = next(node for node in tree.body if isinstance(node, ast.FunctionDef) and node.name == path.stem)
        namespace = {}
        exec(compile(tree, str(path), "exec"), namespace)
        values = [3 if path.stem.endswith("fact") else .25 for _ in function.args.args]
        namespace[path.stem](*values)
        passed.append(str(path))
print(json.dumps({"stdlib_only_programs": len(passed), "numpy_available": False, "sympy_available": False, "programs": passed}, indent=2))
