package de.gaalop.rest.api;

import jakarta.servlet.ServletContext;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class OpenApiConfig {

    private final ServletContext servletContext;

    public OpenApiConfig(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    @Bean
    public GroupedOpenApi englishApi() {
        return GroupedOpenApi.builder()
                .group("english")
                .pathsToMatch("/api/v1/compile")
                .addOpenApiCustomizer(englishCustomizer())
                .build();
    }

    @Bean
    public GroupedOpenApi chineseApi() {
        return GroupedOpenApi.builder()
                .group("chinese")
                .pathsToMatch("/api/v1/compile")
                .addOpenApiCustomizer(chineseCustomizer())
                .build();
    }

    @Bean
    public GroupedOpenApi germanApi() {
        return GroupedOpenApi.builder()
                .group("german")
                .pathsToMatch("/api/v1/compile")
                .addOpenApiCustomizer(germanCustomizer())
                .build();
    }

    private OpenApiCustomizer englishCustomizer() {
        return openApi -> {
            useSameOriginServer(openApi);
            openApi.info(new Info()
                    .title("GAALOPScript Compilation REST API")
                    .version("1.0.0")
                    .description("Compile GAALOPScript into target-language source code with selectable algebra spaces, code generators, optimization switches, and optional visualization output."));
            openApi.setTags(java.util.List.of(new Tag()
                    .name("Compilation")
                    .description("Compile and optimize GAALOPScript.")));
            customizeCompileOperation(openApi,
                    "Compile GAALOPScript",
                    "Build generated code and optional visualization output from the selected algebra plugin, code generator, output mode, and optimization options.",
                    "GAALOPScript compile request.",
                    "Compilation succeeded.",
                    "Invalid request parameters or GAALOPScript compilation failed.",
                    "Internal server error.",
                    "CompileRequest",
                    "CompileResponse");
            customizeSchemas(openApi, englishSchemaDescriptions());
        };
    }

    private OpenApiCustomizer chineseCustomizer() {
        return openApi -> {
            useSameOriginServer(openApi);
            openApi.info(new Info()
                    .title("GAALOPScript 编译 REST API")
                    .version("1.0.0")
                    .description("将 GAALOPScript 编译为目标语言代码，支持选择几何代数空间、代码生成器、优化开关以及可选的可视化输出。"));
            openApi.setTags(java.util.List.of(new Tag()
                    .name("编译")
                    .description("编译和优化 GAALOPScript。")));
            customizeCompileOperation(openApi,
                    "编译 GAALOPScript",
                    "根据选择的几何代数空间、代码生成器、输出模式和优化选项，生成目标代码以及可选的可视化结果。",
                    "GAALOPScript 编译请求。",
                    "编译成功。",
                    "请求参数错误或 GAALOPScript 编译失败。",
                    "服务端内部错误。",
                    "编译请求",
                    "编译响应");
            customizeSchemas(openApi, chineseSchemaDescriptions());
        };
    }

    private OpenApiCustomizer germanCustomizer() {
        return openApi -> {
            useSameOriginServer(openApi);
            openApi.info(new Info()
                    .title("GAALOPScript Kompilierungs-REST-API")
                    .version("1.0.0")
                    .description("Kompiliert GAALOPScript in Quellcode einer Zielsprache, mit auswählbaren Algebra-Räumen, Codegeneratoren, Optimierungsoptionen und optionaler Visualisierungsausgabe."));
            openApi.setTags(java.util.List.of(new Tag()
                    .name("Kompilierung")
                    .description("GAALOPScript kompilieren und optimieren.")));
            customizeCompileOperation(openApi,
                    "GAALOPScript kompilieren",
                    "Erzeugt generierten Code und optionale Visualisierungsausgabe aus dem gewählten Algebra-Plugin, Codegenerator, Ausgabemodus und den Optimierungsoptionen.",
                    "GAALOPScript-Kompilierungsanfrage.",
                    "Kompilierung erfolgreich.",
                    "Ungültige Anfrageparameter oder GAALOPScript-Kompilierung fehlgeschlagen.",
                    "Interner Serverfehler.",
                    "Kompilierungsanfrage",
                    "Kompilierungsantwort");
            customizeSchemas(openApi, germanSchemaDescriptions());
        };
    }

    private void useSameOriginServer(OpenAPI openApi) {
        openApi.setServers(java.util.List.of(new Server()
                .url(servletContext.getContextPath().isEmpty() ? "/" : servletContext.getContextPath())
                .description("Same origin as Swagger UI")));
    }

    private void customizeCompileOperation(OpenAPI openApi,
                                           String summary,
                                           String description,
                                           String requestDescription,
                                           String successDescription,
                                           String badRequestDescription,
                                           String serverErrorDescription,
                                           String requestExampleName,
                                           String responseExampleName) {
        var pathItem = openApi.getPaths().get("/api/v1/compile");
        if (pathItem == null || pathItem.getPost() == null) {
            return;
        }

        var operation = pathItem.getPost();
        operation.setTags(java.util.List.of(openApi.getTags().get(0).getName()));
        operation.setSummary(summary);
        operation.setDescription(description);

        RequestBody requestBody = operation.getRequestBody();
        if (requestBody != null) {
            requestBody.setRequired(true);
            requestBody.setDescription(requestDescription);
            var json = requestBody.getContent().get("application/json");
            if (json != null) {
                json.addExamples(requestExampleName, new Example().value(exampleRequest()));
            }
        }

        if (operation.getResponses() != null) {
            response(operation.getResponses().get("200"), successDescription, responseExampleName, exampleResponse());
            response(operation.getResponses().get("400"), badRequestDescription, "CompileError", exampleError("400"));
            response(operation.getResponses().get("500"), serverErrorDescription, "ServerError", exampleError("500"));
        }
    }

    private void response(ApiResponse response, String description, String exampleName, Object exampleValue) {
        if (response == null) {
            return;
        }
        response.setDescription(description);
        if (response.getContent() != null && response.getContent().get("application/json") != null) {
            response.getContent().get("application/json")
                    .addExamples(exampleName, new Example().value(exampleValue));
        }
    }

    private void customizeSchemas(OpenAPI openApi, Map<String, Map<String, String>> descriptions) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }

        descriptions.forEach((schemaName, fieldDescriptions) -> {
            Schema<?> schema = openApi.getComponents().getSchemas().get(schemaName);
            if (schema == null) {
                return;
            }
            String typeDescription = fieldDescriptions.get("_type");
            if (typeDescription != null) {
                schema.setDescription(typeDescription);
            }
            if (schema.getProperties() == null) {
                return;
            }
            fieldDescriptions.forEach((fieldName, fieldDescription) -> {
                Object property = schema.getProperties().get(fieldName);
                if (property instanceof Schema<?> propertySchema) {
                    propertySchema.setDescription(fieldDescription);
                }
            });
        });
    }

    private Map<String, Map<String, String>> englishSchemaDescriptions() {
        return Map.of(
                "CompileRequest", Map.of(
                        "_type", "GAALOPScript compilation request.",
                        "algebraPlugins", "Algebra space plugin.",
                        "algebraDimension", "QCA qubit count, 1 to 3; defaults to 1. Ignored for fixed algebras.",
                        "codegenPlugins", "Target code generator.",
                        "outputMode", "Controls whether code, visualization, or both are returned.",
                        "visualizationEnabled", "Enables visualization output.",
                        "optimization", "Optimization settings.",
                        "script", "GAALOPScript input."
                ),
                "ScriptInput", Map.of(
                        "_type", "GAALOPScript source blocks.",
                        "functionName", "Function name used by generated code.",
                        "optimizeCode", "Main GAALOPScript body to compile.",
                        "variableAssignments", "Runtime values used for visualization.",
                        "multivectorsVisualized", "Visualization directives or multivector list."
                ),
                "OptimizationOptions", Map.of(
                        "_type", "Optimizer switches.",
                        "cse", "Enable common subexpression elimination.",
                        "maxima", "Enable Maxima algebraic simplification."
                ),
                "CompileResponse", Map.of(
                        "_type", "Compilation response.",
                        "statusCode", "Business status code.",
                        "message", "Response message.",
                        "optimizeResult", "Generated target-language code.",
                        "visualizationCode", "Core ganja.js visualization script. The frontend wraps it into HTML."
                )
        );
    }

    private Map<String, Map<String, String>> chineseSchemaDescriptions() {
        return Map.of(
                "CompileRequest", Map.of(
                        "_type", "GAALOPScript 编译请求。",
                        "algebraPlugins", "几何代数空间插件。",
                        "codegenPlugins", "目标代码生成器。",
                        "outputMode", "控制返回代码、可视化结果，或两者都返回。",
                        "visualizationEnabled", "是否启用可视化输出。",
                        "optimization", "优化配置。",
                        "script", "GAALOPScript 输入内容。"
                ),
                "ScriptInput", Map.of(
                        "_type", "GAALOPScript 源码块。",
                        "functionName", "生成代码使用的函数名。",
                        "optimizeCode", "需要编译的主 GAALOPScript 内容。",
                        "variableAssignments", "可视化运行时使用的变量赋值。",
                        "multivectorsVisualized", "可视化指令或多重向量列表。"
                ),
                "OptimizationOptions", Map.of(
                        "_type", "优化器开关。",
                        "cse", "启用公共子表达式消除。",
                        "maxima", "启用 Maxima 代数化简。"
                ),
                "CompileResponse", Map.of(
                        "_type", "编译响应。",
                        "statusCode", "业务状态码。",
                        "message", "响应消息。",
                        "optimizeResult", "生成的目标语言代码。",
                        "visualizationCode", "核心 ganja.js 可视化脚本。前端负责拼接 HTML。"
                )
        );
    }

    private Map<String, Map<String, String>> germanSchemaDescriptions() {
        return Map.of(
                "CompileRequest", Map.of(
                        "_type", "GAALOPScript-Kompilierungsanfrage.",
                        "algebraPlugins", "Algebra-Raum-Plugin.",
                        "algebraDimension", "QCA-Qubitanzahl, 1 bis 3; Standardwert 1. Bei festen Algebren ignoriert.",
                        "codegenPlugins", "Codegenerator für die Zielsprache.",
                        "outputMode", "Steuert, ob Code, Visualisierung oder beides zurückgegeben wird.",
                        "visualizationEnabled", "Aktiviert die Visualisierungsausgabe.",
                        "optimization", "Optimierungseinstellungen.",
                        "script", "GAALOPScript-Eingabe."
                ),
                "ScriptInput", Map.of(
                        "_type", "GAALOPScript-Quellblöcke.",
                        "functionName", "Funktionsname für den generierten Code.",
                        "optimizeCode", "Hauptinhalt des zu kompilierenden GAALOPScript.",
                        "variableAssignments", "Laufzeitwerte für die Visualisierung.",
                        "multivectorsVisualized", "Visualisierungsanweisungen oder Multivektor-Liste."
                ),
                "OptimizationOptions", Map.of(
                        "_type", "Optimierungsschalter.",
                        "cse", "Aktiviert Common Subexpression Elimination.",
                        "maxima", "Aktiviert algebraische Vereinfachung mit Maxima."
                ),
                "CompileResponse", Map.of(
                        "_type", "Kompilierungsantwort.",
                        "statusCode", "Fachlicher Statuscode.",
                        "message", "Antwortnachricht.",
                        "optimizeResult", "Generierter Code der Zielsprache.",
                        "visualizationCode", "Kernskript für die ganja.js-Visualisierung. Das Frontend erzeugt daraus HTML."
                )
        );
    }

    private Map<String, Object> exampleRequest() {
        return Map.of(
                "algebraPlugins", "ALGEBRA_CGA",
                "codegenPlugins", "JAVA",
                "outputMode", "CODE_AND_VISUALIZATION",
                "visualizationEnabled", true,
                "optimization", Map.of("cse", false, "maxima", false),
                "script", Map.of(
                        "functionName", "threespheres",
                        "optimizeCode", "?x1=createPoint(a1,a2,a3);\n?S1=x1-0.5*(d14*d14)*einf;",
                        "variableAssignments", "a1=0; a2=0; a3=0; d14=0.5;",
                        "multivectorsVisualized", ":Blue;\n:S1;"
                )
        );
    }

    private Map<String, Object> exampleResponse() {
        return Map.of(
                "statusCode", "200",
                "message", "Compilation succeeded / 编译成功",
                "optimizeResult", "public class threespheres { ... }",
                "visualizationCode", "Algebra(4,1,0,()=>{ ... });"
        );
    }

    private Map<String, Object> exampleError(String statusCode) {
        return Map.of(
                "statusCode", statusCode,
                "message", "Compilation failed.",
                "optimizeResult", "",
                "visualizationCode", ""
        );
    }
}
