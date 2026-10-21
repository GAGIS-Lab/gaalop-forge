package de.gaalop.rest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gaalop.rest.dto.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PythonCodeGenerationServiceTest {
    private final GaalopCompileService service = new GaalopCompileService("",
            new CompileHistoryService(new ObjectMapper(), false, "unused"));

    @Test
    public void freeParameterMathUsesCompleteMappingAcrossAlgebras() throws Exception {
        for (AlgebraPlugin algebra : new AlgebraPlugin[]{AlgebraPlugin.ALGEBRA_QCA, AlgebraPlugin.ALGEBRA_CGA, AlgebraPlugin.ALGEBRA_QGA}) {
            CompileRequest request = request(algebra, "?first=exp(alpha)+log(beta)+tan(gamma); ?second=asin(alpha)+acos(alpha)+atan(alpha)+ceil(beta)+floor(beta)+fact(gamma);");
            String code = service.compile(request).getOptimizeResult();
            for (String function : new String[]{"exp", "log", "tan", "asin", "acos", "atan", "ceil", "floor", "factorial"}) {
                assertTrue(code, code.contains("." + function + "("));
            }
            assertTrue(code, code.contains("return ("));
            assertFalse(code, code.contains("numpy"));
            assertFalse(code, code.contains("sympy"));
        }
    }

    @Test
    public void scalarCseTemporariesCannotBreakMultipleOutputGeneration() throws Exception {
        CompileRequest request = request(AlgebraPlugin.ALGEBRA_QCA,
                "v=alpha+beta; ?first=1+sin(v)*f1+cos(v)*f2; ?second=2+sin(v)*f1T+gamma*cos(v)*f2T;");
        request.getOptimization().setCse(true);
        String code = service.compile(request).getOptimizeResult();
        assertTrue(code, code.contains("return ("));
        assertTrue(code, code.contains("_gaalop_math.sin("));
        assertTrue(code, code.contains("_gaalop_math.cos("));
        assertFalse(code, code.contains("numpy"));
    }

    @Test
    public void arithmeticOutputsHaveNoUnusedThirdPartyOrMathImports() throws Exception {
        String code = service.compile(request(AlgebraPlugin.ALGEBRA_QCA, "?mix=1+theta*f1; ?other=alpha*theta;")).getOptimizeResult();
        assertFalse(code, code.contains("numpy"));
        assertFalse(code, code.contains("sympy"));
        assertFalse(code, code.contains("import math"));
    }

    private CompileRequest request(AlgebraPlugin algebra, String source) {
        CompileRequest request = new CompileRequest();
        request.setAlgebraPlugins(algebra);
        request.setAlgebraDimension(2);
        request.setCodegenPlugins(CodegenPlugin.PYTHON);
        request.setOutputMode(OutputMode.CODE_ONLY);
        request.getScript().setFunctionName("python_numeric");
        request.getScript().setOptimizeCode(source);
        return request;
    }
}
