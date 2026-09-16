package de.gaalop.garamon.qra;

import java.util.HashMap;
import java.util.Map;

/** Projects onto ket_s and I*ket_s in Cl(2n+2,0), without a dense matrix or SVD.
 * Input indices are XOR blade masks, NOT GAALOP's grade-ordered indices.
 * The orthogonal ket columns each have squared Euclidean coefficient norm 2^-n.
 */
public final class QraStateProjection {
    private QraStateProjection() { }

    public static final class Result {
        public final int nqubits;
        public final String[] labels;
        public final double[] real, imaginary, probabilities;
        public final double totalProbability, residualNorm;
        public Result(int n, double[] re, double[] im, double residual) {
            nqubits = n; real = re; imaginary = im; residualNorm = residual;
            labels = new String[re.length]; probabilities = new double[re.length];
            double total = 0;
            for (int i = 0; i < re.length; i++) {
                labels[i] = String.format("%" + n + "s", Integer.toBinaryString(i)).replace(' ', '0');
                probabilities[i] = re[i] * re[i] + im[i] * im[i];
                total += probabilities[i];
            }
            totalProbability = total;
        }
    }

    public static Result solve(int n, double[] coefficients) {
        check(n);
        if (coefficients.length != (1 << (2 * n + 2))) throw new IllegalArgumentException("Wrong QRA coefficient count");
        for (double x : coefficients) if (!Double.isFinite(x)) throw new IllegalArgumentException("Non-finite QRA coefficient");
        double[] remaining = coefficients.clone();
        double[] re = new double[1 << n], im = new double[1 << n];
        int imaginaryMask = (1 << (2 * n)) | (1 << (2 * n + 1));
        for (int state = 0; state < re.length; state++) {
            Map<Integer, Double> ket = ket(n, state);
            Map<Integer, Double> iket = leftBlade(imaginaryMask, ket, 1);
            re[state] = project(ket, coefficients, remaining, 1 << n);
            im[state] = project(iket, coefficients, remaining, 1 << n);
        }
        double residual = 0;
        for (double x : remaining) residual = Math.hypot(residual, x);
        return new Result(n, re, im, residual);
    }

    private static double project(Map<Integer, Double> basis, double[] input, double[] residual, int scale) {
        double dot = 0;
        for (Map.Entry<Integer, Double> e : basis.entrySet()) dot += input[e.getKey()] * e.getValue();
        double amplitude = dot * scale;
        for (Map.Entry<Integer, Double> e : basis.entrySet()) residual[e.getKey()] -= amplitude * e.getValue();
        return amplitude;
    }

    /** Exact dyadic construction matching the supplied GAALOPScripts. */
    public static Map<Integer, Double> ket(int n, int state) {
        check(n);
        if (state < 0 || state >= (1 << n)) throw new IllegalArgumentException("Invalid basis state");
        int imaginary = (1 << (2 * n)) | (1 << (2 * n + 1));
        Map<Integer, Double> result = new HashMap<Integer, Double>();
        result.put(0, 1.0);
        // Id = product_k (1 - e_k * I * e_(n+k)) / 2.
        for (int k = 0; k < n; k++) {
            int a = 1 << k, b = 1 << (n + k);
            int mask = a ^ imaginary ^ b;
            double sign = -0.5 * sign(a, imaginary) * sign(a ^ imaginary, b);
            Map<Integer, Double> next = leftBlade(mask, result, sign);
            addScaled(next, result, 0.5);
            result = next;
        }
        // ket_s = f1T^s1 ... fnT^sn Id. Apply rightmost operator first.
        for (int k = n - 1; k >= 0; k--) {
            if ((state & (1 << (n - 1 - k))) == 0) continue;
            int a = 1 << k, b = 1 << (n + k);
            Map<Integer, Double> next = leftBlade(a, result, 0.5);
            addScaled(next, leftBlade(imaginary ^ b, result, -0.5 * sign(imaginary, b)), 1);
            result = next;
        }
        return result;
    }

    public static Map<Integer, Double> leftBlade(int mask, Map<Integer, Double> input, double scale) {
        Map<Integer, Double> out = new HashMap<Integer, Double>();
        for (Map.Entry<Integer, Double> e : input.entrySet()) {
            out.put(mask ^ e.getKey(), scale * sign(mask, e.getKey()) * e.getValue());
        }
        return out;
    }

    private static void addScaled(Map<Integer, Double> target, Map<Integer, Double> source, double scale) {
        for (Map.Entry<Integer, Double> e : source.entrySet()) {
            double value = target.getOrDefault(e.getKey(), 0.0) + scale * e.getValue();
            if (value == 0) target.remove(e.getKey()); else target.put(e.getKey(), value);
        }
    }

    private static int sign(int left, int right) {
        int parity = 0;
        while (left != 0) {
            int bit = Integer.numberOfTrailingZeros(left);
            parity ^= Integer.bitCount(right & ((1 << bit) - 1)) & 1;
            left &= left - 1;
        }
        return parity == 0 ? 1 : -1;
    }

    private static void check(int n) {
        if (n < 2 || n > 9) throw new IllegalArgumentException("QRA supports 2 through 9 qubits");
    }
}
