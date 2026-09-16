// QraNativeLibrary.java
package de.gaalop.garamon.qra;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import de.gaalop.garamon.GaramonNativeEngine;
import java.io.File;

public final class QraNativeLibrary implements GaramonNativeEngine {
    private final QraAlgebraId id;
    private final NativeLibrary lib;
    private final int dimension;
    private final int coeffCount;

    QraNativeLibrary(QraAlgebraId id, File dll) {
        this.id = id;
        System.load(dll.getAbsolutePath());
        this.lib = NativeLibrary.getInstance(dll.getAbsolutePath());
        this.dimension = callInt("dimension");
        this.coeffCount = callInt("coeff_count");
        int expectedDimension = 2 * id.nqubits + 2;
        if (dimension != expectedDimension || coeffCount != (1 << expectedDimension)) {
            throw new IllegalStateException("Native library dimensions do not match " + id.id);
        }
    }

    private int callInt(String suffix) {
        return lib.getFunction(id.prefix + suffix).invokeInt(new Object[0]);
    }

    private double[] callProduct(String suffix, double[] a, double[] b) {
        if (a.length != coeffCount || b.length != coeffCount) {
            throw new IllegalArgumentException("Expected dense length " + coeffCount);
        }
        double[] out = new double[coeffCount];
        Function f = lib.getFunction(id.prefix + suffix);
        int status = f.invokeInt(new Object[] { a, b, out });
        if (status != 0) throw new IllegalStateException(id.id + "_" + suffix + " failed: " + status);
        return out;
    }

    public int dimension() { return dimension; }
    public int coefficientCount() { return coeffCount; }
    public double[] outer(double[] a, double[] b) { return callProduct("outer_dense", a, b); }
    public double[] inner(double[] a, double[] b) { return callProduct("inner_dense", a, b); }
    public double[] geometric(double[] a, double[] b) { return callProduct("geometric_dense", a, b); }
}
