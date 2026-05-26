package de.gaalop.rest.dto;

public enum AlgebraPlugin {
    ALGEBRA_2D("2d", "imaginary numbers"),
    ALGEBRA_3D("3d", "euclidean geometric algebra"),
    ALGEBRA_2D_PGA("2dpga", "2D projective geometric algebra"),
    ALGEBRA_3D_PGA("3dpga", "3D projective geometric algebra"),
    ALGEBRA_CRA("cra", "compass ruler algebra"),
    ALGEBRA_STA("sta", "space-time algebra"),
    ALGEBRA_CGA("cga", "conformal geometric algebra"),
    ALGEBRA_GAC("gac", "geometric algebra for conics"),
    ALGEBRA_DCGA("dcga", "double conformal geometric algebra"),
    ALGEBRA_CCGA("ccga", "cubic CGA"),
    ALGEBRA_QGA("qga", "quantum bit geometric algebra");

    private final String algebraId;
    private final String displayName;

    AlgebraPlugin(String algebraId, String displayName) {
        this.algebraId = algebraId;
        this.displayName = displayName;
    }

    public String getAlgebraId() {
        return algebraId;
    }

    public String getDisplayName() {
        return displayName;
    }
}
