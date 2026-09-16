package de.gaalop.algebra;

import de.gaalop.InputFile;
import de.gaalop.OptimizationException;
import de.gaalop.cfg.ControlFlowGraph;
import de.gaalop.cfg.ExpressionStatement;
import de.gaalop.dfg.Expression;
import de.gaalop.dfg.FloatConstant;
import de.gaalop.dfg.MacroCall;
import de.gaalop.dfg.Variable;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class MacroInliningTest {
    @Test
    public void undefinedDualProducesCompilationError() throws Exception {
        assertUndefinedMacro("qga", "?result = *e1;", "dual", "qga");
    }

    @Test
    public void undefinedValueMacroProducesCompilationError() throws Exception {
        assertUndefinedMacro("cga", "?result = missing(1);", "missing", "1 argument");
    }

    @Test
    public void wrongMacroArityProducesCompilationError() throws Exception {
        assertUndefinedMacro("qca", "?result = createPoint(a, b, c);", "createPoint", "3 arguments");
    }

    @Test
    public void rendererCallDoesNotStopDualInlining() throws Exception {
        ControlFlowGraph graph = graphWithRenderer(new Variable("e1"));
        new Plugin().createAlgebraStrategy().transform(graph);
        assertEquals(0, MacroCallCounter.countMacroCallsInGraph(graph));
        assertEquals(1, graph.unknownMacros.size());
        assertEquals("DrawLine", graph.unknownMacros.getFirst().macroCall.getName());
    }

    @Test
    public void rendererArgumentsAreStillInlined() throws Exception {
        ControlFlowGraph graph = graphWithRenderer(new MacroCall("Dual", Collections.singletonList(new Variable("e1"))));
        new Plugin().createAlgebraStrategy().transform(graph);
        assertEquals(0, MacroCallCounter.countMacroCallsInGraph(graph));
        assertEquals(1, graph.unknownMacros.size());
    }

    @Test
    public void undefinedRendererArgumentProducesCompilationError() throws Exception {
        ControlFlowGraph graph = graphWithRenderer(new MacroCall("missing", Collections.singletonList(new FloatConstant(1))));
        assertUndefinedMacro(graph, "missing", "1 argument");
    }

    private void assertUndefinedMacro(String algebra, String source, String... details) throws Exception {
        assertUndefinedMacro(parse(algebra, source), details);
    }

    private void assertUndefinedMacro(ControlFlowGraph graph, String... details) throws Exception {
        try {
            new Plugin().createAlgebraStrategy().transform(graph);
            fail("Expected an actionable compilation error");
        } catch (OptimizationException exception) {
            for (String detail : details) {
                assertTrue(exception.getMessage(), exception.getMessage().contains(detail));
            }
        }
    }

    private ControlFlowGraph graphWithRenderer(Expression argument) throws Exception {
        ControlFlowGraph graph = parse("cga", "?result = *e1;");
        graph.getStartNode().insertAfter(new ExpressionStatement(graph,
                new MacroCall("DrawLine", Arrays.asList(argument, new Variable("e2")))));
        return graph;
    }

    private ControlFlowGraph parse(String algebra, String source) throws Exception {
        ControlFlowGraph graph = new de.gaalop.clucalc.input.Plugin().createCodeParser()
                .parseFile(new InputFile("macro-test.clu", source));
        graph.algebraName = algebra;
        graph.dimension = 1;
        graph.asRessource = true;
        return graph;
    }
}
