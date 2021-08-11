package hylke.dotgen.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 *
 * @author hylke
 */
public class RequerementClass implements Comparable<RequerementClass> {

    public final String definition;
    public String targetType;
    public String name = "";
    public final List<String> dependencies = new ArrayList<>();
    public final List<RequerementClass> imports = new ArrayList<>();
    public final List<Requerement> requirements = new ArrayList<>();
    public final List<Recommendation> recommendations = new ArrayList<>();
    public final Set<Image> inImage = Image.emptySet();
    public int refCount = -1;

    public RequerementClass(String definition) {
        this.definition = definition;
        inImage.addAll(Image.imagesMatchingDef(definition));
    }

    public void addDependency(String dependency) {
        dependencies.add(dependency);
    }

    public void addImport(RequerementClass dependency) {
        imports.add(dependency);
    }

    public void addRequirement(Requerement req) {
        requirements.add(req);
    }

    public void addRecommendation(Recommendation rec) {
        recommendations.add(rec);
    }

    @Override
    public int compareTo(RequerementClass o) {
        return definition.compareTo(o.definition);
    }

}
