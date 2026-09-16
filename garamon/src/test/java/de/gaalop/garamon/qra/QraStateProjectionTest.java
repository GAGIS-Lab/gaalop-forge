package de.gaalop.garamon.qra;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class QraStateProjectionTest {
    @Test public void matchesEverySuppliedBasisCoefficient() throws Exception {
        Pattern line = Pattern.compile("(I_)?ket([01]+)\\[(\\d+)\\] = ([^;]+);");
        for (int n = 2; n <= 6; n++) {
            QraBladeOrderMapper mapper = new QraBladeOrderMapper(2*n+2);
            Map<String, Map<Integer,Double>> expected = new HashMap<String, Map<Integer,Double>>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    getClass().getResourceAsStream("/qra/basis_n"+n+".txt"), StandardCharsets.UTF_8))) {
                String text;
                while ((text=reader.readLine()) != null) {
                    Matcher m = line.matcher(text);
                    if (!m.find()) continue;
                    String key = (m.group(1) == null ? "" : "I_") + m.group(2);
                    expected.computeIfAbsent(key, k -> new HashMap<Integer,Double>()).put(
                        mapper.gaalopToGaramonIndex(Integer.parseInt(m.group(3))), Double.parseDouble(m.group(4)));
                }
            }
            assertEquals(2*(1<<n), expected.size());
            for (int state=0; state<(1<<n); state++) {
                String bits=String.format("%"+n+"s", Integer.toBinaryString(state)).replace(' ','0');
                Map<Integer,Double> ket=QraStateProjection.ket(n,state);
                assertEquals("n="+n+" ket="+bits, expected.get(bits), ket);
                assertEquals(expected.get("I_"+bits), QraStateProjection.leftBlade(3<<(2*n), ket, 1));
            }
        }
    }

    @Test public void projectsComplexSuperpositionsThroughNineQubits() {
        for (int n=2;n<=9;n++) {
            double[] coefficients=new double[1<<(2*n+2)];
            int last=(1<<n)-1;
            for (Map.Entry<Integer,Double> e:QraStateProjection.ket(n,0).entrySet()) coefficients[e.getKey()]+=e.getValue()*0.6;
            for (Map.Entry<Integer,Double> e:QraStateProjection.leftBlade(3<<(2*n),QraStateProjection.ket(n,last),1).entrySet()) coefficients[e.getKey()]+=e.getValue()*0.8;
            QraStateProjection.Result result=QraStateProjection.solve(n,coefficients);
            assertEquals(0.6,result.real[0],1e-12);
            assertEquals(0.8,result.imaginary[last],1e-12);
            assertEquals(1,result.totalProbability,1e-12);
            assertEquals(0,result.residualNorm,1e-12);
            for(int s=1;s<last;s++) assertEquals(0,result.probabilities[s],1e-12);
        }
    }

    @Test public void preservesNonNormalizedAndOffSubspaceResults() {
        double[] input=new double[64]; input[0]=1;
        QraStateProjection.Result result=QraStateProjection.solve(2,input);
        assertEquals(1,result.real[0],1e-12);
        assertEquals(Math.sqrt(0.75),result.residualNorm,1e-12);
        Arrays.fill(input,0);
        for(Map.Entry<Integer,Double> e:QraStateProjection.ket(2,1).entrySet()) input[e.getKey()]=2*e.getValue();
        assertEquals(4,QraStateProjection.solve(2,input).totalProbability,1e-12);
    }
}
