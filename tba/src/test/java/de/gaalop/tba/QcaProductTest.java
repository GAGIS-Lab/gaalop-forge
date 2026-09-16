package de.gaalop.tba;

import de.gaalop.algebra.AlStrategy;
import de.gaalop.cfg.AlgebraDefinitionFile;
import de.gaalop.tba.table.Parser;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;
import static org.junit.Assert.*;

public class QcaProductTest {
    private UseAlgebra qca(int n) {
        AlgebraDefinitionFile definition = new AlgebraDefinitionFile();
        definition.create("qca", n);
        definition.setUseAsRessource(true);
        definition.setProductsFilePath("algebra/qca/products.csv");
        AlStrategy.createBlades(definition);
        assertEquals("f1", definition.getBladeString(3));
        return new UseAlgebra(definition);
    }

    private TreeMap<Integer, Float> product(UseAlgebra algebra, int a, int b) {
        return algebra.getTableGeo().getProduct(a, b).getValueArr(algebra.getAlgebra());
    }

    @Test
    public void fermionicRelationsAndFractionalCoefficients() {
        for (int n = 1; n <= 3; n++) {
            UseAlgebra algebra = qca(n);
            for (int i = 0; i < n; i++) {
                int f = 3 + 2 * i;
                assertTrue(product(algebra, f, f).isEmpty());
                assertTrue(product(algebra, f + 1, f + 1).isEmpty());
                TreeMap<Integer, Float> forward = product(algebra, f, f + 1);
                assertEquals(0.5f, forward.get(0), 0);
                TreeMap<Integer, Float> sum = new TreeMap<>(forward);
                product(algebra, f + 1, f).forEach((k, v) -> sum.merge(k, v, Float::sum));
                assertEquals(1f, sum.get(0), 0);
                for (Map.Entry<Integer, Float> entry : sum.entrySet()) {
                    if (entry.getKey() != 0) assertEquals(0f, entry.getValue(), 0);
                }
                for (int j = i + 1; j < n; j++) {
                    int other = 3 + 2 * j;
                    for (int offset = 0; offset < 2; offset++) {
                        TreeMap<Integer, Float> cross = product(algebra, f, other + offset);
                        product(algebra, other + offset, f).forEach((k, v) -> cross.merge(k, v, Float::sum));
                        for (float value : cross.values()) assertEquals(0f, value, 0);
                    }
                }
            }
        }
    }

    @Test
    public void auxiliaryVectorsAndTheirBivectorHaveCorrectSquares() {
        for (int n = 1; n <= 3; n++) {
            UseAlgebra algebra = qca(n);
            assertEquals(1f, product(algebra, 1, 1).get(0), 0);
            assertEquals(1f, product(algebra, 2, 2).get(0), 0);
            TreeMap<Integer, Float> imaginary = product(algebra, 1, 2);
            assertEquals(1, imaginary.size());
            Map.Entry<Integer, Float> blade = imaginary.firstEntry();
            assertNotEquals(Integer.valueOf(0), blade.getKey());
            TreeMap<Integer, Float> squared = product(algebra, blade.getKey(), blade.getKey());
            assertEquals(1, squared.size());
            assertEquals(-1f, blade.getValue() * blade.getValue() * squared.get(0), 0);
        }
    }

    @Test
    public void generatedSignatureMatchesUpstreamDisplaySignature() {
        AlgebraDefinitionFile definition = new AlgebraDefinitionFile();
        for (int n = 1; n <= 10; n++) {
            definition.create("qca", n);
            assertEquals("Cl(" + (n + 2) + "," + n + ",0)", definition.getSignatureString());
            assertEquals(AlgebraDefinitionFile.getSignatureString("qca", n), definition.getSignatureString());
        }
    }

    @Test
    public void fractionalTextTableRoundTrip() {
        Multivector value = new Multivector();
        value.addBlade(new BladeRef(0.5f, 0));
        value.addBlade(new BladeRef(-0.25f, 3));
        value.addBlade(new BladeRef(0.0000001f, 5));
        assertEquals(value.getValueArr(null), Parser.parseMultivector(value.print()).getValueArr(null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZeroQubits() {
        new AlgebraDefinitionFile().create("qca", 0);
    }
}
