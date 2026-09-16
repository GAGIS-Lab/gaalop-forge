// GaramonNativeEngine.java
package de.gaalop.garamon;

public interface GaramonNativeEngine {
    int dimension();
    int coefficientCount();
    double[] outer(double[] a, double[] b);
    double[] inner(double[] a, double[] b);
    double[] geometric(double[] a, double[] b);
}