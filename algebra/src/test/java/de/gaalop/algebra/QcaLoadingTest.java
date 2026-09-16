package de.gaalop.algebra;

import de.gaalop.OptimizationException;
import de.gaalop.cfg.ControlFlowGraph;
import de.gaalop.dfg.BaseVector;
import org.junit.Test;
import static org.junit.Assert.*;

public class QcaLoadingTest {
    @Test
    public void registersBothQuantumAlgebras() {
        assertTrue(Plugin.getDefinedAlgebras().stream().anyMatch(a -> a.id.equals("qca")));
        assertTrue(Plugin.getDefinedAlgebras().stream().anyMatch(a -> a.id.equals("qga")));
        assertTrue(Plugin.getDefinedAlgebras().stream().anyMatch(a -> a.id.equals("cga")));
    }

    @Test
    public void basisPrefixSurvivesCopyAndEquality() {
        BaseVector f = new BaseVector("f", "1T");
        assertEquals("f1T", f.toString());
        assertEquals(f, f.copy());
        assertNotEquals(f, new BaseVector("1T"));
        assertEquals(new BaseVector(1), new BaseVector("1"));
    }

    @Test(expected = OptimizationException.class)
    public void missingMacrosFailCompilation() throws Exception {
        Plugin plugin = new Plugin();
        plugin.atLeastOneQuestionSignedRequired = false;
        ControlFlowGraph graph = new ControlFlowGraph();
        graph.algebraName = "qca";
        graph.dimension = 1;
        graph.asRessource = false;
        graph.algebraBaseDirectory = "missing-qca-test-directory";
        plugin.createAlgebraStrategy().transform(graph);
    }
}
