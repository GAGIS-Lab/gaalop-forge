package de.gaalop.rest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gaalop.rest.dto.*;
import org.junit.Test;
import org.junit.Assume;
import static org.junit.Assert.*;
import java.nio.file.*;

public class QraServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final GaalopCompileService service = new GaalopCompileService("",
            new CompileHistoryService(mapper, false, "unused-history"));

    private CompileRequest request(int n, String source) {
        CompileRequest r=new CompileRequest();
        r.setAlgebraPlugins(AlgebraPlugin.ALGEBRA_QRA);
        r.setAlgebraDimension(n);
        r.setCodegenPlugins(CodegenPlugin.CPP);
        r.setOutputMode(OutputMode.CODE_AND_VISUALIZATION);
        ScriptInput script=new ScriptInput(); script.setOptimizeCode(source); r.setScript(script);
        return r;
    }

    @Test public void rejectsInvalidDimensionsBeforeCompilation() {
        for(int n:new int[]{-1,0,1,10,20}) {
            assertThrows(IllegalArgumentException.class,()->service.compile(request(n,"?res=1;")));
        }
        CompileRequest unsupported=request(2,"?res=1;");
        unsupported.getOptimization().setCse(true);
        assertThrows(IllegalArgumentException.class,()->service.compile(unsupported));
        unsupported.getOptimization().setCse(false);
        unsupported.getOptimization().setMaxima(true);
        assertThrows(IllegalArgumentException.class,()->service.compile(unsupported));
    }

    @Test public void nativeGroverAndScriptVisualization() throws Exception {
        Assume.assumeTrue("Set gaalop.garamon.nativeDir to run native integration tests",
                System.getProperty("gaalop.garamon.nativeDir") != null);
        Path examples=Paths.get("../examples/qra");
        String[] names={"Grover_n2_s2.txt","Grover_n3_s5_6.txt","Grover_n4_s8.txt","Grover_n5_s4.txt","Grover_n6_s17.txt"};
        for(int n=2;n<=6;n++) {
            String source=new String(Files.readAllBytes(examples.resolve(names[n-2])),java.nio.charset.StandardCharsets.UTF_8);
            CompileRequest r=request(n,source.replace("?res;", ""));
            r.getScript().setMultivectorsVisualized(":res;");
            CompileResponse response=service.compile(r);
            assertEquals("200",response.getStatusCode());
            assertFalse(response.getOptimizeResult().isEmpty());
            de.gaalop.garamon.qra.QraStateProjection.Result result=response.getQuantumResults().get("res");
            assertNotNull(result);
            assertEquals(1,result.totalProbability,1e-8);
            assertEquals(0,result.residualNorm,1e-8);
            // Compare with the independent amplitude-amplification probability formula.
            int marked=n==3 ? 2 : 1;
            int iterations=n==2||n==3 ? 1 : n==4 ? 2 : n==5 ? 3 : 5;
            double theta=Math.asin(Math.sqrt((double)marked/(1<<n)));
            double expected=Math.pow(Math.sin((2*iterations+1)*theta),2);
            double actual=n==3 ? result.probabilities[5]+result.probabilities[6]
                    : result.probabilities[n==2?2:n==4?8:n==5?4:17];
            assertEquals("Grover n="+n,expected,actual,1e-8);
        }
    }

    @Test public void numericAssignmentsAreUsedInCodeOnlyMode() throws Exception {
        Assume.assumeTrue(System.getProperty("gaalop.garamon.nativeDir") != null);
        CompileRequest r=request(2,"?res=x*e1*e1;");
        r.setOutputMode(OutputMode.CODE_ONLY);
        r.getScript().setVariableAssignments("x=3;");
        assertTrue(service.compile(r).getOptimizeResult().contains("3.0"));
    }

    @Test public void highQubitScriptsProduceImaginaryBasisStates() throws Exception {
        Assume.assumeTrue(System.getProperty("gaalop.garamon.nativeDir") != null);
        for(int n=7;n<=9;n++) {
            StringBuilder script=new StringBuilder("i=er1*er2;\n");
            for(int k=1;k<=n;k++) script.append("f").append(k).append("=0.5*(e").append(k).append("+i*e").append(n+k).append("); f").append(k).append("T=0.5*(e").append(k).append("-i*e").append(n+k).append(");\n");
            script.append("Id=");
            for(int k=1;k<=n;k++) { if(k>1) script.append('*'); script.append('f').append(k).append("*f").append(k).append('T'); }
            script.append("; ?res=i");
            for(int k=1;k<=n;k++) script.append("*f").append(k).append('T');
            script.append("*Id;");
            CompileRequest request = request(n,script.toString());
            request.getScript().setMultivectorsVisualized(":res;");
            CompileResponse response=service.compile(request);
            de.gaalop.garamon.qra.QraStateProjection.Result result=response.getQuantumResults().get("res");
            assertEquals(1,result.imaginary[(1<<n)-1],1e-10);
            assertEquals(1,result.totalProbability,1e-10);
            assertEquals(0,result.residualNorm,1e-10);
        }
    }

    @Test public void visualizationUsesAssignedParametersAndOnlySelectedStates() throws Exception {
        Assume.assumeTrue(System.getProperty("gaalop.garamon.nativeDir") != null);
        CompileRequest r = request(2, "i=er1*er2; f1=0.5*(e1+i*e3); f1T=0.5*(e1-i*e3);"
                + "f2=0.5*(e2+i*e4); f2T=0.5*(e2-i*e4); Id=f1*f1T*f2*f2T;"
                + "?debug=7; ?psi=cos(theta/2)*Id+sin(theta/2)*f1T*Id;");
        r.getScript().setVariableAssignments("theta=0;");
        r.getScript().setMultivectorsVisualized("selected=psi;\n:selected;");
        CompileResponse first = service.compile(r);
        assertEquals(java.util.Collections.singleton("selected"), first.getQuantumResults().keySet());
        assertEquals(1, first.getQuantumResults().get("selected").probabilities[0], 1e-10);
        assertTrue(first.getOptimizeResult().contains("debug"));
        r.getScript().setVariableAssignments("theta=3.141592653589793;");
        assertEquals(1, service.compile(r).getQuantumResults().get("selected").probabilities[2], 1e-10);
        r.getScript().setMultivectorsVisualized(":psi;\n:Id;");
        assertEquals(2, service.compile(r).getQuantumResults().size());
        r.getScript().setMultivectorsVisualized("");
        assertTrue(service.compile(r).getQuantumResults().isEmpty());
        r.getScript().setVariableAssignments("");
        assertThrows(de.gaalop.CompilationException.class, () -> service.compile(r));
    }
}
