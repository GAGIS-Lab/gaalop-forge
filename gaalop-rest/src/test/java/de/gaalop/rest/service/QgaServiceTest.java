package de.gaalop.rest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gaalop.rest.dto.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class QgaServiceTest {
    @Test
    public void bundledQbitGaCompilesWithFreeParametersInAllWebLanguages() throws Exception {
        GaalopCompileService service = new GaalopCompileService("",
                new CompileHistoryService(new ObjectMapper(), false, "unused-history"));
        for (CodegenPlugin target : new CodegenPlugin[]{CodegenPlugin.JAVA, CodegenPlugin.CPP, CodegenPlugin.PYTHON}) {
            CompileRequest request = new CompileRequest();
            request.setAlgebraPlugins(AlgebraPlugin.ALGEBRA_QGA);
            request.setCodegenPlugins(target);
            request.setOutputMode(OutputMode.CODE_ONLY);
            request.getScript().setFunctionName("qbit_ga");
            request.getScript().setOptimizeCode("?positiveSquare=e1*e1; ?nullSquare=einfx*einfx;"
                    + "?pairing=einfx.e0x; ?scaled=scale*e1;");
            CompileResponse response = service.compile(request);
            assertEquals("200", response.getStatusCode());
            String code = response.getOptimizeResult();
            String suffix = target == CodegenPlugin.JAVA ? "$0" : target == CodegenPlugin.CPP ? "[0]" : "_1";
            assertTrue(code.matches("(?s).*" + java.util.regex.Pattern.quote("positiveSquare" + suffix) + "\\s*=\\s*1(?:\\.0)?[dDfF]?\\s*[;\\n].*"));
            assertTrue(code.matches("(?s).*" + java.util.regex.Pattern.quote("pairing" + suffix) + "\\s*=\\s*(?:\\(-1(?:\\.0)?[dDfF]?\\)|-1(?:\\.0)?[dDfF]?)\\s*[;\\n].*"));
            assertTrue(code.contains("scale"));
            assertNull(response.getQuantumResults());
            assertEquals("", response.getVisualizationCode());
        }
    }
}
