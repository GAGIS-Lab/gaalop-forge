package de.gaalop.rest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gaalop.garamon.qra.QraStateProjection;
import de.gaalop.rest.dto.AlgebraPlugin;
import de.gaalop.rest.dto.CodegenPlugin;
import de.gaalop.rest.dto.CompileRequest;
import de.gaalop.rest.dto.CompileResponse;
import de.gaalop.rest.dto.OutputMode;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class QcaExpansionTest {
    private static final double EPSILON = 1e-10;
    private final GaalopCompileService service = new GaalopCompileService("",
            new CompileHistoryService(new ObjectMapper(), false, "unused"));

    @Test
    public void fourAndFiveQubitBasisStatesPreserveOrderingAndComplexPhase() throws Exception {
        for (int n : new int[]{4, 5}) {
            StringBuilder source = new StringBuilder(vacuum(n));
            StringBuilder selected = new StringBuilder();
            for (int state = 0; state < (1 << n); state++) {
                source.append("?psi").append(state).append("=(cos(theta)+j*sin(theta))*")
                        .append(ket(n, state)).append("Id;");
                selected.append(":psi").append(state).append(';');
            }
            CompileRequest request = request(n, source.toString(), "theta=0.37;", selected.toString());
            Map<String, QraStateProjection.Result> results = service.compile(request).getQuantumResults();
            assertEquals(1 << n, results.size());
            for (int state = 0; state < (1 << n); state++) {
                QraStateProjection.Result result = results.get("psi" + state);
                assertEquals(n, result.nqubits);
                for (int index = 0; index < (1 << n); index++) {
                    assertEquals(index == state ? Math.cos(0.37) : 0, result.real[index], EPSILON);
                    assertEquals(index == state ? Math.sin(0.37) : 0, result.imaginary[index], EPSILON);
                    assertEquals(index == state ? 1 : 0, result.probabilities[index], EPSILON);
                    assertEquals(String.format("%" + n + "s", Integer.toBinaryString(index)).replace(' ', '0'), result.labels[index]);
                }
                assertEquals(1, result.totalProbability, EPSILON);
                assertEquals(0, result.residualNorm, EPSILON);
            }
        }
    }

    @Test
    public void controlledGatesAndRelativePhaseMatchIndependentGhzStateVector() throws Exception {
        for (int n : new int[]{4, 5}) {
            StringBuilder source = new StringBuilder(vacuum(n));
            for (int k = 1; k <= n; k++) {
                source.append('P').append(k).append("=f").append(k).append("*f").append(k).append("T;")
                        .append('N').append(k).append("=f").append(k).append("T*f").append(k).append(';')
                        .append('Z').append(k).append("=P").append(k).append("-N").append(k).append(';');
            }
            source.append("H1=(P1+f1+f1T-N1)/sqrt(2); state1=H1*Id;");
            for (int k = 2; k <= n; k++) {
                source.append('C').append(k).append("=P1+N1*");
                for (int prior = 1; prior < k; prior++) source.append('Z').append(prior).append('*');
                source.append("(f").append(k).append("+f").append(k).append("T); state")
                        .append(k).append("=C").append(k).append("*state").append(k - 1).append(';');
            }
            source.append("?psi=(P1+j*N1)*state").append(n).append(';');
            CompileRequest request = request(n, source.toString(), "", ":psi;");
            request.setOutputMode(OutputMode.VISUALIZATION_ONLY);
            QraStateProjection.Result result = service.compile(request).getQuantumResults().get("psi");
            for (int index = 0; index < (1 << n); index++) {
                assertEquals(index == 0 ? 1 / Math.sqrt(2) : 0, result.real[index], EPSILON);
                assertEquals(index == (1 << n) - 1 ? 1 / Math.sqrt(2) : 0, result.imaginary[index], EPSILON);
                assertEquals(index == 0 || index == (1 << n) - 1 ? 0.5 : 0, result.probabilities[index], EPSILON);
            }
            assertEquals(1, result.totalProbability, EPSILON);
            assertEquals(0, result.residualNorm, EPSILON);
        }
    }

    @Test
    public void matlabKeepsFreeParametersWhileVisualizationUsesAssignments() throws Exception {
        for (int n : new int[]{4, 5}) {
            CompileRequest request = request(n, vacuum(n) + "?psi=cos(theta/2)*Id+j*sin(theta/2)*"
                    + ket(n, (1 << n) - 1) + "Id;", "theta=0;", ":psi;");
            request.setCodegenPlugins(CodegenPlugin.MATLAB);
            CompileResponse zero = service.compile(request);
            assertTrue(zero.getOptimizeResult(), zero.getOptimizeResult().contains("(theta)"));
            assertEquals(1, zero.getQuantumResults().get("psi").probabilities[0], EPSILON);
            request.getScript().setVariableAssignments("theta=1.5707963267948966;");
            CompileResponse half = service.compile(request);
            assertEquals(zero.getOptimizeResult(), half.getOptimizeResult());
            assertEquals(0.5, half.getQuantumResults().get("psi").probabilities[0], EPSILON);
            assertEquals(0.5, half.getQuantumResults().get("psi").probabilities[(1 << n) - 1], EPSILON);
            request.setOutputMode(OutputMode.CODE_ONLY);
            request.getScript().setVariableAssignments("");
            assertEquals(zero.getOptimizeResult(), service.compile(request).getOptimizeResult());
        }
    }

    private CompileRequest request(int n, String source, String assignments, String selected) {
        CompileRequest request = new CompileRequest();
        request.setAlgebraPlugins(AlgebraPlugin.ALGEBRA_QCA);
        request.setAlgebraDimension(n);
        request.setCodegenPlugins(CodegenPlugin.JAVA);
        request.setOutputMode(OutputMode.CODE_AND_VISUALIZATION);
        request.getScript().setFunctionName("qca_expanded");
        request.getScript().setOptimizeCode(source);
        request.getScript().setVariableAssignments(assignments);
        request.getScript().setMultivectorsVisualized(selected);
        return request;
    }

    private String vacuum(int n) {
        StringBuilder source = new StringBuilder("j=ei1*ei2; Id=");
        for (int k = 1; k <= n; k++) {
            if (k > 1) source.append('*');
            source.append('f').append(k).append("*f").append(k).append('T');
        }
        return source.append(';').toString();
    }

    private String ket(int n, int state) {
        StringBuilder source = new StringBuilder();
        for (int k = 1; k <= n; k++) {
            if ((state & (1 << (n - k))) != 0) source.append('f').append(k).append("T*");
        }
        return source.toString();
    }
}
