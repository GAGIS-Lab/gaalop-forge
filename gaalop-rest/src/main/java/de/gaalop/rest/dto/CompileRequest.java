package de.gaalop.rest.dto;

public class CompileRequest {

    private AlgebraPlugin algebraPlugins;
    private Integer algebraDimension;

    public Integer getAlgebraDimension() {
        return algebraDimension;
    }

    public void setAlgebraDimension(Integer algebraDimension) {
        this.algebraDimension = algebraDimension;
    }
    private CodegenPlugin codegenPlugins;
    private OutputMode outputMode = OutputMode.CODE_ONLY;
    private Boolean visualizationEnabled = Boolean.FALSE;
    private OptimizationOptions optimization = new OptimizationOptions();
    private ScriptInput script = new ScriptInput();

    public AlgebraPlugin getAlgebraPlugins() {
        return algebraPlugins;
    }

    public void setAlgebraPlugins(AlgebraPlugin algebraPlugins) {
        this.algebraPlugins = algebraPlugins;
    }

    public CodegenPlugin getCodegenPlugins() {
        return codegenPlugins;
    }

    public void setCodegenPlugins(CodegenPlugin codegenPlugins) {
        this.codegenPlugins = codegenPlugins;
    }

    public OutputMode getOutputMode() {
        return outputMode;
    }

    public void setOutputMode(OutputMode outputMode) {
        this.outputMode = outputMode;
    }

    public Boolean getVisualizationEnabled() {
        return visualizationEnabled;
    }

    public void setVisualizationEnabled(Boolean visualizationEnabled) {
        this.visualizationEnabled = visualizationEnabled;
    }

    public OptimizationOptions getOptimization() {
        return optimization;
    }

    public void setOptimization(OptimizationOptions optimization) {
        this.optimization = optimization;
    }

    public ScriptInput getScript() {
        return script;
    }

    public void setScript(ScriptInput script) {
        this.script = script;
    }
}
