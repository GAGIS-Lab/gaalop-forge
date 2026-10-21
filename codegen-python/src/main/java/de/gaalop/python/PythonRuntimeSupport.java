package de.gaalop.python;

import de.gaalop.UsedVariablesVisitor;
import de.gaalop.cfg.*;
import de.gaalop.dfg.MathFunction;
import de.gaalop.dfg.Variable;
import java.util.*;

/** Self-contained, demand-driven helpers for scalar math and coefficient arrays. */
final class PythonRuntimeSupport {
    private final Set<String> reserved = new HashSet<>();
    private final Map<String, String> names = new LinkedHashMap<>();
    private final Set<MathFunction> functions = EnumSet.noneOf(MathFunction.class);
    private boolean arrays;

    PythonRuntimeSupport(ControlFlowGraph graph, String functionName, boolean useArrays) {
        reserved.add(functionName);
        reserved.addAll(graph.getInputs());
        reserved.addAll(graph.getLocals());
        reserved.addAll(graph.getScalars());
        reserved.addAll(graph.getOutputs());
        graph.accept(new EmptyControlFlowVisitor() {
            @Override
            public void visit(AssignmentNode node) {
                reserve(node.getVariable());
                UsedVariablesVisitor used = new UsedVariablesVisitor();
                node.getValue().accept(used);
                for (Variable variable : used.getVariables()) reserve(variable);
                super.visit(node);
            }
            private void reserve(Variable variable) {
                reserved.add(variable.getName());
                reserved.add(variable.getNewName(graph, useArrays));
            }
        });
    }

    String name(String key) {
        if (!names.containsKey(key)) {
            String base = "_gaalop_" + key;
            String candidate = base;
            for (int suffix = 1; reserved.contains(candidate); suffix++) candidate = base + "_" + suffix;
            reserved.add(candidate);
            names.put(key, candidate);
        }
        return names.get(key);
    }

    String math(MathFunction function) {
        functions.add(function);
        if (function == MathFunction.ABS || function == MathFunction.FACT) return name(function.toString());
        return name("math") + "." + numericName(function);
    }

    String numpy() { arrays = true; return name("numpy"); }
    String setComponent() { arrays = true; return name("set_component"); }

    String render() {
        StringBuilder imports = new StringBuilder();
        StringBuilder helpers = new StringBuilder();
        boolean math = functions.stream().anyMatch(function -> function != MathFunction.ABS);
        if (math) line(imports, 0, "import math as " + name("math"));
        if (arrays) line(imports, 0, "import numpy as " + name("numpy"));
        if (functions.contains(MathFunction.ABS)) line(imports, 0, "from builtins import abs as " + name("abs"));
        if (functions.contains(MathFunction.FACT)) {
            line(imports, 0, "from builtins import int as " + name("int"));
            line(helpers, 0, "def " + name("fact") + "(value):");
            // GAALOP constants are doubles. Accept integral numeric values,
            // while math.factorial still rejects fractional/negative values.
            line(helpers, 1, "integer = " + name("int") + "(value)");
            line(helpers, 1, "if integer == value:");
            line(helpers, 2, "value = integer");
            line(helpers, 1, "return " + name("math") + ".factorial(value)");
            line(helpers, 0, "");
        }
        if (arrays) {
            String np = name("numpy");
            line(helpers, 0, "def " + name("set_component") + "(array, index, value):");
            line(helpers, 1, "if " + np + ".iscomplexobj(value):");
            line(helpers, 2, "dtype = " + np + ".result_type(array.dtype, value)");
            line(helpers, 2, "if dtype != array.dtype:");
            line(helpers, 3, "array = array.astype(dtype)");
            line(helpers, 1, "array[index] = value");
            line(helpers, 1, "return array");
            line(helpers, 0, "");
        }
        return imports.append('\n').append(helpers).toString();
    }

    private static String numericName(MathFunction function) {
        switch (function) {
            case FACT: return "factorial";
            case ACOS: case ASIN: case ATAN: case CEIL: case COS: case EXP:
            case FLOOR: case LOG: case SIN: case SQRT: case TAN:
                return function.toString();
            default: throw new IllegalArgumentException("Unsupported Python scalar function: " + function);
        }
    }

    private static void line(StringBuilder code, int indentation, String text) {
        for (int i = 0; i < indentation; i++) code.append("    ");
        code.append(text).append('\n');
    }
}
