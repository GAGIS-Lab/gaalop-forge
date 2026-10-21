package de.gaalop.rest.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gaalop.rest.dto.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class QuantumLanguagesTest {
 @Test public void generatesAllExposedTargets() throws Exception {
  GaalopCompileService service=new GaalopCompileService("",new CompileHistoryService(new ObjectMapper(),false,"unused"));
  for(AlgebraPlugin algebra:new AlgebraPlugin[]{AlgebraPlugin.ALGEBRA_QRA,AlgebraPlugin.ALGEBRA_QCA,AlgebraPlugin.ALGEBRA_QGA}) {
   for(CodegenPlugin target:CodegenPlugin.values()) {
    if(target==CodegenPlugin.VERILOG || target==CodegenPlugin.GANJA || target==CodegenPlugin.VISUALIZER || target==CodegenPlugin.VIS2D || target==CodegenPlugin.GAPP || target==CodegenPlugin.GAPP_DEBUGGER || target==CodegenPlugin.GAPP_OPENCL) continue;
    CompileRequest r=new CompileRequest(); r.setAlgebraPlugins(algebra);r.setAlgebraDimension(2);r.setCodegenPlugins(target);r.setOutputMode(OutputMode.CODE_ONLY);
    r.getScript().setFunctionName("quantum_test");
    r.getScript().setOptimizeCode("?res=2*e1+3;");
    String code=service.compile(r).getOptimizeResult();
    assertFalse(algebra+" "+target,code.trim().isEmpty());
    assertTrue(algebra+" "+target,code.contains("res"));
   }
  }
 }
}
