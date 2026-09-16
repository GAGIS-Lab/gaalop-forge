package de.gaalop.rest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gaalop.rest.dto.*;
import de.gaalop.garamon.qra.QraStateProjection;
import org.junit.Test;
import static org.junit.Assert.*;

public class QcaVisualizationTest {
    private final GaalopCompileService service = new GaalopCompileService("", new CompileHistoryService(new ObjectMapper(), false, "unused"));
    private CompileRequest request(int n,String source,String assignments,String selected) {
        CompileRequest r=new CompileRequest(); r.setAlgebraPlugins(AlgebraPlugin.ALGEBRA_QCA); r.setAlgebraDimension(n);
        r.setCodegenPlugins(CodegenPlugin.JAVA); r.setOutputMode(OutputMode.CODE_AND_VISUALIZATION);
        r.getScript().setOptimizeCode(source); r.getScript().setVariableAssignments(assignments); r.getScript().setMultivectorsVisualized(selected); return r;
    }
    private String vacuum(int n) {
        StringBuilder s=new StringBuilder("j=ei1*ei2; Id=");
        for(int k=1;k<=n;k++) { if(k>1)s.append('*'); s.append('f').append(k).append("*f").append(k).append('T'); }
        return s.append(';').toString();
    }
    @Test public void allBasisStatesAndComplexPhases() throws Exception {
        for(int n=1;n<=3;n++) for(int bits=0;bits<(1<<n);bits++) {
            String ket=""; for(int k=1;k<=n;k++) if((bits&(1<<(n-k)))!=0) ket+="f"+k+"T*";
            String source=vacuum(n)+"?psi=(cos(theta)+j*sin(theta))*"+ket+"Id;";
            QraStateProjection.Result result=service.compile(request(n,source,"theta=0.37;",":psi;")).getQuantumResults().get("psi");
            for(int k=0;k<(1<<n);k++) assertEquals(k==bits?1:0,result.probabilities[k],1e-10);
            assertEquals(Math.cos(0.37),result.real[bits],1e-10);
            assertEquals(Math.sin(0.37),result.imaginary[bits],1e-10);
            assertEquals(0,result.residualNorm,1e-10);
        }
    }
    @Test public void superpositionParametersSelectionAndCodeAreIndependent() throws Exception {
        CompileRequest r=request(2,vacuum(2)+"?debug=7; ?psi=cos(theta/2)*Id+j*sin(theta/2)*f1T*f2T*Id;","theta=1.5707963267948966;","selected=psi; :selected;");
        CompileResponse first=service.compile(r);
        assertEquals(1,first.getQuantumResults().size());
        assertEquals(0.5,first.getQuantumResults().get("selected").probabilities[0],1e-10);
        assertEquals(0.5,first.getQuantumResults().get("selected").probabilities[3],1e-10);
        r.getScript().setVariableAssignments("theta=0;");
        CompileResponse second=service.compile(r);
        assertEquals(first.getOptimizeResult(),second.getOptimizeResult());
        assertEquals(1,second.getQuantumResults().get("selected").probabilities[0],1e-10);
        r.setOutputMode(OutputMode.VISUALIZATION_ONLY);
        assertEquals("",service.compile(r).getOptimizeResult());
        r.getScript().setMultivectorsVisualized(":psi; :Id;");
        assertEquals(2,service.compile(r).getQuantumResults().size());
        r.getScript().setVariableAssignments("");
        assertThrows(Exception.class,()->service.compile(r));
    }
    @Test public void hadamardAndResidualAreNotSilentlyNormalized() throws Exception {
        CompileRequest r=request(1,vacuum(1)+"H=(f1*f1T+f1+f1T-f1T*f1)/sqrt(2); ?psi=H*Id;","",":psi;");
        QraStateProjection.Result result=service.compile(r).getQuantumResults().get("psi");
        assertEquals(0.5,result.probabilities[0],1e-10); assertEquals(0.5,result.probabilities[1],1e-10);
        r.getScript().setOptimizeCode(vacuum(1)+"?psi=2*Id+ei1;");
        result=service.compile(r).getQuantumResults().get("psi");
        assertEquals(4,result.totalProbability,1e-10); assertEquals(1,result.residualNorm,1e-10);
    }
    @Test public void bellCircuitAndPauliYMatchComplexStateVectors() throws Exception {
        String source=vacuum(2)+"P1=f1*f1T; N1=f1T*f1; Z1=P1-N1;"
                + "H1=(P1+f1+f1T-N1)/sqrt(2); CNOT=P1+N1*Z1*(f2+f2T); ?psi=CNOT*H1*Id;";
        QraStateProjection.Result result=service.compile(request(2,source,"",":psi;")).getQuantumResults().get("psi");
        assertArrayEquals(new double[]{1/Math.sqrt(2),0,0,1/Math.sqrt(2)},result.real,1e-10);
        assertEquals(0,result.residualNorm,1e-10);
        result=service.compile(request(1,vacuum(1)+"Y=j*(f1T-f1); ?psi=Y*Id;","",":psi;")).getQuantumResults().get("psi");
        assertArrayEquals(new double[]{0,1},result.imaginary,1e-10);
    }
    @Test public void unknownStateAndNonFiniteParametersAreRejected() throws Exception {
        CompileRequest r=request(1,vacuum(1)+"?psi=Id;","",":missing;");
        assertThrows(Exception.class,()->service.compile(r));
        CompileRequest invalid=request(1,vacuum(1)+"?psi=sqrt(theta)*Id;","theta=-1;",":psi;");
        assertThrows(Exception.class,()->service.compile(invalid));
    }
}
