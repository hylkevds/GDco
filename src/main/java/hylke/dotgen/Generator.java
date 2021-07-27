package hylke.dotgen;

import hylke.dotgen.model.Data;
import hylke.dotgen.model.ConformanceClass;
import hylke.dotgen.model.Recommendation;
import hylke.dotgen.model.Requerement;
import hylke.dotgen.model.RequerementClass;
import hylke.dotgen.model.Image;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

/**
 *
 * @author hylke
 */
public class Generator {

    private static final Logger LOGGER = LoggerFactory.getLogger(Generator.class.getName());
    private static final String DATE_MODIFIED = "2021-07-08";
    private static final String DATE_CREATED = "2021-07-08";
    private static final String DATE_APPROVED = "2021-12-20";
    private static final String DATE_SUBMITTED = "2021-07-08";
    private static final String DATE_ACCEPTED = "2099-08-02";

    private final String source;
    private final String target;

    private Data documentData = new Data();

    public Generator(String source, String target) {
        this.source = source;
        this.target = target;
    }

    public void process() throws IOException, SAXException, ParserConfigurationException, XPathExpressionException {
        LOGGER.info("Working on: {}", source);
        LOGGER.info(" Output to: {}", target);
        File sourceFile = new File(source);

        documentData = new ParserOms()
                .addIgnoreReq(".*[{].*")
                .addIgnoreDep("ISO 19103.*")
                .addIgnoreDep("ISO 19107.*")
                .addIgnoreDep("ISO 19108.*")
                .addIgnoreDep(Pattern.quote("Unified Modeling Language (UML). Version 2.3. May 2010"))
                .parseSource(sourceFile)
                .getDocumentData();

        for (Image image : Image.values()) {
            File targetFileFull = new File(target + "_" + image.name().toLowerCase() + ".dot");
            generateDot(image, targetFileFull, false);
            File targetFileClass = new File(target + "_" + image.name().toLowerCase() + "_cls.dot");
            generateDot(image, targetFileClass, true);
        }

        for (RequerementClass reqClass : documentData.getRequirementClasses().values()) {
            File targetFileFull = new File(target + "_" + StringUtils.replace(reqClass.definition, "/", "_") + ".dot");
            generateDotFromClass(reqClass, targetFileFull);
        }

        generateReqHtml(new File(target + "_requirements.html"));
        generateTtl(new File(target + ".ttl"));
        LOGGER.info("Found {} RequirementClasses.", documentData.getRequirementClasses().size());
        LOGGER.info("Found {} Requirements.", documentData.getRequirements().size());

    }

