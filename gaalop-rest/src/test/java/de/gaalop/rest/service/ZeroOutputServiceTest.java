package de.gaalop.rest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gaalop.rest.dto.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real parser/algebra/optimizer/generator regressions, not hand-built Python. */
public class ZeroOutputServiceTest {
    private final GaalopCompileService service = new GaalopCompileService("",
            new CompileHistoryService(new ObjectMapper(), false, "unused"));

    @Test
    public void zeroOutputsSurviveEveryWebGeneratorAndCse() throws Exception {
        CodegenPlugin[] targets = {CodegenPlugin.PYTHON, CodegenPlugin.CSHARP,
                CodegenPlugin.CPP, CodegenPlugin.JAVA, CodegenPlugin.RUST,
                CodegenPlugin.JULIA, CodegenPlugin.MATLAB, CodegenPlugin.MATHEMATICA,
                CodegenPlugin.COMPRESSED, CodegenPlugin.CLUCALC, CodegenPlugin.GAALET_OUTPUT,
                CodegenPlugin.GANJA, CodegenPlugin.GAPP, CodegenPlugin.GAPP_OPENCL,
                CodegenPlugin.LATEX, CodegenPlugin.DOT};
        for (boolean cse : new boolean[]{false, true}) {
            for (CodegenPlugin target : targets) {
                String zero = compile(target, cse, "zero", "?z=0;");
                assertTrue(target + " lost zero output:\n" + zero, zero.contains("z") && zero.contains("0"));
                String mixed = compile(target, cse, "mixed", "?a=2; ?z=e1.e2;");
                assertTrue(target + ":\n" + mixed, mixed.contains("a") && mixed.contains("z"));
                String projected = compile(target, cse, "projected", "//#pragma output v e1 e2 e3\n?v=0*e1;");
                assertTrue(target + ":\n" + projected, projected.contains("v"));
                String sparse = compile(target, cse, "sparse", "//#pragma output v e1 e2 e3\n?v=x*e1;");
                assertTrue(target + ":\n" + sparse, sparse.contains("v") && sparse.contains("x"));
                if (target == CodegenPlugin.PYTHON) {
                    assertTrue(zero, zero.contains("z_1 = 0.0"));
                    assertTrue(mixed, mixed.contains("z_1 = 0.0"));
                    for (String blade : new String[]{"e1", "e2", "e3"}) assertTrue(projected, projected.contains("v_" + blade + " = 0.0"));
                    assertTrue(sparse, sparse.contains("v_e2 = 0.0") && sparse.contains("v_e3 = 0.0"));
                }
                if (target == CodegenPlugin.JAVA) assertTrue(zero, zero.contains("result.put(\"z$0\",z$0)"));
                if (target == CodegenPlugin.GAPP) assertTrue(zero, zero.contains("resetMv z[32]"));
                if (target == CodegenPlugin.GAPP_OPENCL) {
                    assertFalse(zero, zero.contains("float-1"));
                    assertFalse(zero, zero.contains("GAPPConstant@"));
                }
            }
        }
    }

    @Test
    public void arrayOutputsSurviveEliminationAndClearCallerOwnedBuffers() throws Exception {
        for (boolean cse : new boolean[]{false, true}) {
            for (boolean python : new boolean[]{false, true}) {
                de.gaalop.CodeGenerator generator;
                if (python) {
                    de.gaalop.python.Plugin plugin = new de.gaalop.python.Plugin();
                    plugin.setUseArrays(true);
                    generator = plugin.createCodeGenerator();
                } else {
                    de.gaalop.csharp.Plugin plugin = new de.gaalop.csharp.Plugin();
                    plugin.setUseArrays(true);
                    generator = plugin.createCodeGenerator();
                }
                de.gaalop.tba.Plugin optimizer = new de.gaalop.tba.Plugin();
                optimizer.setOptGCSE(cse);
                de.gaalop.globalSettings.Plugin settings = new de.gaalop.globalSettings.Plugin();
                settings.setOutputToNormalBase(false);
                de.gaalop.algebra.Plugin algebra = new de.gaalop.algebra.Plugin();
                String[] names = {"zero", "mixed", "projected", "sparse"};
                String[] scripts = {"?z=0;", "?a=2; ?z=0;", "//#pragma output v e1 e2 e3\n?v=0*e1;", "//#pragma output v e1 e2 e3\n?v=x*e1;"};
                for (int i = 0; i < names.length; i++) {
                    de.gaalop.CompilerFacade facade = new de.gaalop.CompilerFacade(
                            new de.gaalop.clucalc.input.Plugin().createCodeParser(), settings.createGlobalSettingsStrategy(),
                            graph -> { }, algebra.createAlgebraStrategy(), optimizer.createOptimizationStrategy(),
                            generator, "cga", true, algebra.getAdditionalBaseDirectory());
                    String code = facade.compile(new de.gaalop.InputFile("zero_" + names[i] + ".clu", scripts[i])).iterator().next().getContent();
                    if (python) assertTrue(code, code.contains(".zeros(32)"));
                    else assertTrue(code, code.contains("Array.Clear("));
                    String directory = System.getProperty("gaalop.zero.fixtureDir");
                    if (directory != null) {
                        Path folder = Paths.get(directory, cse ? "cse" : "plain", python ? "PYTHON_ARRAY" : "CSHARP_ARRAY");
                        Files.createDirectories(folder);
                        Files.write(folder.resolve(names[i] + ".txt"), code.getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
        }
    }

    @Test
    public void typedZeroComponentsAndScalarCseTemporariesHaveCompleteReturns() throws Exception {
        for (boolean cse : new boolean[]{false, true}) {
            String typed = compile(CodegenPlugin.PYTHON, cse, "typed",
                    "//#pragma return v typed tuple as (<e1>, <e2>, <e3>)\n?v=0*e1;");
            assertFalse(typed, typed.contains("<e1>"));
            assertTrue(typed, typed.contains("v_e1 = 0.0") && typed.contains("v_e2 = 0.0") && typed.contains("v_e3 = 0.0"));
            String code = compile(CodegenPlugin.PYTHON, cse, "cse",
                    "?z=0; ?r=(x+1)*(x+1)+(x+1);");
            assertTrue(code, code.contains("z_1 = 0.0") && code.contains("return ("));
            compile(CodegenPlugin.CSHARP, cse, "cse", "?z=0; ?r=(x+1)*(x+1)+(x+1);");
        }
    }

    private String compile(CodegenPlugin target, boolean cse, String name, String source) throws Exception {
        CompileRequest request = new CompileRequest();
        request.setAlgebraPlugins(AlgebraPlugin.ALGEBRA_CGA);
        request.setCodegenPlugins(target);
        request.setOutputMode(OutputMode.CODE_ONLY);
        request.getOptimization().setCse(cse);
        request.getScript().setFunctionName("zero_" + name);
        request.getScript().setOptimizeCode(source);
        CompileResponse response = service.compile(request);
        String code = target == CodegenPlugin.GANJA ? response.getVisualizationCode() : response.getOptimizeResult();
        assertFalse(target + " generated empty " + name, code.trim().isEmpty());
        String directory = System.getProperty("gaalop.zero.fixtureDir");
        if (directory != null) {
            Path folder = Paths.get(directory, cse ? "cse" : "plain", target.name());
            Files.createDirectories(folder);
            Files.write(folder.resolve(name + ".txt"), code.getBytes(StandardCharsets.UTF_8));
        }
        return code;
    }
}
