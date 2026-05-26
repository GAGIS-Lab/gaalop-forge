package de.gaalop.rest.dto;

public class ScriptInput {

    private String optimizeCode = "";
    private String variableAssignments = "";
    private Object multivectorsVisualized;
    private String functionName = "script";

    public String getOptimizeCode() {
        return optimizeCode;
    }

    public void setOptimizeCode(String optimizeCode) {
        this.optimizeCode = optimizeCode;
    }

    public String getVariableAssignments() {
        return variableAssignments;
    }

    public void setVariableAssignments(String variableAssignments) {
        this.variableAssignments = variableAssignments;
    }

    public Object getMultivectorsVisualized() {
        return multivectorsVisualized;
    }

    public void setMultivectorsVisualized(Object multivectorsVisualized) {
        this.multivectorsVisualized = multivectorsVisualized;
    }

    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }
}
