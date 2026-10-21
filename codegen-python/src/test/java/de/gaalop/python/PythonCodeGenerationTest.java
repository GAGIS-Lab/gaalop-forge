package de.gaalop.python;

import de.gaalop.InputFile;
import de.gaalop.OutputFile;
import de.gaalop.cfg.*;
import de.gaalop.dfg.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PythonCodeGenerationTest {
    @Test
    public void everyMathFunctionHasNumericMappingWithoutSymbolicDependencies() throws Exception {
        List<String> failures = new ArrayList<>();
        for (MathFunction function : MathFunction.values()) {
            Fixture f = new Fixture("x");
            f.assign(component("result", 0), new MathFunctionCall(variable("x"), function));
            String code = generate("f_" + function, f.output("result"), false);
            if (function == MathFunction.INVERT) {
                if (!code.contains("1.0 / (x)")) failures.add(function.toString());
            } else if (function != MathFunction.ABS) {
                String numeric = function == MathFunction.FACT ? "factorial" : function.toString();
                if (!code.contains("." + numeric + "(")) failures.add(function.toString());
            }
            assertFalse(code, code.contains("sympy"));
            assertFalse(code, code.contains("is_symbolic"));
        }
        assertTrue(failures.toString(), failures.isEmpty());
    }

    @Test
    public void arithmeticAndAbsDoNotRequireNumpyMathOrSympy() throws Exception {
        Fixture f = new Fixture("x");
        f.assign(component("result", 0), new Addition(new MathFunctionCall(variable("x"), MathFunction.ABS), number(2)));
        String code = generate("arithmetic", f.output("result"), false);
        assertFalse(code, code.contains("numpy"));
        assertFalse(code, code.contains("sympy"));
        assertFalse(code, code.contains("import math"));
        assertFalse(code, code.contains("typing"));
    }

    @Test
    public void onlyUsedMathFunctionsAreEmitted() throws Exception {
        Fixture f = new Fixture("x");
        f.assign(component("result", 0), new MathFunctionCall(variable("x"), MathFunction.EXP));
        String code = generate("exp_only", f.output("result"), false);
        assertTrue(code, code.contains("import math"));
        assertTrue(code, code.contains("_gaalop_math.exp(x)"));
        assertFalse(code, code.contains("sympy"));
        assertFalse(code, code.contains("numpy"));
    }

    @Test
    public void intermediateArraysKeepAllComponentsAndPromoteOnAssignment() throws Exception {
        ControlFlowGraph graph = intermediate();
        String code = generate("intermediate", graph, true);
        assertEquals(code, 1, occurrences(code, "middle = " + numpyAlias(code) + ".zeros("));
        assertEquals(code, 1, occurrences(code, "first = " + numpyAlias(code) + ".zeros("));
        assertFalse(code, code.contains("cseValue = " + numpyAlias(code) + ".zeros("));
        assertFalse(code, code.contains("is_symbolic"));
        assertTrue(code, code.contains(".iscomplexobj(value)"));
    }

    @Test
    public void scalarModePreservesIntermediateAndMultipleOutputLayout() throws Exception {
        String code = generate("intermediate_scalar", intermediate(), false);
        assertFalse(code, code.contains("numpy"));
        assertTrue(code, code.contains("middle_0 = 1.0"));
        assertTrue(code, code.contains("from typing import Tuple"));
    }

    @Test
    public void independentOutputsAndParameterFirstWorkInBothModes() throws Exception {
        for (boolean arrays : new boolean[]{false, true}) {
            Fixture f = new Fixture("alpha", "beta");
            f.assign(component("left", 0), variable("alpha"));
            f.assign(component("left", 3), number(2));
            f.assign(component("right", 0), number(1));
            f.assign(component("right", 5), new MathFunctionCall(variable("beta"), MathFunction.SIN));
            generate("independent" + (arrays ? "_array" : ""), f.output("left", "right"), arrays);
        }
    }

    @Test
    public void supportNamesAvoidInputsLocalsAndFunctionNames() throws Exception {
        for (boolean arrays : new boolean[]{false, true}) {
            Fixture f = new Fixture("math", "np", "abs", "pow", "int", "_gaalop_math", "_gaalop_numpy", "_gaalop_sin", "_gaalop_is_symbolic", "_gaalop_set_component");
            Expression sum = number(0);
            for (String input : f.graph.getInputs()) sum = new Addition(sum, variable(input));
            f.assign(component("result", 0), new Addition(new MathFunctionCall(sum, MathFunction.SIN), new MathFunctionCall(variable("abs"), MathFunction.ABS)));
            f.assign(component("result", 3), new Exponentiation(variable("pow"), number(3)));
            String code = generate("names" + (arrays ? "_array" : ""), f.output("result"), arrays);
            assertFalse(code, code.contains("pow(pow,"));
            assertTrue(code, code.contains("import math as _gaalop_math_1"));
        }
    }

    @Test
    public void constantsAndEmptyFunctionsRemainValid() throws Exception {
        for (boolean arrays : new boolean[]{false, true}) {
            Fixture f = new Fixture();
            f.assign(component("constant", 3), number(7));
            generate("constant" + (arrays ? "_array" : ""), f.output("constant"), arrays);
            String code = generate("empty" + (arrays ? "_array" : ""), new Fixture().graph, arrays);
            assertTrue(code, code.contains("return None"));
            assertFalse(code, code.contains("numpy"));
        }
    }

    @Test
    public void nestedMathAndPowersAcceptMultipleNumericInputs() throws Exception {
        for (boolean arrays : new boolean[]{false, true}) {
            Fixture f = new Fixture("alpha", "beta", "gamma");
            Expression trig = new MathFunctionCall(new Addition(variable("alpha"), variable("beta")), MathFunction.SIN);
            f.assign(variable("temporary"), new MathFunctionCall(trig, MathFunction.EXP));
            f.assign(component("result", 0), number(1));
            f.assign(component("result", 3), new Addition(variable("temporary"), new Exponentiation(variable("gamma"), number(3))));
            f.assign(component("other", 0), new MathFunctionCall(variable("beta"), MathFunction.SQRT));
            generate("nested" + (arrays ? "_array" : ""), f.output("result", "other"), arrays);
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void generationErrorsDoNotReturnPartialPrograms() throws Exception {
        Fixture f = new Fixture();
        f.assign(component("result", 0), new OuterProduct(number(1), number(2)));
        generate("unsupported", f.output("result"), false);
    }

    @Test
    public void existingLargeScalarTemporarySetsKeepTheirReturnOrder() throws Exception {
        Fixture f = new Fixture("alpha", "beta");
        Expression previous = variable("alpha");
        for (int i = 0; i < 20; i++) {
            Variable temporary = variable("temporary_" + i);
            f.assign(temporary, new Addition(previous, variable("beta")));
            previous = temporary;
        }
        f.assign(component("result", 0), new Addition(previous, variable("alpha")));
        f.assign(component("result", 3), variable("beta"));
        f.assign(component("constant", 0), number(7));
        String code = generate("many_cse", f.output("result", "constant"), false);
        assertTrue(code, code.contains("return ("));
    }

    @Test
    public void customTupleReturnAnnotationsKeepTheirRequiredImport() throws Exception {
        Fixture f = new Fixture("alpha", "beta");
        f.assign(component("result", 0), variable("alpha"));
        f.assign(component("result", 3), variable("beta"));
        f.graph.addReturnDefinition(new ReturnDefinition(new String[]{"result"}, "Tuple[float, float]", "(<0>, <3>)"));
        String code = generate("custom_tuple", f.output("result"), false);
        assertTrue(code, code.contains("from typing import Tuple"));
    }

    private static ControlFlowGraph intermediate() {
        Fixture f = new Fixture("alpha", "beta", "gamma");
        f.assign(variable("cseValue"), new Addition(variable("beta"), variable("gamma")));
        f.assign(component("middle", 0), number(1));
        f.assign(component("middle", 3), variable("alpha"));
        f.assign(component("middle", 5), variable("cseValue"));
        f.assign(component("first", 0), number(2));
        f.assign(component("first", 2), new Addition(new Multiplication(component("middle", 3), component("middle", 5)), variable("gamma")));
        f.assign(component("first", 6), new Addition(component("middle", 0), component("middle", 3)));
        f.assign(component("second", 1), number(3));
        f.assign(component("second", 4), new Subtraction(component("middle", 3), new Multiplication(variable("beta"), variable("gamma"))));
        f.assign(component("constant", 0), number(7));
        f.assign(component("constant", 4), component("middle", 0));
        return f.output("first", "second", "constant");
    }

    private String generate(String name, ControlFlowGraph graph, boolean arrays) throws Exception {
        graph.setSource(new InputFile(name + ".clu", ""));
        Plugin plugin = new Plugin();
        plugin.setUseArrays(arrays);
        OutputFile output = plugin.createCodeGenerator().generate(graph).iterator().next();
        String directory = System.getProperty("gaalop.python.fixtureDir");
        if (directory != null) {
            Path root = Paths.get(directory);
            Files.createDirectories(root);
            Files.write(root.resolve(output.getName()), output.getContent().getBytes(StandardCharsets.UTF_8));
        }
        return output.getContent();
    }

    private static String numpyAlias(String code) {
        for (String line : code.split("\n")) if (line.startsWith("import numpy as ")) return line.substring("import numpy as ".length());
        return "np";
    }
    private static int occurrences(String text, String part) { return (text.length() - text.replace(part, "").length()) / part.length(); }
    private static MultivectorComponent component(String name, int index) { return new MultivectorComponent(name, index); }
    private static Variable variable(String name) { return new Variable(name); }
    private static FloatConstant number(double value) { return new FloatConstant(value); }

    private static final class Fixture {
        final ControlFlowGraph graph = new ControlFlowGraph();
        SequentialNode tail = graph.getStartNode();
        Fixture(String... inputs) {
            for (String input : inputs) graph.addInputVariable(variable(input));
            Expression[] blades = new Expression[8];
            for (int i = 0; i < blades.length; i++) blades[i] = number(i);
            graph.getAlgebraDefinitionFile().blades = blades;
            graph.getAlgebraDefinitionFile().blades2 = blades;
        }
        void assign(Variable lhs, Expression rhs) {
            AssignmentNode node = new AssignmentNode(graph, lhs, rhs);
            tail.insertAfter(node);
            tail = node;
        }
        ControlFlowGraph output(String... names) {
            for (String name : names) {
                StoreResultNode node = new StoreResultNode(graph, variable(name));
                tail.insertAfter(node);
                tail = node;
            }
            return graph;
        }
    }
}
