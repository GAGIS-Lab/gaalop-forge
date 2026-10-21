package de.gaalop.rest.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gaalop.rest.dto.AlgebraPlugin;
import de.gaalop.rest.dto.CodegenPlugin;
import de.gaalop.rest.dto.CompileRequest;
import de.gaalop.rest.dto.OutputMode;
import de.gaalop.rest.service.CompileHistoryService;
import de.gaalop.rest.service.GaalopCompileService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

public class ServerLogRegressionTest {

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
    public void unsupportedMethodsReturn405AndPreserveAllowHeader() throws Exception {
        for (HttpMethod method : new HttpMethod[] {HttpMethod.GET, HttpMethod.PUT,
                HttpMethod.DELETE, HttpMethod.PATCH}) {
            for (String accept : new String[] {"application/json", "text/html"}) {
                MvcResult result = mvc.perform(request(method, "/api/v1/compile")
                        .header(HttpHeaders.ACCEPT, accept)).andReturn();
                assertError(result, 405);
                assertEquals("POST", result.getResponse().getHeader(HttpHeaders.ALLOW));
                assertTrue(result.getResolvedException() instanceof HttpRequestMethodNotSupportedException);
            }
        }
    }

    @Test
    public void missingMalformedAndInvalidEnumJsonReturn400() throws Exception {
        for (String body : new String[] {"", "{", "{\"codegenPlugins\":\"UNKNOWN\"}",
                "{\"algebraDimension\":\"two\"}"}) {
            assertError(mvc.perform(post("/api/v1/compile")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andReturn(), 400, "JSON");
        }
    }

    @Test
    public void unsupportedContentTypeReturns415() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/compile")
                .contentType(MediaType.TEXT_PLAIN)
                .content("{\"codegenPlugins\":\"JAVA\"}")).andReturn();
        assertError(result, 415);
        assertTrue(result.getResponse().getHeader(HttpHeaders.ACCEPT)
                .contains(MediaType.APPLICATION_JSON_VALUE));
    }

    @Test
    public void webRuntimeExcludesDesktopEntryPointsPluginsAndOpenGl() {
        ClassLoader loader = GaalopCompileService.class.getClassLoader();
        for (String type : new String[] {"de.gaalop.gui.Main", "de.gaalop.Main",
                "de.gaalop.codegen_verilog.Plugin", "de.gaalop.gappDebugger.Plugin",
                "de.gaalop.vis2d.Plugin", "de.gaalop.visualizer.Plugin", "org.lwjgl.Sys"}) {
            assertNull("Desktop class must not be on the Web classpath: " + type,
                    loader.getResource(type.replace('.', '/') + ".class"));
        }
    }

    @Test
    public void desktopOutputsAreRejectedBeforeParsingForAllWorkspacesAndModes() throws Exception {
        for (CodegenPlugin target : new CodegenPlugin[] {CodegenPlugin.VERILOG,
                CodegenPlugin.VISUALIZER, CodegenPlugin.VIS2D, CodegenPlugin.GAPP_DEBUGGER}) {
            for (AlgebraPlugin algebra : new AlgebraPlugin[] {AlgebraPlugin.ALGEBRA_CGA,
                    AlgebraPlugin.ALGEBRA_QCA, AlgebraPlugin.ALGEBRA_QRA}) {
                for (OutputMode mode : OutputMode.values()) {
                    CompileRequest input = input();
                    input.setAlgebraPlugins(algebra);
                    input.setAlgebraDimension(2);
                    input.setCodegenPlugins(target);
                    input.setOutputMode(mode);
                    // Capability validation must run before either parsing or native loading.
                    input.getScript().setOptimizeCode("this is not valid GAALOPScript");
                    assertError(compile(input), 400, target.getPluginName(), "desktop", "Web");
                }
            }
        }
    }

    @Test
    public void supportedCodeAndBrowserVisualizationStillCompile() throws Exception {
        CompileRequest input = input();
        input.setOutputMode(OutputMode.CODE_AND_VISUALIZATION);
        input.setVisualizationEnabled(true);
        input.getScript().setOptimizeCode("?sphere=createPoint(0,0,0)-0.5*einf;");
        input.getScript().setMultivectorsVisualized(":sphere;");
        MvcResult result = compile(input);
        assertEquals(200, result.getResponse().getStatus());
        JsonNode body = mapper.readTree(result.getResponse().getContentAsByteArray());
        assertEquals("200", body.get("statusCode").asText());
        assertTrue(body.get("optimizeResult").asText().contains("sphere"));
        assertFalse(body.get("visualizationCode").asText().isEmpty());
    }

    private CompileRequest input() {
        CompileRequest input = new CompileRequest();
        input.setAlgebraPlugins(AlgebraPlugin.ALGEBRA_CGA);
        input.setCodegenPlugins(CodegenPlugin.JAVA);
        input.setOutputMode(OutputMode.CODE_ONLY);
        input.getScript().setFunctionName("web_regression");
        input.getScript().setOptimizeCode("?result=x;");
        return input;
    }

    private MvcResult compile(CompileRequest input) throws Exception {
        return mvc.perform(post("/api/v1/compile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(input))).andReturn();
    }

    private void assertError(MvcResult result, int status, String... details) throws Exception {
        assertEquals(status, result.getResponse().getStatus());
        assertEquals(MediaType.APPLICATION_JSON_VALUE, result.getResponse().getContentType());
        JsonNode body = mapper.readTree(result.getResponse().getContentAsByteArray());
        assertEquals(Integer.toString(status), body.get("statusCode").asText());
        for (String detail : details) {
            assertTrue(body.toString(), body.get("message").asText().contains(detail));
        }
        assertEquals("", body.get("optimizeResult").asText());
        assertEquals("", body.get("visualizationCode").asText());
    }
}
