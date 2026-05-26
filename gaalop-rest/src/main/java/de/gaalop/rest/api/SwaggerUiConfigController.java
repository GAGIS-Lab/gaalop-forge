package de.gaalop.rest.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class SwaggerUiConfigController {

    @GetMapping("/v3/api-docs/swagger-config")
    public Map<String, Object> swaggerConfig() {
        return Map.of(
                "urls", List.of(
                        Map.of("name", "English", "url", "/v3/api-docs/english"),
                        Map.of("name", "中文", "url", "/v3/api-docs/chinese"),
                        Map.of("name", "Deutsch", "url", "/v3/api-docs/german")
                ),
                "urls.primaryName", "English",
                "validatorUrl", ""
        );
    }
}
