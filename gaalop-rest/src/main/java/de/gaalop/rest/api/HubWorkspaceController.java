package de.gaalop.rest.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/hub")
public class HubWorkspaceController {
    private static final int MAX_BYTES = 1024 * 1024;
    private final String baseUrl;
    private final ObjectMapper mapper;
    private final int readTimeout;

    @Autowired
    public HubWorkspaceController(@Value("${gaalop.hub.base-url:}") String baseUrl, ObjectMapper mapper) {
        this(baseUrl, mapper, 10000);
    }

    HubWorkspaceController(String baseUrl, ObjectMapper mapper, int readTimeout) {
        this.baseUrl = baseUrl.trim();
        this.mapper = mapper;
        this.readTimeout = readTimeout;
    }

    @GetMapping("/examples/{id}/workspace")
    public ResponseEntity<JsonNode> workspace(@PathVariable("id") String id,
            @RequestParam MultiValueMap<String, String> params) {
        String revision = params.getFirst("revision");
        if (!id.matches("[A-Za-z0-9_-]{1,128}")
                || params.keySet().stream().anyMatch(key -> !key.equals("revision"))
                || (params.containsKey("revision") && (params.get("revision").size() != 1
                || revision == null || !revision.matches("[1-9][0-9]{0,17}")))) {
            return error(400, "Invalid example ID or revision.");
        }
        if (baseUrl.isEmpty()) return error(503, "Hub service is not configured.");
        HttpURLConnection connection = null;
        try {
            URI base = URI.create(baseUrl);
            if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme()))
                    || base.getHost() == null || base.getUserInfo() != null
                    || base.getQuery() != null || base.getFragment() != null
                    || !(base.getPath().isEmpty() || base.getPath().equals("/"))) {
                return error(503, "Hub service configuration is invalid.");
            }
            UriComponentsBuilder builder = UriComponentsBuilder.fromUri(base)
                    .replacePath("/api/v1/examples").pathSegment(id, "workspace");
            if (revision != null) builder.queryParam("revision", revision);
            connection = (HttpURLConnection) builder.build().encode().toUri().toURL().openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(readTimeout);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            int status = connection.getResponseCode();
            if (status != 200 && status != 404 && status != 409 && status != 422) {
                return error(502, "Hub returned an unexpected response.");
            }
            if (connection.getContentLengthLong() > MAX_BYTES) return error(502, "Hub response is too large.");
            byte[] body;
            try (InputStream input = status == 200 ? connection.getInputStream() : connection.getErrorStream();
                    ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                if (input == null) return error(502, "Hub returned invalid JSON.");
                byte[] buffer = new byte[8192];
                int count, total = 0;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_BYTES) return error(502, "Hub response is too large.");
                    output.write(buffer, 0, count);
                }
                body = output.toByteArray();
            }
            JsonNode json = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(body);
            if (json == null || !json.isObject()) return error(502, "Hub returned invalid JSON.");
            return response(status, json);
        } catch (SocketTimeoutException ex) {
            return error(504, "Hub request timed out.");
        } catch (Exception ex) {
            return error(502, "Unable to read the Hub workspace.");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private ResponseEntity<JsonNode> error(int status, String message) {
        return response(status, mapper.createObjectNode().put("message", message));
    }

    private ResponseEntity<JsonNode> response(int status, JsonNode body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .header("Cache-Control", "no-store").body(body);
    }
}
