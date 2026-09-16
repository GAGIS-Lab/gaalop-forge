package de.gaalop.rest.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class SwaggerUiConfigController {

    @GetMapping("/v3/api-docs/swagger-config")
    public Map<String, Object> swaggerConfig(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        return Map.of(
                "urls", List.of(
                        Map.of("name", "English", "url", contextPath + "/v3/api-docs/english"),
                        Map.of("name", "中文", "url", contextPath + "/v3/api-docs/chinese"),
                        Map.of("name", "Deutsch", "url", contextPath + "/v3/api-docs/german")
                ),
                "urls.primaryName", "English",
                "validatorUrl", ""
        );
    }
}
