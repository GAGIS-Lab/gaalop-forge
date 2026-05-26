package de.gaalop.rest.dto;

public class OptimizationOptions {

    private Boolean cse = Boolean.FALSE;
    private Boolean maxima = Boolean.FALSE;

    public Boolean getCse() {
        return cse;
    }

    public void setCse(Boolean cse) {
        this.cse = cse;
    }

    public Boolean getMaxima() {
        return maxima;
    }

    public void setMaxima(Boolean maxima) {
        this.maxima = maxima;
    }
}
