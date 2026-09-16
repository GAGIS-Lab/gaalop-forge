package de.gaalop.tba;

/**
 * Represents a blade by its index.
 * Stores also a prefactor
 * 
 * @author Christian Steinmetz
 */
public class BladeRef {

    private float prefactor;
    private int index;

    public BladeRef(float prefactor, int index) {
        this.prefactor = prefactor;
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

    public float getPrefactor() {
        return prefactor;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public void setPrefactor(float prefactor) {
        this.prefactor = prefactor;
    }

    @Override
    public String toString() {
        return prefactor+"["+index+"]";
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 59 * hash + Float.floatToIntBits(prefactor);
        hash = 59 * hash + this.index;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final BladeRef other = (BladeRef) obj;
        if (this.prefactor != other.prefactor) {
            return false;
        }
        if (this.index != other.index) {
            return false;
        }
        return true;
    }
}
