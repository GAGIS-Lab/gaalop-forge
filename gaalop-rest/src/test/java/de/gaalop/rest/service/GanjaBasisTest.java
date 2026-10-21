package de.gaalop.rest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gaalop.rest.dto.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class GanjaBasisTest {
    @Test
    public void fourPointSphereUsesOrthogonalCoordinatesOnlyForVisualization() throws Exception {
        GaalopCompileService service = new GaalopCompileService("",
                new CompileHistoryService(new ObjectMapper(), false, "unused-history"));
        CompileRequest request = new CompileRequest();
        request.setAlgebraPlugins(AlgebraPlugin.ALGEBRA_CGA);
        request.setCodegenPlugins(CodegenPlugin.JAVA);
        request.setOutputMode(OutputMode.CODE_AND_VISUALIZATION);
        request.getScript().setOptimizeCode("?P1=createPoint(0,0,1); P2=createPoint(0,1,0);"
                + "P3=createPoint(1,0,0); P4=createPoint(0,-1,0); S=*(P1^P2^P3^P4); ?m=S*einf*S;");
        request.getScript().setMultivectorsVisualized(":P1; :P2; :P3; :P4; :S;");
        CompileResponse combined = service.compile(request);
        String code = combined.getVisualizationCode();
        // Each unit point has spatial norm 1 and em coefficient 1: null norm.
        for (String name : new String[]{"P1", "P2", "P3", "P4"}) {
            assertTrue(code, code.contains(name + "[5] = 1.0; // em"));
            assertFalse(code, code.contains(name + "[4] = 0.5;"));
        }
        // S = 2 ep represents a sphere centered at zero with radius squared 1.
        assertTrue(code, code.contains("S[4] = 2.0; // ep"));
        assertFalse(code, code.contains("// einf"));
        request.setOutputMode(OutputMode.CODE_ONLY);
        assertEquals(combined.getOptimizeResult(), service.compile(request).getOptimizeResult());
        request.setCodegenPlugins(CodegenPlugin.GANJA);
        assertTrue(service.compile(request).getVisualizationCode().contains("S[4] = 2.0; // ep"));
    }
}
