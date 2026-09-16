package de.gaalop.productComputer;

import de.gaalop.tba.BladeRef;
import de.gaalop.tba.Multivector;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * Represents a sum of weighted blades
 * @author Christian Steinmetz
 */
public class SumOfBlades extends LinkedList<SignedBlade> {

    /**
     * Converts this sum of blades to a multivector
     * @param map The map, which represents the zeroInfBlade->index map
     * @param bitCount The maximum number of bits
     * @return The resulting multivector
     */
    public Multivector toMultivector(HashMap<Blade, Integer> map, int bitCount) {
        Multivector result = new Multivector();
        for (SignedBlade sb: this) {
            Blade b = new Blade(bitCount, sb);

            Integer m = map.get(b);
            result.addBlade(new BladeRef(sb.coefficient, m));
        }
        return result;
    }

    

}
