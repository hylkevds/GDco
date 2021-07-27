package hylke.dotgen.model;

import java.util.Objects;
import java.util.Set;

/**
 *
 * @author hylke
 */
public class Recommendation implements Comparable<Recommendation> {

    public final String definition;
    public String description = "";
    public final Set<Image> inImage = Image.emptySet();
    public int refCount = -1;

    public Recommendation(String definition) {
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

    @Override
    public int compareTo(Recommendation o) {
        return definition.compareTo(o.definition);
    }

}
