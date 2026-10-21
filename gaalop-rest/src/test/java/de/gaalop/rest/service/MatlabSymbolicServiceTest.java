package de.gaalop.rest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gaalop.rest.dto.AlgebraPlugin;
import de.gaalop.rest.dto.CodegenPlugin;
import de.gaalop.rest.dto.CompileRequest;
import de.gaalop.rest.dto.OutputMode;
import org.junit.Test;
import static org.junit.Assert.*;

public class MatlabSymbolicServiceTest {
    private final GaalopCompileService service = new GaalopCompileService("",
            new CompileHistoryService(new ObjectMapper(), false, "unused"));

    @Test
    public void mixedNumericAndFreeCoefficientsUseSymbolicInitializationAcrossAlgebras() throws Exception {
        for (AlgebraPlugin algebra : new AlgebraPlugin[]{AlgebraPlugin.ALGEBRA_QCA, AlgebraPlugin.ALGEBRA_CGA, AlgebraPlugin.ALGEBRA_QGA}) {
            String basis = algebra == AlgebraPlugin.ALGEBRA_QCA ? "f1" : "e1";
            CompileRequest request = request(algebra, "?mixed=1+theta*" + basis + "; ?other=2+alpha*theta; ?constant=7;");
            String code = service.compile(request).getOptimizeResult();
            assertTrue(code, code.contains("mixed = theta([]);"));
            assertTrue(code, code.contains("other = alpha([]);"));
            assertTrue(code, code.contains("other = theta([]);"));
            assertFalse("Independent numeric outputs must not be promoted", code.contains("constant = "));
            assertTrue(code, code.contains("constant(1) = 7.0;"));
        }
    }

    @Test
    public void commonSubexpressionsRetainTheirSymbolicDependencies() throws Exception {
        CompileRequest request = request(AlgebraPlugin.ALGEBRA_QCA,
                "v=alpha+beta; ?first=1+sin(v)*f1+cos(v)*f2; ?second=2+sin(v)*f1T+gamma*cos(v)*f2T;");
        request.getOptimization().setCse(true);
        String code = service.compile(request).getOptimizeResult();
        for (String input : new String[]{"alpha", "beta"}) {
            assertTrue(code, code.contains("first = " + input + "([]);"));
            assertTrue(code, code.contains("second = " + input + "([]);"));
        }
        assertTrue(code, code.contains("second = gamma([]);"));
        assertFalse(code, code.contains("first = gamma([]);"));
    }

    private CompileRequest request(AlgebraPlugin algebra, String source) {
        CompileRequest request = new CompileRequest();
        request.setAlgebraPlugins(algebra);
        request.setAlgebraDimension(2);
        request.setCodegenPlugins(CodegenPlugin.MATLAB);
        request.setOutputMode(OutputMode.CODE_ONLY);
        request.getScript().setFunctionName("matlab_symbols");
        request.getScript().setOptimizeCode(source);
        return request;
    }
}
