package hylke.dotgen.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author hylke
 */
public class ConformanceClass implements Comparable<ConformanceClass> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConformanceClass.class.getName());

    public final String definition;
    public String purpose;
    public String method;
    public String type;
    public RequerementClass requirement;

    public ConformanceClass(String definition) {
        this.definition = definition;
    }

    public void addRequirement(RequerementClass req) {
        if (requirement != null) {
            LOGGER.error("Conformance Class {} already has a requirement {}, overwriting with {}", definition, requirement.definition, req.definition);
        }
        requirement = req;
    }

    @Override
    public int compareTo(ConformanceClass o) {
        return definition.compareTo(o.definition);
    }

}
