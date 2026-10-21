package de.gaalop.matlab;

import de.gaalop.InputFile;
import de.gaalop.OutputFile;
import de.gaalop.cfg.AssignmentNode;
import de.gaalop.cfg.ControlFlowGraph;
import de.gaalop.cfg.SequentialNode;
import de.gaalop.cfg.StoreResultNode;
import de.gaalop.dfg.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import static org.junit.Assert.*;

public class MatlabSymbolicInitializationTest {
    @Test
    public void constantBeforeSymbolicComponentUsesInputPrototype() throws Exception {
        Fixture f = new Fixture("theta");
        f.assign(component("mix", 0), number(1));
        f.assign(component("mix", 3), variable("theta"));
        String code = generate("mixed", f.output("mix"));
        assertTrue(code, code.indexOf("mix = theta([]);") >= 0);
        assertTrue(code, code.indexOf("mix = theta([]);") < code.indexOf("mix(1) = 1.0;"));
        assertFalse(code, code.contains("zeros("));
    }

    @Test
    public void intermediateArraysAndScalarTemporariesPropagateInputDependencies() throws Exception {
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
        String code = generate("intermediate", f.output("first", "second", "constant"));
        for (String array : new String[]{"middle", "first", "second", "constant"}) {
            for (String input : new String[]{"alpha", "beta", "gamma"}) {
                assertTrue(code, code.contains(array + " = " + input + "([]);"));
            }
        }
        assertTrue("Reading a constant component of a symbolic array still returns sym", code.contains("constant = alpha([]);"));
        assertFalse("Scalar CSE temporaries are assigned directly", code.contains("cseValue = alpha([])"));
    }

    @Test
    public void unrelatedInputsDoNotPromoteIndependentOutputs() throws Exception {
        Fixture f = new Fixture("alpha", "beta");
        f.assign(component("left", 0), number(1));
        f.assign(component("left", 3), variable("alpha"));
        f.assign(component("right", 0), number(2));
        f.assign(component("right", 5), variable("beta"));
        String code = generate("independent", f.output("left", "right"));
        assertTrue(code, code.contains("left = alpha([]);"));
        assertTrue(code, code.contains("right = beta([]);"));
        assertFalse(code, code.contains("left = beta([]);"));
        assertFalse(code, code.contains("right = alpha([]);"));
    }

    @Test
    public void helperAndMatlabFunctionNamesCannotShadowUserInputs() throws Exception {
        Fixture f = new Fixture("isa", "sym", "zeros", "gaalopIsSym");
        f.assign(component("mix", 0), number(1));
        f.assign(component("mix", 3), new Addition(new Addition(variable("isa"), variable("sym")),
                new Addition(variable("zeros"), variable("gaalopIsSym"))));
        String code = generate("names", f.output("mix"));
        assertTrue(code, code.contains("gaalopIsSym_1 = @isa;"));
        assertTrue(code, code.contains("gaalopIsSym_1(isa, 'sym')"));
        assertTrue(code, code.contains("mix = sym([]);")); // sym is the input, used by indexing, not a constructor.
        assertTrue(code, code.contains("mix = zeros([]);"));
    }

    @Test
    public void symbolicFirstStillLeavesNumericClassAndShapeToOriginalAssignments() throws Exception {
        Fixture f = new Fixture("alpha");
        f.assign(component("mix", 0), variable("alpha"));
        f.assign(component("mix", 3), number(2));
        String code = generate("symbolic_first", f.output("mix"));
        assertTrue(code, code.contains("mix(1) = alpha;"));
        assertTrue(code, code.contains("mix(4) = 2.0;"));
        assertFalse(code, code.contains("else\n"));
    }

    @Test
    public void constantOnlyFunctionsNeedNoSymbolicToolboxOrTypeHelpers() throws Exception {
        Fixture f = new Fixture();
        f.assign(component("constant", 3), number(7));
        String code = generate("constant_only", f.output("constant"));
        assertFalse(code, code.contains("@isa"));
        assertFalse(code, code.contains("sym"));
        assertTrue(code, code.contains("constant(4) = 7.0;"));
    }

    @Test
    public void emptyFunctionsRemainValid() throws Exception {
        String code = generate("empty", new Fixture().graph);
        assertFalse(code, code.contains("@isa"));
        assertTrue(code, code.endsWith("end\n"));
    }

    private String generate(String name, ControlFlowGraph graph) throws Exception {
        String suffix = System.getProperty("gaalop.matlab.fixtureSuffix", "");
        graph.setSource(new InputFile(name + suffix + ".clu", ""));
        OutputFile output = new Plugin().createCodeGenerator().generate(graph).iterator().next();
        String directory = System.getProperty("gaalop.matlab.fixtureDir");
        if (directory != null) {
            Path root = Paths.get(directory);
            Files.createDirectories(root);
            Files.write(root.resolve(output.getName()), output.getContent().getBytes(StandardCharsets.UTF_8));
        }
        return output.getContent();
    }

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