    private void generateTtl(File targetFile) throws IOException {
        StringBuilder sb = new StringBuilder("@prefix adms: <http://www.w3.org/ns/adms#> .\n")
                .append("@prefix dcat: <http://www.w3.org/ns/dcat#> .\n")
                .append("@prefix dct: <http://purl.org/dc/terms/> .\n")
                .append("@prefix na: <http://www.opengis.net/def/metamodel/ogc-na/> .\n")
                .append("@prefix ogcdt: <http://www.opengis.net/def/doc-type/> .\n")
                .append("@prefix owl: <http://www.w3.org/2002/07/owl#> .\n")
                .append("@prefix reg: <http://purl.org/linked-data/registry#> .\n")
                .append("@prefix skos: <http://www.w3.org/2004/02/skos/core#> .\n")
                .append("@prefix spec: <http://www.opengis.net/def/ont/modspec/> .\n")
                .append("@prefix specrel: <http://www.opengis.net/def/ont/specrel/> .\n")
                .append("@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n")
                .append("@prefix dcterms: <http://purl.org/dc/terms/> .\n")
                .append("\n")
                .append("<http://www.opengis.net/spec/docs/20-082r2-anno> a owl:Ontology .\n")
                .append("\n")
                .append("\n")
                .append("\n")
                .append("<http://www.opengis.net/def/docs/20-082r2> a spec:Specification ;\n")
                .append("    dcterms:creator \"Kathi Schleidt\" ;\n")
                .append("    dcterms:dateAccepted \"").append(DATE_ACCEPTED).append("\"^^xsd:date ;\n")
                .append("    dcterms:dateSubmitted \"").append(DATE_SUBMITTED).append("\"^^xsd:date ;\n")
                .append("    dcterms:identifier \"http://www.opengis.net/doc/is/OMS/3.0\" ;\n")
                .append("    reg:status reg:statusValid ;\n")
                .append("    na:doctype ogcdt:ip ;\n")
                .append("    spec:authority \"Open Geospatial Consortium\" ;\n");
        boolean first = true;
        for (ConformanceClass confClass : documentData.getConformanceClasses().values()) {
            if (first) {
                first = false;
                sb.append("    spec:class ");
            } else {
                sb.append(",\n		");
            }
            sb.append("<http://www.opengis.net/spec/OMS/3.0").append(confClass.definition).append(">");
        }
        sb.append(" ;\n")
                .append("    spec:date \"").append(DATE_APPROVED).append("\"^^xsd:date ;\n")
                .append("    specrel:implementation <http://www.opengis.net/def/docs/20-082r2> ;\n")
                .append("    skos:notation \"20-082r2\"^^na:doc_no ;\n")
                .append("    skos:prefLabel \"OGC® Abstract Specification Topic 20 - Observations and measurements\" ;\n")
                .append("    adms:version \"3.0\" ;\n")
                .append("    dcat:landingPage <http://docs.opengeospatial.org/is/20-082r2/20-082r2.html> .");

        sb.append("\n\n");

        // Conformance Tests
        for (Requerement req : documentData.getRequirements().values()) {
            String confTestDef = req.definition.replace("/req/", "/conf/");
            sb.append("<http://www.opengis.net/spec/OMS/3.0").append(confTestDef).append("> a spec:ConformanceTest,\n")
                    .append("        skos:Concept ;\n")
                    .append("    spec:method \"Inspect the documentation of the application, schema or profile.\" ;\n")
                    .append("    spec:purpose \"Verify that all requirements from the requirements class have been fulfilled.\" ;\n")
                    .append("    spec:requirement <http://www.opengis.net/spec/OMS/3.0").append(req.definition).append("> ;\n")
                    .append("    spec:testType spec:Capabilities ;\n");
            for (RequerementClass reqClass : req.inClass) {
                sb.append("    skos:broader <http://www.opengis.net/spec/OMS/3.0")
                        .append(reqClass.definition.replace("/req/", "/conf/"))
                        .append("> ;\n");
            }
            //                    .append("    skos:broader <http://www.opengis.net/spec/OMS/3.0/conf/obs-cpt/Observation> ;\n")
            //                    .append("    skos:broader <http://www.opengis.net/spec/OMS/3.0/conf/obs-core/AbstractObservation> ;\n")
            sb.append("    skos:definition \"Verify that all requirements from the requirements class have been fulfilled.\" ;\n")
                    .append("    skos:inScheme <http://www.opengis.net/spec/OMS/3.0> ;\n")
                    .append("    skos:prefLabel \"Conformance Test http://www.opengis.net/spec/OMS/3.0").append(req.definition).append("\" .")
                    .append("\n\n");
        }
        sb.append("\n\n\n");

        // RequirementClasses
        for (RequerementClass reqClass : documentData.getRequirementClasses().values()) {
            sb.append("<http://www.opengis.net/spec/OMS/3.0").append(reqClass.definition).append("> a spec:RequirementClass,\n")
                    .append("        skos:Concept ;\n");
            for (Requerement req : reqClass.requirements) {
                sb.append("    spec:normativeStatement <http://www.opengis.net/spec/OMS/3.0").append(req.definition).append("> ;\n");
            }
            for (String imprt : reqClass.imports) {
                sb.append("    skos:broader <http://www.opengis.net/spec/OMS/3.0").append(imprt).append("> ;\n");
            }
            sb.append("    skos:definition \"").append(reqClass.definition).append("\" ;\n")
                    .append("    skos:inScheme <http://www.opengis.net/spec/OMS/3.0> ;\n")
                    .append("    skos:prefLabel \"Requirement Class ").append(reqClass.definition).append("\" .")
                    .append("\n\n");
        }
        sb.append("\n\n\n");

        // Requirements
        for (Requerement req : documentData.getRequirements().values()) {
            sb.append("<http://www.opengis.net/spec/OMS/3.0").append(req.definition).append("> a spec:Requirement,\n")
                    .append("        skos:Concept ;\n")
                    .append("    dcterms:description \"").append(req.description.replaceAll("[\"]", "\\\"")).append("\" ;\n");
            for (RequerementClass reqClass : req.inClass) {
                sb.append("    skos:broader <http://www.opengis.net/spec/OMS/3.0").append(reqClass.definition).append("> ;\n");
            }
            sb.append("    skos:definition \"").append(req.description).append("\" ;\n")
                    .append("    skos:inScheme <http://www.opengis.net/spec/OMS/3.0> ;\n")
                    .append("    skos:prefLabel \"Requirement: ").append(req.definition).append("\" .")
                    .append("\n\n");
        }
        sb.append("\n\n\n");

        // Conformance Classes
        for (ConformanceClass confClass : documentData.getConformanceClasses().values()) {
            sb.append("<http://www.opengis.net/spec/OMS/3.0").append(confClass.definition).append("> a spec:ConformanceClass,\n")
                    .append("        skos:Concept ;\n")
                    .append("    skos:definition \"").append(confClass.definition).append("\" ;\n")
                    .append("    skos:inScheme <http://www.opengis.net/spec/OMS/3.0> ;\n")
                    .append("    skos:prefLabel \"Conformance Class ").append(confClass.definition).append("\" ;\n")
                    .append("    skos:topConceptOf <http://www.opengis.net/spec/OMS/3.0> .")
                    .append("\n\n");
        }
        sb.append("\n\n\n");

        sb.append("<http://www.opengis.net/spec/OMS/3.0> a skos:ConceptScheme ;\n")
                .append("    dcterms:created \"").append(DATE_CREATED).append("\"^^xsd:date ;\n")
                .append("    dcterms:modified \"").append(DATE_MODIFIED).append("\"^^xsd:date ;\n")
                .append("    dcterms:source <http://www.opengis.net/def/docs/20-082r2> ;\n")
                .append("    skos:definition \"A convenience hierarchy for navigating the elements of a specification using the SKOS model\" ;\n")
                .append("    skos:hasTopConcept <http://www.opengis.net/spec/OMS/3.0/conf/obs-cpt>,\n")
                .append("		<http://www.opengis.net/spec/OMS/3.0/conf/obs-core>,\n")
                .append("		<http://www.opengis.net/spec/OMS/3.0/conf/obs-basic>,\n")
                .append("		<http://www.opengis.net/spec/OMS/3.0/conf/obs-cpt/Observation>,\n")
                .append("		<http://www.opengis.net/spec/OMS/3.0/conf/obs-core/AbstractObservationCharacteristics>,\n")
                .append("		<http://www.opengis.net/spec/OMS/3.0/conf/obs-core/AbstractObservation>,\n")
                .append("		<http://www.opengis.net/spec/OMS/3.0/conf/obs-basic/ObservationCharacteristics>,\n")
                .append("		<http://www.opengis.net/spec/OMS/3.0/conf/obs-basic/Observation>,\n")
                .append("		<http://www.opengis.net/spec/OMS/3.0/conf/obs-basic/ObservingCapability> ;\n")
                .append("    skos:prefLabel \"Specification elements for OGC 20-082r2 Observations, Measurements and Samples\" .");

        FileUtils.write(targetFile, sb, StandardCharsets.UTF_8);
    }

