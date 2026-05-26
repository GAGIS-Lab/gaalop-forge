package de.gaalop.rest.dto;

public enum CodegenPlugin {
    CLUCALC("GAALOPScript"),
    JULIA("Julia"),
    VERILOG("Verilog"),
    GAPP_DEBUGGER("Gapp Debugger"),
    CSHARP("C#"),
    RUST("Rust"),
    JAVA("Java"),
    VIS2D("Vis2d"),
    GAALET_OUTPUT("C/C++ (gaalet)"),
    GAPP("GAPP CodeGenerator"),
    COMPRESSED("compressed C/C++"),
    VISUALIZER("Visualizer"),
    GANJA("Ganja"),
    GAPP_OPENCL("GAPP OpenCL"),
    PYTHON("Python"),
    MATLAB("MATLAB"),
    DOT("Graphviz DOT"),
    MATHEMATICA("Mathematica"),
    CPP("C/C++"),
    LATEX("LaTeX");

    private final String pluginName;

    CodegenPlugin(String pluginName) {
        this.pluginName = pluginName;
    }

    public String getPluginName() {
        return pluginName;
    }
}
