package de.gaalop.testbenchTbaGapp.tba;

import de.gaalop.CompilerFacade;
import de.gaalop.InputFile;
import de.gaalop.dfg.MultivectorComponent;
import de.gaalop.dfg.Variable;
import de.gaalop.testbenchTbaGapp.tba.framework.CFGInterpreter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/** Executable examples for the QCA code-generation workspace, not quantum circuits. */
public class QcaWebExamplesTest {
    @Test
    public void fermionicRelationsExample() throws Exception {
        for (int qubits = 1; qubits <= 3; qubits++) {
            for (boolean cse : new boolean[]{false, true}) {
                CFGInterpreter result = compile(qubits, cse,
                        "?half=f1.f1T; ?anti=f1*f1T+f1T*f1; ?nil=f1*f1; ?wedge=f1^f1T;");
                assertScalar(result, "half", 0.5);
                assertScalar(result, "anti", 1);
                assertScalar(result, "nil", 0);
                Map<Integer, Double> wedge = output(result, "wedge");
                assertEquals(1, wedge.size());
                assertFalse(wedge.containsKey(0));
                assertEquals(1, wedge.values().iterator().next(), 0);
            }
        }
    }

    @Test
    public void commutingProjectorsExample() throws Exception {
        for (int qubits = 2; qubits <= 3; qubits++) {
            for (boolean cse : new boolean[]{false, true}) {
                CFGInterpreter result = compile(qubits, cse,
                        "p1=f1*f1T; p2=f2*f2T; p=p1*p2; "
                        + "?cross=f1*f2T+f2T*f1; ?commutator=p1*p2-p2*p1; "
                        + "?idempotence=p*p-p; ?projector=p;");
                assertScalar(result, "cross", 0);
                assertScalar(result, "commutator", 0);
                assertScalar(result, "idempotence", 0);
                Map<Integer, Double> projector = output(result, "projector");
                assertEquals(4, projector.size());
                assertEquals(0.25, projector.get(0), 0);
                assertEquals(2, Collections.frequency(projector.values(), 0.5));
                assertEquals(1, Collections.frequency(projector.values(), 1.0));
            }
        }
    }

    @Test
    public void auxiliaryBivectorExample() throws Exception {
        for (int qubits = 1; qubits <= 3; qubits++) {
            for (boolean cse : new boolean[]{false, true}) {
                CFGInterpreter result = compile(qubits, cse,
                        "j=ei1*ei2; ?square1=ei1*ei1; ?square2=ei2*ei2; "
                        + "?imaginarySquare=j*j; ?reverseSum=j+~j;");
                assertScalar(result, "square1", 1);
                assertScalar(result, "square2", 1);
                assertScalar(result, "imaginarySquare", -1);
                assertScalar(result, "reverseSum", 0);
            }
        }
    }

    private CFGInterpreter compile(int qubits, boolean cse, String script) throws Exception {
        CFGInterpreter interpreter = new CFGInterpreter(new HashMap<Variable, Double>());
        de.gaalop.tba.Plugin optimizer = new de.gaalop.tba.Plugin();
        optimizer.setOptGCSE(cse);
        CompilerFacade compiler = new CompilerFacade(
                new de.gaalop.clucalc.input.Plugin().createCodeParser(),
                new de.gaalop.globalSettings.Plugin().createGlobalSettingsStrategy(),
                graph -> {}, new de.gaalop.algebra.Plugin().createAlgebraStrategy(),
                optimizer.createOptimizationStrategy(), graph -> {
                    graph.accept(interpreter);
                    return Collections.emptySet();
                }, "qca", qubits, true, "");
        compiler.compile(new InputFile("qca_web_example.clu", script));
        return interpreter;
    }

    private Map<Integer, Double> output(CFGInterpreter result, String name) {
        Map<Integer, Double> components = new HashMap<>();
        result.getMapVariables().forEach((variable, value) -> {
            if (variable.getName().equals(name) && variable instanceof MultivectorComponent && value != 0) {
                components.put(((MultivectorComponent) variable).getBladeIndex(), value);
            }
        });
        return components;
    }

    private void assertScalar(CFGInterpreter result, String name, double expected) {
        Map<Integer, Double> actual = output(result, name);
        if (expected == 0) {
            assertTrue(name + ": " + actual, actual.isEmpty());
        } else {
            assertEquals(name, 1, actual.size());
            assertNotNull(name, actual.get(0));
            assertEquals(name, expected, actual.get(0), 1e-9);
        }
    }
}
