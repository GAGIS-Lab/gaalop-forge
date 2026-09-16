package de.gaalop.rest.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gaalop.rest.dto.AlgebraPlugin;
import de.gaalop.rest.dto.CodegenPlugin;
import de.gaalop.rest.dto.CompileRequest;
import de.gaalop.rest.service.CompileHistoryService;
import de.gaalop.rest.service.GaalopCompileService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class CompileControllerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private MockMvc mvc;

    @Before
    public void setUp() {
        GaalopCompileService service = new GaalopCompileService("",
                new CompileHistoryService(mapper, false, "unused-history"));
        mvc = MockMvcBuilders.standaloneSetup(new CompileController(service))
                .setControllerAdvice(new RestExceptionHandler())
                .build();
    }

    @Test
    public void unavailableDualReturns400WithAlgebraAndOperator() throws Exception {
        assertCompilationError(AlgebraPlugin.ALGEBRA_QGA, "?result = *e1;", "qga", "Dual", "'*'");
    }

    @Test
    public void undefinedMacroReturns400WithNameAndArity() throws Exception {
        assertCompilationError(AlgebraPlugin.ALGEBRA_CGA, "?result = missing(1);", "missing", "1 arguments");
    }

    @Test
    public void cgaPointMacroInQcaReturns400InsteadOfInternalError() throws Exception {
        assertCompilationError(AlgebraPlugin.ALGEBRA_QCA, "?result = createPoint(a,b,c);",
                "qca", "createPoint", "3 arguments");
    }

    @Test
    public void supportedCgaDualStillCompiles() throws Exception {
        MvcResult result = compile(AlgebraPlugin.ALGEBRA_CGA, "?result = *e1;");
        assertEquals(200, result.getResponse().getStatus());
        JsonNode body = mapper.readTree(result.getResponse().getContentAsByteArray());
        assertEquals("200", body.get("statusCode").asText());
        assertFalse(body.get("optimizeResult").asText().isEmpty());
    }

    private void assertCompilationError(AlgebraPlugin algebra, String source, String... details) throws Exception {
        MvcResult result = compile(algebra, source);
        assertEquals(400, result.getResponse().getStatus());
        assertEquals(MediaType.APPLICATION_JSON_VALUE, result.getResponse().getContentType());
        JsonNode body = mapper.readTree(result.getResponse().getContentAsByteArray());
        assertEquals("400", body.get("statusCode").asText());
        for (String detail : details) {
            assertTrue(body.toString(), body.get("message").asText().contains(detail));
        }
        assertEquals("", body.get("optimizeResult").asText());
        assertEquals("", body.get("visualizationCode").asText());
    }

    private MvcResult compile(AlgebraPlugin algebra, String source) throws Exception {
        CompileRequest request = new CompileRequest();
        request.setAlgebraPlugins(algebra);
        request.setCodegenPlugins(CodegenPlugin.JAVA);
        request.getScript().setOptimizeCode(source);
        return mvc.perform(post("/api/v1/compile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(request))).andReturn();
    }
}