    private void generateReqHtml(File targetFile) throws IOException {
        StringBuilder sb = new StringBuilder("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd\"><html>")
                .append("<head>\n")
                .append("  <title>All Requirements</title>\n")
                .append("  <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n")
                .append("  <style>\n")
                .append("    .def {white-space:nowrap}\n")
                .append("    td {border-top:1px solid #999; vertical-align:top;padding:3px;}\n")
                .append("  </style>\n")
                .append("</head>\n")
                .append("<body>\n")
                .append("  <table>\n")
                .append("    <tr><th colspan=\"3\">Requirements</th></tr>\n")
                .append("    <tr><th>#</th><th>definition</th><th>description</th></tr>\n");
        for (Requerement req : documentData.getRequirements().values()) {
            String name = req.definition.substring(1 + req.definition.lastIndexOf('/'));
            sb.append("    ")
                    .append("<tr>")
                    .append("<td>").append(req.refCount).append("</td>")
                    .append("<td class='def'>").append(req.definition).append("</td>")
                    .append("<td>").append(req.description).append("</td>")
                    .append("</tr>\n");
        }
        sb.append("  </table>\n");

        sb.append("  <table>\n")
                .append("    <tr><th colspan=\"3\">Recommendations</th></tr>\n")
                .append("    <tr><th>#</th><th>definition</th><th>description</th></tr>\n");
        for (Recommendation rec : documentData.getRecommendations().values()) {
            sb.append("    ")
                    .append("<tr>")
                    .append("<td>").append(rec.refCount).append("</td>")
                    .append("<td class='def'>").append(rec.definition).append("</td>")
                    .append("<td>").append(rec.description).append("</td>")
                    .append("</tr>\n");
        }
        sb.append("  </table>\n");

        sb.append("  <table>\n")
                .append("    <tr><th colspan=\"4\">RequirementClasses</th></tr>\n")
                .append("    <tr><th>#</th><th>definition</th><th>name</th><th>type</th></tr>\n");
        for (RequerementClass confCls : documentData.getRequirementClasses().values()) {
            sb.append("    ")
                    .append("<tr>")
                    .append("<td>").append(confCls.refCount).append("</td>")
                    .append("<td class='def'>").append(confCls.definition).append("</td>")
                    .append("<td>").append(confCls.name).append("</td>")
                    .append("<td>").append(confCls.targetType).append("</td>")
                    .append("</tr>\n");
        }
        sb.append("  </table>\n");

        sb.append("  <table>\n")
                .append("    <tr><th colspan=\"5\">Conformance Classes</th></tr>\n")
                .append("    <tr><th>definition</th><th>requirement</th><th>purpose</th><th>method</th><th>type</th></tr>\n");
        for (ConformanceClass confCls : documentData.getConformanceClasses().values()) {
            sb.append("    ")
                    .append("<tr>")
                    .append("<td class='def'>").append(confCls.definition).append("</td>")
                    .append("<td>").append(confCls.requirement.definition).append("</td>")
                    .append("<td>").append(confCls.purpose).append("</td>")
                    .append("<td>").append(confCls.method).append("</td>")
                    .append("<td>").append(confCls.type).append("</td>")
                    .append("</tr>\n");
        }
        sb.append("  </table>\n");

        sb.append("</body>")
                .append("</html>");
        FileUtils.write(targetFile, sb, StandardCharsets.UTF_8);
    }

