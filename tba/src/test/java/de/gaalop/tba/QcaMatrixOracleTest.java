package de.gaalop.tba;

import de.gaalop.algebra.AlStrategy;
import de.gaalop.cfg.AlgebraDefinitionFile;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Checks the product engine against real matrices, without using its metric,
 * orthogonal-basis transformations, or multiplication algorithm as the oracle.
 * The auxiliary matrices X and Z square to +1. Fermionic generators are
 * J tensor Jordan-Wigner annihilation operators, with J = XZ and fT = transpose(f).
 */
public class QcaMatrixOracleTest {
    private static final double EPSILON = 1e-10;

    @Test
    public void bladeProductsMatchIndependentMatrices() {
        for (int n = 1; n <= 3; n++) {
            Fixture fixture = new Fixture(n);
            // Exhaust all 256 + 4,096 + 65,536 ordered blade pairs.
            for (int left = 0; left < fixture.count; left++) {
                for (int right = 0; right < fixture.count; right++) {
                    fixture.assertProduct(left, right);
                }
            }
        }
    }

    @Test
    public void mixedMultivectorsMatchMatricesAndAreAssociative() {
        Random random = new Random(0x514341L);
        for (int n = 1; n <= 3; n++) {
            Fixture fixture = new Fixture(n);
            for (int sample = 0; sample < 6; sample++) {
                Map<Integer, Double> a = fixture.randomMultivector(random);
                Map<Integer, Double> b = fixture.randomMultivector(random);
                Map<Integer, Double> c = fixture.randomMultivector(random);
                Map<Integer, Double> ab = fixture.product(a, b);
                assertMatrixEquals("mixed product n=" + n + " sample=" + sample,
                        multiply(fixture.matrix(a), fixture.matrix(b)), fixture.matrix(ab));

                Map<Integer, Double> left = fixture.product(ab, c);
                Map<Integer, Double> right = fixture.product(a, fixture.product(b, c));
                assertEquals("associativity n=" + n + " sample=" + sample, left, right);
                assertMatrixEquals("triple product n=" + n + " sample=" + sample,
                        multiply(multiply(fixture.matrix(a), fixture.matrix(b)), fixture.matrix(c)),
                        fixture.matrix(left));
                assertEquals("reversion n=" + n + " sample=" + sample,
                        fixture.reverse(ab), fixture.product(fixture.reverse(b), fixture.reverse(a)));
            }
        }
    }

    private static final class Fixture {
        private final UseAlgebra algebra;
        private final int count;
        private final int size;
        private final double[][][] blades;

        private Fixture(int n) {
            AlgebraDefinitionFile definition = new AlgebraDefinitionFile();
            definition.create("qca", n);
            definition.setUsePrecalculatedTable(false);
            definition.setUseAsRessource(true);
            definition.setProductsFilePath("algebra/qca/products.csv");
            AlStrategy.createBlades(definition);
            algebra = new UseAlgebra(definition);
            count = algebra.getBladeCount();
            size = 1 << (n + 1);
            assertEquals(1 << (2 * n + 2), count);
            Map<String, double[][]> vectors = independentVectors(n);
            blades = new double[count][][];
            for (int blade = 0; blade < count; blade++) {
                double[][] value = identity(size);
                int grade = 0;
                for (String name : algebra.getAlgebra().getBlade(blade).getBases()) {
                    double[][] vector = vectors.get(name);
                    assertNotNull("unexpected QCA generator " + name, vector);
                    // A_r wedge v = (A_r v + (-1)^r v A_r) / 2.
                    value = combine(multiply(value, vector), 0.5,
                            multiply(vector, value), grade % 2 == 0 ? 0.5 : -0.5);
                    grade++;
                }
                blades[blade] = value;
            }
        }

        private void assertProduct(int left, int right) {
            Map<Integer, Double> product = new TreeMap<>();
            algebra.geo(left, right).getValueArr(algebra.getAlgebra())
                    .forEach((blade, coefficient) -> product.put(blade, (double) coefficient));
            assertMatrixEquals("blade product " + algebra.getAlgebra().getBlade(left)
                    + " * " + algebra.getAlgebra().getBlade(right),
                    multiply(blades[left], blades[right]), matrix(product));
        }

