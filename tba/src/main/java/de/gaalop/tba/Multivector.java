package de.gaalop.tba;

import java.util.LinkedList;
import java.util.TreeMap;
import java.util.Vector;

/**
 * Represents a multivector, e.g. a vector of blades
 * @author Christian Steinmetz
 */
public class Multivector {

    private Vector<BladeRef> blades;

    public Multivector() {
        blades = new Vector<BladeRef>();
    }

    /**
     * Adds a BladeRef object to this multivector
     * @param blade The bladeref object to be added
     */
    public void addBlade(BladeRef blade) {
        blades.add(blade);
    }

    /**
     * Returns the values of all non-null blades in this multivector
     * @return The values of all non-null blades
     */
    public TreeMap<Integer, Float> getValueArr(Algebra algebra) {
        TreeMap<Integer, Float> result = new TreeMap<>();
        blades.forEach(cur -> {
            result.merge(cur.getIndex(), cur.getPrefactor(), (pL, pR) -> pL + pR);
        });
        
        // Remove 0 values
        LinkedList<Integer> nullIndices = new LinkedList<>();
        result.entrySet().forEach(entry -> {
            if (entry.getValue() == 0) 
                nullIndices.add(entry.getKey());
        });
        
        for (Integer nullIndex: nullIndices)
            result.remove(nullIndex);

        return result;
    }

    @Override
    public String toString() {
        return blades.toString();
    }

    public Vector<BladeRef> getBlades() {
        return blades;
    }

    public String print() {
        StringBuilder sb = new StringBuilder();
        for (BladeRef ref: blades) {

            float prefactor = ref.getPrefactor();
            if (prefactor == 0) continue;
            if (prefactor > 0) sb.append("+");
            if (prefactor == -1) sb.append("-");
            else if (prefactor != 1) sb.append(new java.math.BigDecimal(Float.toString(prefactor)).toPlainString());
            sb.append("E").append(ref.getIndex());
        }
        if (sb.length()==0) return "";
        if (sb.charAt(0) == '+')
            return sb.substring(1);
        else
            return sb.toString();
    }

}
