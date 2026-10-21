package de.gaalop.rest.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import static org.junit.Assert.*;

public class HubWorkspaceControllerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private MultiValueMap<String,String> params(String... values) {
        MultiValueMap<String,String> p = new LinkedMultiValueMap<>();
        for (String v : values) p.add("revision", v);
        return p;
    }
    @Test public void rejectsBadParametersAndMissingConfiguration() {
        HubWorkspaceController c = new HubWorkspaceController("", mapper);
        assertEquals(503,c.workspace("test",params()).getStatusCode().value());
        for(String id:new String[]{"..","bad.id","x".repeat(129)})
            assertEquals(400,c.workspace(id,params()).getStatusCode().value());
        for(String r:new String[]{"0","-1","01","","1".repeat(19)})
            assertEquals(400,c.workspace("test",params(r)).getStatusCode().value());
        assertEquals(400,c.workspace("test",params("1","2")).getStatusCode().value());
        assertEquals(502,new HubWorkspaceController("http://127.0.0.1:1",mapper).workspace("test",params()).getStatusCode().value());
    }
    @Test public void boundedProxyPreservesJsonAndStatusesWithoutForwardingCredentials() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        AtomicReference<String> query=new AtomicReference<>();
        AtomicReference<String> credentials=new AtomicReference<>();
        server.createContext("/api/v1/examples", e -> {
            query.set(e.getRequestURI().getRawQuery());
            credentials.set(e.getRequestHeaders().getFirst("Authorization") == null
                && e.getRequestHeaders().getFirst("Cookie") == null ? "absent" : "present");
            String id=e.getRequestURI().getPath().split("/")[4];
            int status=id.matches("s[0-9]+") ? Integer.parseInt(id.substring(1)) : 200;
            String body="{\"schemaVersion\":1,\"id\":\"ok\",\"revision\":\"1\",\"title\":\"example\",\"workspace\":\"quantum\",\"algebra\":\"QRA\",\"algebraPlugins\":\"ALGEBRA_QRA\",\"qubits\":3,\"script\":{\"optimizeCode\":\"?res=1;\",\"variableAssignments\":\"\",\"multivectorsVisualized\":\":res;\"}}";
            if(status!=200) body="{\"detail\":{\"code\":\"REVISION_MISMATCH\",\"currentRevision\":\"2\"}}";
            if(id.equals("bad")) body="not json";
            if(id.equals("array")) body="[]";
            if(id.equals("trailing")) body="{} {}";
            if(id.equals("large")) body="x".repeat(1024*1024+1);
            if(id.equals("slow")) { try { Thread.sleep(300); } catch(InterruptedException ex) { Thread.currentThread().interrupt(); } }
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            e.getResponseHeaders().set("Content-Type","application/json");
            e.getResponseHeaders().set("Location","/api/v1/examples/ok/workspace");
            e.sendResponseHeaders(status,0); // chunked: exercise streaming limit
            try { e.getResponseBody().write(bytes); } catch(java.io.IOException ignored) {} finally { e.close(); }
        });
        server.start();
        try {
            HubWorkspaceController c=new HubWorkspaceController("http://127.0.0.1:"+server.getAddress().getPort(),mapper,100);
            org.springframework.test.web.servlet.MockMvc mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(c).build();
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/hub/examples/ok/workspace")
                    .param("revision", "1").header("Authorization", "Bearer browser-secret").header("Cookie", "session=browser-secret"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
            assertEquals("absent", credentials.get());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/hub/examples/ok/workspace?revision=1&revision=2"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
            var response=c.workspace("ok",params("1"));
            assertEquals(200,response.getStatusCode().value());
            assertEquals("revision=1",query.get());
            assertEquals("absent",credentials.get());
            assertEquals("",response.getBody().at("/script/variableAssignments").asText());
            assertEquals(":res;",response.getBody().at("/script/multivectorsVisualized").asText());
            assertEquals("no-store",response.getHeaders().getFirst("Cache-Control"));
            c.workspace("ok",params()); assertNull(query.get());
            for(int status:new int[]{404,409,422}) {
                var error=c.workspace("s"+status,params());
                assertEquals(status,error.getStatusCode().value());
                assertEquals("2",error.getBody().at("/detail/currentRevision").asText());
            }
            for(String id:new String[]{"s302","s500","bad","array","trailing","large"})
                assertEquals(id,502,c.workspace(id,params()).getStatusCode().value());
            assertEquals(504,c.workspace("slow",params()).getStatusCode().value());
        } finally { server.stop(0); }
    }
}
