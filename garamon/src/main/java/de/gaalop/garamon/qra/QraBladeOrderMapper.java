// QraBladeOrderMapper.java
package de.gaalop.garamon.qra;

import java.util.Arrays;

public final class QraBladeOrderMapper {
    private final int size;
    private final int[] xorToGaalop;
    private final int[] gaalopToXor;

    public QraBladeOrderMapper(int dimension) {
        this.size = 1 << dimension;
        this.xorToGaalop = new int[size];
        this.gaalopToXor = new int[size];
        Arrays.fill(xorToGaalop, -1);
        Arrays.fill(gaalopToXor, -1);
        xorToGaalop[0] = 0;
        gaalopToXor[0] = 0;
        int[] index = {1};
        for (int grade = 1; grade <= dimension; grade++) {
            assign(0, grade, 0, index, dimension);
        }
    }

    private void assign(int start, int remaining, int mask, int[] index, int dim) {
        if (remaining == 0) {
            int gaalop = index[0]++;
            xorToGaalop[mask] = gaalop;
            gaalopToXor[gaalop] = mask;
            return;
        }
        for (int i = start; i <= dim - remaining; i++) {
            assign(i + 1, remaining - 1, mask | (1 << i), index, dim);
        }
    }

    public int gaalopToGaramonIndex(int gaalopIndex) { return gaalopToXor[gaalopIndex]; }

    public double[] garamonToGaalop(double[] x) {
        double[] r = new double[size];
        for (int i = 0; i < size; i++) r[xorToGaalop[i]] = x[i];
        return r;
    }
}