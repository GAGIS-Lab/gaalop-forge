package de.gaalop.rest.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gaalop.rest.dto.CompileResponse;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public class RestExceptionHandlerTest {

    private MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @Before
    public void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new RestExceptionHandler())
                .build();
    }

    @Test
    public void missingResourceReturnsJsonForBrowserAndApiAcceptHeaders() throws Exception {
        for (String accept : new String[] {"application/json", "*/*", "text/html",
                "text/css", "image/avif,image/webp,image/*", "application/json;q=0,text/html"}) {
            MvcResult result = mvc.perform(get("/missing").header("Accept", accept)).andReturn();
            assertError(result, 404, "Endpoint not found.");
            assertTrue(result.getResolvedException() instanceof NoResourceFoundException);
        }
    }

    @Test
    public void missingResourceWithoutAcceptHeaderReturnsJson() throws Exception {
        assertError(mvc.perform(get("/missing")).andReturn(), 404, "Endpoint not found.");
    }

    @Test
    public void badRequestWithHtmlAcceptPreservesStatusAndMessage() throws Exception {
        assertError(mvc.perform(get("/bad-request").accept(MediaType.TEXT_HTML)).andReturn(),
                400, "Invalid input.");
    }

    @Test
    public void unexpectedErrorWithHtmlAcceptHidesInternalDetails() throws Exception {
        assertError(mvc.perform(get("/internal-error").accept(MediaType.TEXT_HTML)).andReturn(),
                500, "Internal server error.");
    }

    @Test
    public void unacceptableResponseReturns406WithoutSerializationFailure() throws Exception {
        MvcResult result = mvc.perform(get("/json-only").accept(MediaType.TEXT_HTML)).andReturn();
        assertEquals(406, result.getResponse().getStatus());
        assertEquals("", result.getResponse().getContentAsString());
        assertTrue(result.getResolvedException() instanceof HttpMediaTypeNotAcceptableException);
    }

    private void assertError(MvcResult result, int status, String message) throws Exception {
        assertEquals(status, result.getResponse().getStatus());
        assertEquals(MediaType.APPLICATION_JSON_VALUE, result.getResponse().getContentType());
        JsonNode body = mapper.readTree(result.getResponse().getContentAsByteArray());
        assertEquals(Integer.toString(status), body.get("statusCode").asText());
        assertEquals(message, body.get("message").asText());
        assertEquals("", body.get("optimizeResult").asText());
        assertEquals("", body.get("visualizationCode").asText());
    }

    @RestController
    public static class TestController {

        @GetMapping("/missing")
        public void missing() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "missing");
        }

        @GetMapping("/bad-request")
        public void badRequest() {
            throw new IllegalArgumentException("Invalid input.");
        }

        @GetMapping("/internal-error")
        public void internalError() {
            throw new IllegalStateException("Internal details must not be exposed.");
        }

        @GetMapping(value = "/json-only", produces = MediaType.APPLICATION_JSON_VALUE)
        public CompileResponse jsonOnly() {
            return CompileResponse.success("", "");
        }
    }
}
