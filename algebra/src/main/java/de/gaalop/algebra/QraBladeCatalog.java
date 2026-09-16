package de.gaalop.algebra;

import de.gaalop.cfg.AlgebraDefinitionFile;
import de.gaalop.dfg.*;
import java.util.Arrays;

/** Read-only QRA blade catalogue with shared suffix expressions, O(2^d) nodes. */
final class QraBladeCatalog {
    private QraBladeCatalog() { }
    static void create(AlgebraDefinitionFile algebra) {
        if (!Arrays.equals(algebra.base, algebra.base2)) throw new IllegalArgumentException("QRA requires identical bases");
        int dimension=algebra.base.length-1;
        Expression[] byMask=new Expression[1<<dimension];
        byMask[0]=new FloatConstant(1);
        for(int mask=1;mask<byMask.length;mask++) {
            int bit=Integer.numberOfTrailingZeros(mask);
            String name=algebra.base[bit+1];
            int rest=mask & (mask-1);
            Expression vector=new BaseVector(name.substring(0,1),name.substring(1));
            byMask[mask]=rest==0 ? vector : new OuterProduct(vector,byMask[rest]);
        }
        algebra.blades=new Expression[byMask.length];
        algebra.blades[0]=byMask[0];
        int[] index={1};
        for(int grade=1;grade<=dimension;grade++) assign(0,grade,0,dimension,index,byMask,algebra.blades);
        algebra.blades2=algebra.blades.clone();
    }
    private static void assign(int first,int remaining,int mask,int dimension,int[] index,Expression[] byMask,Expression[] output) {
        if(remaining==0) { output[index[0]++]=byMask[mask]; return; }
        for(int bit=first;bit<=dimension-remaining;bit++) assign(bit+1,remaining-1,mask|(1<<bit),dimension,index,byMask,output);
    }
}