    private void generateDotFromClass(RequerementClass mainClass, File targetFile) throws IOException {
        Map<String, RequerementClass> classes = new TreeMap<>();
        Map<String, Requerement> reqs = new TreeMap<>();
        Map<String, Recommendation> reccs = new TreeMap<>();
        gatherFrom(mainClass, classes, reqs, reccs);
        generateDot(null, targetFile, false, classes, reqs, reccs);
    }

    private void gatherFrom(RequerementClass mainClass, Map<String, RequerementClass> classes, Map<String, Requerement> reqs, Map<String, Recommendation> reccs) {
        classes.put(mainClass.definition, mainClass);
        for (Requerement req : mainClass.requirements) {
            reqs.put(req.definition, req);
        }
        for (Recommendation rec : mainClass.recommendations) {
            reccs.put(rec.definition, rec);
        }
        for (String imprt : mainClass.imports) {
            RequerementClass imprtCls = documentData.findOrCreateRequirementClass(imprt);
            gatherFrom(imprtCls, classes, reqs, reccs);
        }
    }

    private void generateDot(Image image, File targetFile, boolean classesOnly) throws IOException {
        generateDot(image, targetFile, classesOnly, documentData.getRequirementClasses(), documentData.getRequirements(), documentData.getRecommendations());
    }

