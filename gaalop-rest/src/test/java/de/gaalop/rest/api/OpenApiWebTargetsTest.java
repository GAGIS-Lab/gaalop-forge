package de.gaalop.rest.api;

import de.gaalop.rest.dto.CodegenPlugin;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.mock.web.MockServletContext;

import static org.junit.Assert.*;

public class OpenApiWebTargetsTest {

    @Test
    public void everyDocumentationLanguageExcludesDesktopOnlyTargets() {
        OpenApiConfig config = new OpenApiConfig(new MockServletContext());
        List<String> allTargets = Arrays.stream(CodegenPlugin.values())
                .map(Enum::name).collect(Collectors.toList());
        for (GroupedOpenApi group : new GroupedOpenApi[] {
                config.englishApi(), config.chineseApi(), config.germanApi()}) {
            StringSchema inlineTargets = new StringSchema();
            inlineTargets.setEnum(new ArrayList<>(allTargets));
            StringSchema referencedTargets = new StringSchema();
            referencedTargets.setEnum(new ArrayList<>(allTargets));
            Schema<Object> request = new Schema<>();
            request.addProperty("codegenPlugins", inlineTargets);
            OpenAPI api = new OpenAPI().paths(new io.swagger.v3.oas.models.Paths()).components(new Components()
                    .addSchemas("CompileRequest", request)
                    .addSchemas("CodegenPlugin", referencedTargets));
            group.getOpenApiCustomizers().forEach(customizer -> customizer.customise(api));
            for (String target : Arrays.asList("VERILOG", "GAPP_DEBUGGER", "VIS2D", "VISUALIZER")) {
                assertFalse(group.getGroup(), inlineTargets.getEnum().contains(target));
                assertFalse(group.getGroup(), referencedTargets.getEnum().contains(target));
            }
            for (String target : Arrays.asList("JAVA", "MATLAB", "PYTHON", "GANJA", "GAPP")) {
                assertTrue(group.getGroup(), inlineTargets.getEnum().contains(target));
                assertTrue(group.getGroup(), referencedTargets.getEnum().contains(target));
            }
            assertEquals(allTargets.size() - 4, inlineTargets.getEnum().size());
        }
    }
}