        private Map<Integer, Double> randomMultivector(Random random) {
            Map<Integer, Double> value = new TreeMap<>();
            // Always include scalar and top-grade components, plus four mixed grades.
            value.put(0, 0.5);
            value.put(count - 1, -0.25);
            for (int term = 0; term < 4; term++) {
                value.merge(random.nextInt(count), (random.nextInt(9) - 4) / 4.0, Double::sum);
            }
            value.values().removeIf(coefficient -> coefficient == 0.0);
            return value;
        }

        private Map<Integer, Double> product(Map<Integer, Double> left, Map<Integer, Double> right) {
            Map<Integer, Double> result = new TreeMap<>();
            for (Map.Entry<Integer, Double> a : left.entrySet()) {
                for (Map.Entry<Integer, Double> b : right.entrySet()) {
                    algebra.geo(a.getKey(), b.getKey()).getValueArr(algebra.getAlgebra())
                            .forEach((blade, coefficient) -> result.merge(blade,
                                    a.getValue() * b.getValue() * coefficient, Double::sum));
                }
            }
            result.values().removeIf(coefficient -> coefficient == 0.0);
            return result;
        }

        private double[][] matrix(Map<Integer, Double> value) {
            double[][] result = new double[size][size];
            for (Map.Entry<Integer, Double> term : value.entrySet()) {
                double[][] blade = blades[term.getKey()];
                for (int row = 0; row < size; row++) {
                    for (int column = 0; column < size; column++) {
                        result[row][column] += term.getValue() * blade[row][column];
                    }
                }
            }
            return result;
        }

        private Map<Integer, Double> reverse(Map<Integer, Double> value) {
            Map<Integer, Double> result = new TreeMap<>();
            value.forEach((blade, coefficient) -> {
                int grade = algebra.getGrade(blade);
                int sign = (grade * (grade - 1) / 2) % 2 == 0 ? 1 : -1;
                result.put(blade, sign * coefficient);
            });
            return result;
        }
    }

    private static Map<String, double[][]> independentVectors(int n) {
        Map<String, double[][]> vectors = new HashMap<>();
        double[][] x = {{0, 1}, {1, 0}};
        double[][] z = {{1, 0}, {0, -1}};
        double[][] j = {{0, -1}, {1, 0}};
        int states = 1 << n;
        vectors.put("ei1", tensor(x, identity(states)));
        vectors.put("ei2", tensor(z, identity(states)));
        for (int mode = 0; mode < n; mode++) {
            double[][] annihilate = new double[states][states];
            for (int state = 0; state < states; state++) {
                if ((state & (1 << mode)) != 0) {
                    int parity = Integer.bitCount(state & ((1 << mode) - 1));
                    annihilate[state ^ (1 << mode)][state] = parity % 2 == 0 ? 1 : -1;
                }
            }
            double[][] f = tensor(j, annihilate);
            vectors.put("f" + (mode + 1), f);
            vectors.put("f" + (mode + 1) + "T", transpose(f));
        }
        return vectors;
    }

    private static double[][] identity(int size) {
        double[][] result = new double[size][size];
        for (int i = 0; i < size; i++) result[i][i] = 1;
        return result;
    }

    private static double[][] multiply(double[][] left, double[][] right) {
        int size = left.length;
        double[][] result = new double[size][size];
        for (int row = 0; row < size; row++) {
            for (int inner = 0; inner < size; inner++) {
                if (left[row][inner] == 0) continue;
                for (int column = 0; column < size; column++) {
                    result[row][column] += left[row][inner] * right[inner][column];
                }
            }
        }
        return result;
    }

    private static double[][] combine(double[][] a, double factorA, double[][] b, double factorB) {
        double[][] result = new double[a.length][a.length];
        for (int row = 0; row < a.length; row++) {
            for (int column = 0; column < a.length; column++) {
                result[row][column] = factorA * a[row][column] + factorB * b[row][column];
            }
        }
        return result;
    }

    private static double[][] transpose(double[][] source) {
        double[][] result = new double[source.length][source.length];
        for (int row = 0; row < source.length; row++) {
            for (int column = 0; column < source.length; column++) {
                result[row][column] = source[column][row];
            }
        }
        return result;
    }

    private static double[][] tensor(double[][] a, double[][] b) {
        int size = a.length * b.length;
        double[][] result = new double[size][size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                result[row][column] = a[row / b.length][column / b.length]
                        * b[row % b.length][column % b.length];
            }
        }
        return result;
    }

    private static void assertMatrixEquals(String context, double[][] expected, double[][] actual) {
        for (int row = 0; row < expected.length; row++) {
            assertArrayEquals(context + " row=" + row, expected[row], actual[row], EPSILON);
        }
    }
}
