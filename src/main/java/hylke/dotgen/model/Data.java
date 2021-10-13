package hylke.dotgen.model;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author hylke
 */
public class Data {

    private static final Logger LOGGER = LoggerFactory.getLogger(Data.class.getName());

    private final String nameSpace;
    private final Map<String, Requerement> requirements = new TreeMap<>();
    private final Map<String, Recommendation> recommendations = new TreeMap<>();
    private final Map<String, RequerementClass> requirementClasses = new TreeMap<>();
    private final Map<String, ConformanceClass> conformanceClasses = new TreeMap<>();

    public Data(String nameSpace) {
        this.nameSpace = nameSpace;
    }

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
        if (definition.startsWith(nameSpace)) {
            definition = definition.substring(nameSpace.length());
        }
        final Requerement item = requirements.computeIfAbsent(definition, t -> new Requerement(t));
        item.refCount++;
        LOGGER.trace("    Req {}: {}", item.refCount, definition);
        return item;
    }

    public Recommendation findOrCreateRecommendation(String definition) {
        if (definition.startsWith(nameSpace)) {
            definition = definition.substring(nameSpace.length());
        }
        final Recommendation item = recommendations.computeIfAbsent(definition, t -> new Recommendation(t));
        item.refCount++;
        LOGGER.trace("    Rec {}: {}", item.refCount, definition);
        return item;
    }

    public RequerementClass findOrCreateRequirementClass(String definition) {
        if (definition.startsWith(nameSpace)) {
            definition = definition.substring(nameSpace.length());
        }
        final RequerementClass item = requirementClasses.computeIfAbsent(definition, t -> new RequerementClass(t));
        item.refCount++;
        LOGGER.trace("    Cls {}: {}", item.refCount, definition);
        return item;
    }

    public ConformanceClass findOrCreateConformanceClass(String definition) {
        if (definition.startsWith(nameSpace)) {
            definition = definition.substring(nameSpace.length());
        }
        return conformanceClasses.computeIfAbsent(definition, t -> new ConformanceClass(t));
    }

    public void checkImageForRelation(String value, Set<Image> mainImages) {
        if (value.startsWith(nameSpace)) {
            value = value.substring(nameSpace.length());
        }
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
