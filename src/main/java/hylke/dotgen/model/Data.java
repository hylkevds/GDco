package hylke.dotgen.model;

import hylke.dotgen.model.ConformanceClass;
import hylke.dotgen.model.Image;
import hylke.dotgen.model.Recommendation;
import hylke.dotgen.model.RequerementClass;
import hylke.dotgen.model.Requerement;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 *
 * @author hylke
 */
public class Data {

    private final Map<String, Requerement> requirements = new TreeMap<>();
    private final Map<String, Recommendation> recommendations = new TreeMap<>();
    private final Map<String, RequerementClass> requirementClasses = new TreeMap<>();
    private final Map<String, ConformanceClass> conformanceClasses = new TreeMap<>();

    public Map<String, Requerement> getRequirements() {
        return requirements;
    }

    public Map<String, Recommendation> getRecommendations() {
        return recommendations;
    }

    public Map<String, RequerementClass> getRequirementClasses() {
        return requirementClasses;
    }

    public Map<String, ConformanceClass> getConformanceClasses() {
        return conformanceClasses;
    }

    public Requerement findOrCreateRequirement(String definition) {
        final Requerement item = requirements.computeIfAbsent(definition, t -> new Requerement(definition));
        item.refCount++;
        return item;
    }

    public Recommendation findOrCreateRecommendation(String definition) {
        final Recommendation item = recommendations.computeIfAbsent(definition, t -> new Recommendation(definition));
        item.refCount++;
        return item;
    }

    public RequerementClass findOrCreateRequirementClass(String definition) {
        final RequerementClass item = requirementClasses.computeIfAbsent(definition, t -> new RequerementClass(definition));
        item.refCount++;
        return item;
    }

    public ConformanceClass findOrCreateConformanceClass(String definition) {
        return conformanceClasses.computeIfAbsent(definition, t -> new ConformanceClass(definition));
    }

    public void checkImageForRelation(String value, Set<Image> mainImages) {
        RequerementClass reqClass = requirementClasses.get(value);
        if (reqClass != null) {
            reqClass.inImage.addAll(mainImages);
        }
        Requerement req = requirements.get(value);
        if (req != null) {
            req.inImage.addAll(mainImages);
        }
    }

}
