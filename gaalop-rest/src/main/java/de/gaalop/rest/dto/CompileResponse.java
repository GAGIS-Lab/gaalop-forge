package de.gaalop.rest.dto;

public class CompileResponse {

    private final String statusCode;
    private final String message;
    private final String optimizeResult;
    private final String visualizationCode;

    public CompileResponse(String statusCode, String message, String optimizeResult, String visualizationCode) {
        this.statusCode = statusCode;
        this.message = message;
        this.optimizeResult = optimizeResult;
        this.visualizationCode = visualizationCode;
    }

    public static CompileResponse success(String optimizeResult, String visualizationCode) {
        return new CompileResponse("200", "Compilation succeeded / 编译成功", optimizeResult, visualizationCode);
    }

    public static CompileResponse error(String statusCode, String message) {
        return new CompileResponse(statusCode, message, "", "");
    }

    public String getStatusCode() {
        return statusCode;
    }

    public String getMessage() {
        return message;
    }

    public String getOptimizeResult() {
        return optimizeResult;
    }

    public String getVisualizationCode() {
        return visualizationCode;
    }
}
