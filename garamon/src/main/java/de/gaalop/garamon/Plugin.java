package de.gaalop.garamon;

import de.gaalop.*;
import de.gaalop.garamon.qra.GaramonQraOptimizationStrategy;
import java.awt.Image;
import java.util.Observable;

public class Plugin extends Observable implements OptimizationStrategyPlugin {
    @ConfigurationProperty(type = ConfigurationProperty.Type.BOOLEAN)
    public boolean emitZeroComponents = false;

//    @ConfigurationProperty(type = ConfigurationProperty.Type.NUMBER)
    public double zeroEpsilon = 1.0e-10;

    public OptimizationStrategy createOptimizationStrategy() {
        return new GaramonQraOptimizationStrategy(this);
    }

    public boolean isEmitZeroComponents() {
        return emitZeroComponents;
    }

    public void setEmitZeroComponents(boolean emitZeroComponents) {
        this.emitZeroComponents = emitZeroComponents;
    }

    public double getZeroEpsilon() {
        return zeroEpsilon;
    }

    public void setZeroEpsilon(double zeroEpsilon) {
        this.zeroEpsilon = zeroEpsilon;
    }


    public String getName() { return "Garamon Native QRA"; }
    public String getDescription() { return "Uses installed Garamon libraries for qra2-qra9, falls back to TBA for other algebras."; }
    public Image getIcon() { return null; }
}