    private void generateDot(Image image, File targetFile, boolean classesOnly, Map<String, RequerementClass> classes, Map<String, Requerement> reqs, Map<String, Recommendation> reccs) throws IOException {
        StringBuilder sb = new StringBuilder("digraph G {\n")
                .append("  rankdir=LR;splines=polyline;\n");
        if (!classesOnly) {
            sb.append("  node [shape=box];\n")
                    .append("  {\n");
            for (Requerement req : reqs.values()) {
                if (image != null && !req.inImage.contains(image)) {
                    continue;
                }
                sb.append("    ")
                        .append('"').append(req.definition).append('"')
                        //.append(" -> ")
                        //.append('"').append(req.description).append('"')
                        .append("\n");
            }
            sb.append("  };\n\n");
        }

        if (!classesOnly) {
            sb.append("  node [shape=box;style=dotted];\n")
                    .append("  {\n");
            for (Recommendation rec : reccs.values()) {
                if (image != null && !rec.inImage.contains(image)) {
                    continue;
                }
                sb.append("    ")
                        .append('"').append(rec.definition).append('"')
                        //.append(" -> ")
                        //.append('"').append(req.description).append('"')
                        .append("\n");
            }
            sb.append("  };\n\n");
        }

        sb.append("  node [shape=plain];\n")
                .append("  {\n");
        for (RequerementClass rq : classes.values()) {
            if (image != null && !rq.inImage.contains(image)) {
                continue;
            }
            sb.append("    ")
                    .append('"').append(rq.definition).append('"')
                    .append("[label=<<TABLE>")
                    .append("<TR><TD>").append(rq.definition).append("</TD></TR>")
                    .append("<TR><TD>").append(rq.name).append("</TD></TR>")
                    .append("<TR><TD>").append(rq.targetType).append("</TD></TR>")
                    .append("</TABLE>>]")
                    .append("\n");
        }
        sb.append("  };\n\n")
                .append("  node [shape=ellipse;style=solid];\n");
        for (RequerementClass rq : classes.values()) {
            if (image != null && !rq.inImage.contains(image)) {
                continue;
            }
            if (!classesOnly) {
                for (Requerement req : rq.requirements) {
                    sb.append("      ")
                            .append('"').append(rq.definition).append('"')
                            .append(" -> ")
                            .append('"').append(req.definition).append('"')
                            .append(";\n");
                }
            }
            if (!classesOnly) {
                for (Recommendation rec : rq.recommendations) {
                    sb.append("      ")
                            .append('"').append(rq.definition).append('"')
                            .append(" -> ")
                            .append('"').append(rec.definition).append('"')
                            .append("[style=dotted];\n");
                }
            }
            for (String dep : rq.imports) {
                sb.append("      ")
                        .append('"').append(rq.definition).append('"')
                        .append(" -> ")
                        .append('"').append(dep).append('"')
                        .append("[style=dashed];\n");
            }
            if (!classesOnly) {
                for (String dep : rq.dependencies) {
                    sb.append("      ")
                            .append('"').append(rq.definition).append('"')
                            .append(" -> ")
                            .append('"').append(dep).append('"')
                            .append("[style=dotted];\n");
                }
            }
        }
        sb.append("}\n");
        FileUtils.write(targetFile, sb, StandardCharsets.UTF_8);
    }

}
