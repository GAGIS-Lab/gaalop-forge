package de.gaalop.testbenchTbaGapp.tba;

import de.gaalop.CompilerFacade;
import de.gaalop.InputFile;
import de.gaalop.dfg.MultivectorComponent;
import de.gaalop.dfg.Variable;
import de.gaalop.testbenchTbaGapp.tba.framework.CFGInterpreter;
import java.util.Collections;
import java.util.HashMap;
import org.junit.Test;
import static org.junit.Assert.*;

public class QcaCompileTest {
    @Test
    public void compileAndEvaluateWithAndWithoutCse() throws Exception {
        for (boolean cse : new boolean[]{false, true}) {
            for (int n = 1; n <= 3; n++) {
                HashMap<Variable, Double> inputs = new HashMap<>();
                inputs.put(new Variable("x"), 3.0);
                CFGInterpreter interpreter = new CFGInterpreter(inputs);
                de.gaalop.tba.Plugin optimizer = new de.gaalop.tba.Plugin();
                optimizer.setOptGCSE(cse);
                CompilerFacade compiler = new CompilerFacade(
                        new de.gaalop.clucalc.input.Plugin().createCodeParser(),
                        new de.gaalop.globalSettings.Plugin().createGlobalSettingsStrategy(),
                        graph -> {}, new de.gaalop.algebra.Plugin().createAlgebraStrategy(),
                        optimizer.createOptimizationStrategy(), graph -> {
                            graph.accept(interpreter);
                            return Collections.emptySet();
                        }, "qca", n, true, "");
                compiler.compile(new InputFile("qca.clu",
                        "?nil=f1*f1; ?half=f1.f1T; ?anti=f1*f1T+f1T*f1; "
                        + "?sign=ei2*ei2; ?imaginarySquare=(ei1*ei2)*(ei1*ei2); "
                        + "?scaled=x*(f" + n + ".f" + n + "T); "
                        + "p=f1*f1T; ?projector=p*p-p; ?basis=2*f1-2*f1T;"));
                assertScalar(interpreter, "nil", 0);
                assertScalar(interpreter, "half", 0.5);
                assertScalar(interpreter, "anti", 1);
                assertScalar(interpreter, "sign", 1);
                assertScalar(interpreter, "imaginarySquare", -1);
                assertScalar(interpreter, "scaled", 1.5);
                assertScalar(interpreter, "projector", 0);
                assertEquals(2.0, interpreter.getMapVariables().get(new MultivectorComponent("basis", 3)), 0);
                assertEquals(-2.0, interpreter.getMapVariables().get(new MultivectorComponent("basis", 4)), 0);
            }
        }
    }

    private void assertScalar(CFGInterpreter interpreter, String name, double expected) {
        Double actual = interpreter.getMapVariables().get(new MultivectorComponent(name, 0));
        if (expected != 0) assertNotNull(name + ": " + interpreter.getMapVariables(), actual);
        assertEquals(expected, actual == null ? 0 : actual, 1e-9);
        interpreter.getMapVariables().forEach((variable, value) -> {
            if (variable.getName().equals(name) && variable instanceof MultivectorComponent
                    && ((MultivectorComponent) variable).getBladeIndex() != 0) assertEquals(0, value, 1e-9);
        });
    }
}
