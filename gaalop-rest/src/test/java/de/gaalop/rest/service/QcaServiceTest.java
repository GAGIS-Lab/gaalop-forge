package de.gaalop.rest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gaalop.rest.dto.*;
import java.util.regex.Pattern;
import org.junit.Test;
import static org.junit.Assert.*;

public class QcaServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final GaalopCompileService service = new GaalopCompileService("",
            new CompileHistoryService(mapper, false, "unused-history"));

    private CompileRequest request() throws Exception {
        return mapper.readValue("{\"algebraPlugins\":\"ALGEBRA_QCA\",\"algebraDimension\":2,"
                + "\"codegenPlugins\":\"JAVA\",\"outputMode\":\"CODE_ONLY\","
                + "\"script\":{\"functionName\":\"qcatest\","
                + "\"optimizeCode\":\"?half=f2.f2T; ?sign=ei2*ei2; ?imaginarySquare=(ei1*ei2)*(ei1*ei2);\"}}", CompileRequest.class);
    }

    @Test
    public void compilesQcaThroughServiceToMultipleLanguages() throws Exception {
        for (CodegenPlugin target : new CodegenPlugin[]{CodegenPlugin.JAVA, CodegenPlugin.CPP, CodegenPlugin.PYTHON}) {
            CompileRequest request = request();
            request.setCodegenPlugins(target);
            CompileResponse response = service.compile(request);
            assertEquals("200", response.getStatusCode());
            assertScalarAssignment(response.getOptimizeResult(), target, "half", "0.5");
            assertScalarAssignment(response.getOptimizeResult(), target, "sign", "1");
            assertScalarAssignment(response.getOptimizeResult(), target, "imaginarySquare", "-1");
            assertEquals("", response.getVisualizationCode());
            assertTrue(mapper.writeValueAsString(request).contains("\"algebraDimension\":2"));
        }
    }

    private void assertScalarAssignment(String code, CodegenPlugin target, String name, String value) {
        String suffix = target == CodegenPlugin.JAVA ? "$0" : target == CodegenPlugin.CPP ? "[0]" : "_1";
        Pattern assignment = Pattern.compile("\\b" + Pattern.quote(name + suffix)
                + "\\s*=\\s*" + Pattern.quote(value) + "(?:\\.0)?[dDfF]?(?=[;\\s])");
        assertTrue(name + " must equal " + value + ":\n" + code, assignment.matcher(code).find());
    }

    @Test
    public void omittedDimensionDefaultsToOne() throws Exception {
        CompileRequest request = request();
        request.setAlgebraDimension(null);
        request.getScript().setOptimizeCode("?half=f1.f1T;");
        assertTrue(service.compile(request).getOptimizeResult().contains("0.5"));
    }

    @Test
    public void invalidDimensionAndVisualizationAreRejected() throws Exception {
        for (int n : new int[]{-1, 0, 4, Integer.MAX_VALUE}) {
            CompileRequest request = request();
            request.setAlgebraDimension(n);
            assertThrows(IllegalArgumentException.class, () -> service.compile(request));
        }
        for (CodegenPlugin target : new CodegenPlugin[]{CodegenPlugin.GANJA, CodegenPlugin.VISUALIZER, CodegenPlugin.VIS2D}) {
            CompileRequest request = request();
            request.setCodegenPlugins(target);
            assertThrows(IllegalArgumentException.class, () -> service.compile(request));
        }
    }

    @Test
    public void cgaStillCompilesWithBundledTables() throws Exception {
        CompileRequest request = request();
        request.setAlgebraPlugins(AlgebraPlugin.ALGEBRA_CGA);
        request.getScript().setOptimizeCode("?s=e1*e1;");
        assertEquals("200", service.compile(request).getStatusCode());
    }
}
