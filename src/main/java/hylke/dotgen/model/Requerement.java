package hylke.dotgen.model;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author hylke
 */
public class Requerement {

    public final String definition;
    public String description = "";
    public final Set<Image> inImage = Image.emptySet();
    public final Set<RequerementClass> inClass = new TreeSet<>();
    public int refCount = -1;

    public Requerement(String definition) {
        this.definition = definition;
        inImage.addAll(Image.imagesMatchingDef(definition));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Requerement other = (Requerement) obj;
        return Objects.equals(this.definition, other.definition);
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 59 * hash + Objects.hashCode(this.definition);
        return hash;
    }

}
