package de.gaalop.rest.service;

import de.gaalop.CodeGenerator;
import de.gaalop.CompilationException;
import de.gaalop.CompilerFacade;
import de.gaalop.InputFile;
import de.gaalop.OutputFile;
import de.gaalop.VisualCodeInserterStrategy;
import de.gaalop.algebra.DefinedAlgebra;
import de.gaalop.rest.dto.AlgebraPlugin;
import de.gaalop.rest.dto.CodegenPlugin;
import de.gaalop.rest.dto.CompileRequest;
import de.gaalop.rest.dto.CompileResponse;
import de.gaalop.rest.dto.OptimizationOptions;
import de.gaalop.rest.dto.OutputMode;
import de.gaalop.rest.dto.ScriptInput;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GaalopCompileService {

    private static final Pattern INLINE_SCRIPT_PATTERN = Pattern.compile(
            "<script(?![^>]*\\bsrc\\b)[^>]*>(.*?)</script>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private final String defaultMaximaCommand;
    private final Path workingDirectory;
    private final CompileHistoryService compileHistoryService;

    public GaalopCompileService(
            @Value("${gaalop.maxima.command:}") String defaultMaximaCommand,
            CompileHistoryService compileHistoryService
    ) {
        this.defaultMaximaCommand = defaultMaximaCommand;
        this.workingDirectory = Paths.get("").toAbsolutePath().normalize();
        this.compileHistoryService = compileHistoryService;
    }

    public CompileResponse compile(CompileRequest request) throws CompilationException {
        CompileRequest safeRequest = request == null ? new CompileRequest() : request;
        ScriptInput script = safeRequest.getScript() == null ? new ScriptInput() : safeRequest.getScript();
        CodegenPlugin codegen = resolveCodegen(safeRequest);

        String optimizeResult = "";
        OutputMode outputMode = safeRequest.getOutputMode() == null ? OutputMode.CODE_ONLY : safeRequest.getOutputMode();
        if (outputMode != OutputMode.VISUALIZATION_ONLY && codegen != CodegenPlugin.GANJA) {
            Set<OutputFile> optimizeFiles = compileFiles(safeRequest, script, codegen, false);
            optimizeResult = selectGeneratedText(optimizeFiles, false);
        }

        String visualizationCode = "";
        if (shouldGenerateVisualization(safeRequest, codegen)) {
            Set<OutputFile> visualizationFiles = compileFiles(safeRequest, script, CodegenPlugin.GANJA, true);
            visualizationCode = extractVisualizationCore(selectGeneratedText(visualizationFiles, true));
        }

        CompileResponse response = CompileResponse.success(optimizeResult, visualizationCode);
        compileHistoryService.recordSuccess(safeRequest, response);
        return response;
    }

    private Set<OutputFile> compileFiles(
            CompileRequest request,
            ScriptInput script,
            CodegenPlugin codegen,
            boolean includeVisualization
    ) throws CompilationException {
        OptimizationOptions optimization = request.getOptimization() == null
                ? new OptimizationOptions()
                : request.getOptimization();

        String algebra = resolveAlgebra(request);

        de.gaalop.globalSettings.Plugin globalSettingsPlugin = new de.gaalop.globalSettings.Plugin();
        if (Boolean.TRUE.equals(optimization.getMaxima())) {
            globalSettingsPlugin.setMaximaCommand(resolveMaximaCommand());
            globalSettingsPlugin.setOptMaxima(true);
        } else {
            globalSettingsPlugin.setOptMaxima(false);
        }

        de.gaalop.algebra.Plugin algebraPlugin = new de.gaalop.algebra.Plugin();
        de.gaalop.tba.Plugin tbaPlugin = new de.gaalop.tba.Plugin();
        tbaPlugin.setOptGCSE(Boolean.TRUE.equals(optimization.getCse()));

        CompilerFacade facade = new CompilerFacade(
                new de.gaalop.clucalc.input.Plugin().createCodeParser(),
                globalSettingsPlugin.createGlobalSettingsStrategy(),
                createVisualizer(includeVisualization, codegen),
                algebraPlugin.createAlgebraStrategy(),
                createOptimizationStrategy(codegen, tbaPlugin),
                createCodeGenerator(codegen),
                algebra,
                isBundledAlgebra(algebra),
                algebraPlugin.getAdditionalBaseDirectory()
        );

        String functionName = normalizeFunctionName(script.getFunctionName());
        InputFile input = new InputFile(functionName + ".clu", buildScript(script, includeVisualization));
        return facade.compile(input);
    }

    private de.gaalop.OptimizationStrategy createOptimizationStrategy(CodegenPlugin codegen, de.gaalop.tba.Plugin tbaPlugin) {
        if (codegen == CodegenPlugin.GAPP
                || codegen == CodegenPlugin.GAPP_DEBUGGER
                || codegen == CodegenPlugin.GAPP_OPENCL) {
            return new de.gaalop.gapp.Plugin().createOptimizationStrategy();
        }
        return tbaPlugin.createOptimizationStrategy();
    }

    private VisualCodeInserterStrategy createVisualizer(boolean includeVisualization, CodegenPlugin codegen) {
        if (!includeVisualization) {
            return graph -> {
            };
        }
        if (codegen == CodegenPlugin.GANJA) {
            return new de.gaalop.ganjaVisualCodeInserter.Plugin().createVisualCodeInserterStrategy();
        }
        return new de.gaalop.visualCodeInserter.Plugin().createVisualCodeInserterStrategy();
    }

    private CodeGenerator createCodeGenerator(CodegenPlugin target) {
        switch (target) {
            case JULIA:
                return new de.gaalop.julia.Plugin().createCodeGenerator();
            case VERILOG:
                return new de.gaalop.codegen_verilog.Plugin().createCodeGenerator();
            case GAPP_DEBUGGER:
                return new de.gaalop.gappDebugger.Plugin().createCodeGenerator();
            case CSHARP:
                return new de.gaalop.csharp.Plugin().createCodeGenerator();
            case RUST:
                return new de.gaalop.rust.Plugin().createCodeGenerator();
            case JAVA:
                return new de.gaalop.java.Plugin().createCodeGenerator();
            case VIS2D:
                return new de.gaalop.vis2d.Plugin().createCodeGenerator();
            case GAALET_OUTPUT:
                return new de.gaalop.gaalet.output.Plugin().createCodeGenerator();
            case GAPP:
                return new de.gaalop.codegenGapp.Plugin().createCodeGenerator();
            case COMPRESSED:
                return new de.gaalop.compressed.Plugin().createCodeGenerator();
            case VISUALIZER:
                return new de.gaalop.visualizer.Plugin().createCodeGenerator();
            case GANJA:
                return new de.gaalop.ganja.Plugin().createCodeGenerator();
            case GAPP_OPENCL:
                return new de.gaalop.gappopencl.Plugin().createCodeGenerator();
            case PYTHON:
                return new de.gaalop.python.Plugin().createCodeGenerator();
            case MATLAB:
                return new de.gaalop.matlab.Plugin().createCodeGenerator();
            case DOT:
                return new de.gaalop.dot.Plugin().createCodeGenerator();
            case MATHEMATICA:
                return new de.gaalop.mathematica.Plugin().createCodeGenerator();
            case CPP:
                return new de.gaalop.cpp.Plugin().createCodeGenerator();
            case LATEX:
                return new de.gaalop.latex.Plugin().createCodeGenerator();
            case CLUCALC:
            default:
                return new de.gaalop.clucalc.output.Plugin().createCodeGenerator();
        }
    }

    private String buildScript(ScriptInput script, boolean includeVisualization) {
        StringBuilder builder = new StringBuilder();
        if (includeVisualization) {
            appendBlock(builder, script.getVariableAssignments());
        }
        appendBlock(builder, script.getOptimizeCode());
        if (includeVisualization) {
            Object visualized = script.getMultivectorsVisualized();
            if (isVisualizationScript(visualized)) {
                appendBlock(builder, String.valueOf(visualized));
            } else {
                for (String multivector : normalizeVisualizedMultivectors(visualized)) {
                    builder.append(':').append(multivector).append(';').append('\n');
                }
            }
        }
        String result = builder.toString();
        if (result.trim().isEmpty()) {
            throw new IllegalArgumentException("script.optimizeCode must not be empty.");
        }
        return result;
    }

    private String selectGeneratedText(Set<OutputFile> files, boolean preferHtml) {
        if (files == null || files.isEmpty()) {
            return "";
        }

        if (preferHtml) {
            for (OutputFile file : files) {
                if (file.getName().toLowerCase(Locale.ROOT).endsWith(".html")) {
                    return file.getContent();
                }
            }
        }

        if (files.size() == 1) {
            return files.iterator().next().getContent();
        }

        StringBuilder builder = new StringBuilder();
        for (OutputFile file : files) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append("/* ").append(file.getName()).append(" */\n");
            builder.append(file.getContent());
            if (!file.getContent().endsWith("\n")) {
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private String extractVisualizationCore(String generatedContent) {
        if (generatedContent == null || generatedContent.trim().isEmpty()) {
            return "";
        }

        Matcher matcher = INLINE_SCRIPT_PATTERN.matcher(generatedContent);
        String lastScript = "";
        while (matcher.find()) {
            lastScript = matcher.group(1);
        }

        if (!lastScript.trim().isEmpty()) {
            return lastScript.trim();
        }
        return generatedContent.trim();
    }

    private void appendBlock(StringBuilder builder, String block) {
        if (block == null || block.trim().isEmpty()) {
            return;
        }
        builder.append(block.trim()).append('\n');
    }

    private boolean shouldGenerateVisualization(CompileRequest request, CodegenPlugin codegen) {
        if (codegen == CodegenPlugin.GANJA) {
            return true;
        }
        OutputMode outputMode = request.getOutputMode() == null ? OutputMode.CODE_ONLY : request.getOutputMode();
        if (outputMode == OutputMode.VISUALIZATION_ONLY
                || outputMode == OutputMode.CODE_AND_VISUALIZATION) {
            return true;
        }
        return Boolean.TRUE.equals(request.getVisualizationEnabled());
    }

    private CodegenPlugin resolveCodegen(CompileRequest request) {
        if (request.getCodegenPlugins() != null) {
            return request.getCodegenPlugins();
        }
        return CodegenPlugin.CLUCALC;
    }

    private String resolveAlgebra(CompileRequest request) {
        if (request.getAlgebraPlugins() != null) {
            return request.getAlgebraPlugins().getAlgebraId();
        }
        return AlgebraPlugin.ALGEBRA_CGA.getAlgebraId();
    }

    private List<String> normalizeVisualizedMultivectors(Object raw) {
        List<String> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        if (raw instanceof Collection<?>) {
            for (Object value : (Collection<?>) raw) {
                addVisualizedName(result, String.valueOf(value));
            }
            return result;
        }
        String text = String.valueOf(raw);
        for (String part : text.split("[,;\\s]+")) {
            addVisualizedName(result, part);
        }
        return result;
    }

    private boolean isVisualizationScript(Object raw) {
        if (!(raw instanceof String)) {
            return false;
        }
        String text = ((String) raw).trim();
        return text.contains(":") || text.contains(";");
    }

    private void addVisualizedName(List<String> result, String rawName) {
        if (rawName == null) {
            return;
        }
        String name = rawName.trim();
        if (name.isEmpty()) {
            return;
        }
        if (!name.matches("(::)?[A-Za-z_$][A-Za-z0-9_$]*")) {
            throw new IllegalArgumentException("Invalid multivector name for visualization: " + name);
        }
        result.add(name);
    }

    private String normalizeFunctionName(String rawName) {
        String name = rawName == null || rawName.trim().isEmpty() ? "script" : rawName.trim();
        return name.replaceAll("[^A-Za-z0-9_$-]", "_");
    }

    private boolean isBundledAlgebra(String algebraName) {
        for (DefinedAlgebra algebra : de.gaalop.algebra.Plugin.getDefinedAlgebras()) {
            if (algebra.id.equals(algebraName)) {
                return true;
            }
        }
        return false;
    }

    private String resolveMaximaCommand() {
        String configuredDefault = trimToNull(defaultMaximaCommand);
        if (configuredDefault != null) {
            return resolveConfiguredMaximaCommand(configuredDefault);
        }

        List<String> candidates = new ArrayList<>();
        candidates.add("tools/maxima/bin/maxima.bat");
        candidates.add("tools/maxima/bin/maxima.exe");
        candidates.add("maxima/bin/maxima.bat");
        candidates.add("maxima/bin/maxima.exe");
        candidates.add("C:\\Program Files (x86)\\Maxima\\bin\\maxima.bat");
        candidates.add("C:\\Program Files\\Maxima\\bin\\maxima.bat");
        candidates.add("C:\\Program Files (x86)\\Maxima-5.24.0\\bin\\maxima.bat");
        candidates.add("C:\\Program Files\\Maxima-5.24.0\\bin\\maxima.bat");
        candidates.add("/usr/bin/maxima");
        candidates.add("/usr/local/bin/maxima");

        for (String candidate : candidates) {
            File executable = resolvePath(candidate).toFile();
            if (executable.isFile()) {
                return executable.getAbsolutePath();
            }
        }

        String onPath = findOnPath("maxima.bat", "maxima.exe", "maxima");
        if (onPath != null) {
            return onPath;
        }

        throw new IllegalArgumentException(
                "Maxima optimization was requested, but Maxima was not found. "
                        + "Install Maxima or set optimization.maximaCommand to the executable path.");
    }

    private String resolveConfiguredMaximaCommand(String configuredCommand) {
        if (looksLikeExecutableName(configuredCommand)) {
            String onPath = findOnPath(configuredCommand);
            if (onPath != null) {
                return onPath;
            }
            throw new IllegalArgumentException("Configured Maxima command was not found on PATH: " + configuredCommand);
        }

        Path resolvedPath = resolvePath(configuredCommand);
        if (resolvedPath.toFile().isFile()) {
            return resolvedPath.toString();
        }

        throw new IllegalArgumentException(
                "Configured Maxima command was not found: " + configuredCommand
                        + ". Relative paths are resolved from " + workingDirectory + ".");
    }

    private Path resolvePath(String path) {
        Path resolved = Paths.get(path);
        if (resolved.isAbsolute()) {
            return resolved.normalize();
        }
        return workingDirectory.resolve(resolved).normalize();
    }

    private boolean looksLikeExecutableName(String command) {
        return !command.contains("/")
                && !command.contains("\\")
                && !command.contains(File.separator);
    }

    private String findOnPath(String... executableNames) {
        String path = System.getenv("PATH");
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        for (String directory : path.split(File.pathSeparator)) {
            if (directory == null || directory.trim().isEmpty()) {
                continue;
            }
            for (String executableName : executableNames) {
                File executable = new File(directory, executableName);
                if (executable.isFile()) {
                    return executable.getAbsolutePath();
                }
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
