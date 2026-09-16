package de.gaalop.algebra;
import de.gaalop.cfg.AlgebraDefinitionFile;
import java.io.InputStreamReader;
import org.junit.Test;
import static org.junit.Assert.*;
public class QraBladeCatalogTest {
    @Test public void preservesLegacyBladeOrder() throws Exception {
        for(int n=2;n<=4;n++) {
            AlgebraDefinitionFile reference=new AlgebraDefinitionFile(), compact=new AlgebraDefinitionFile();
            String path="/de/gaalop/algebra/algebra/qra"+n+"/definition.csv";
            try(InputStreamReader reader=new InputStreamReader(getClass().getResourceAsStream(path))) { reference.loadFromFile(reader); }
            try(InputStreamReader reader=new InputStreamReader(getClass().getResourceAsStream(path))) { compact.loadFromFile(reader); }
            AlStrategy.createBlades(reference); QraBladeCatalog.create(compact);
            assertEquals(reference.blades.length,compact.blades.length);
            for(int i=0;i<reference.blades.length;i++) assertEquals(reference.blades[i].toString(),compact.blades[i].toString());
        }
    }
}
