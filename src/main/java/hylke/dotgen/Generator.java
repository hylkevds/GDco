package hylke.dotgen;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;
import org.htmlcleaner.CleanerProperties;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.PrettyXmlSerializer;
import org.htmlcleaner.TagNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author hylke
 */
public class Generator {

    private static final Logger LOGGER = LoggerFactory.getLogger(Generator.class.getName());
    private final String source;
    private final String target;

    private XPathExpression exprTablesList;
    private XPathExpression exprRowList;
    private XPathExpression exprCellList;

    private final Map<String, Requerement> requirements = new TreeMap<>();
    private final Map<String, Recommendation> recommendations = new TreeMap<>();
    private final Map<String, RequerementClass> requirementClasses = new TreeMap<>();
    private final Map<String, ConformanceClass> conformanceClasses = new TreeMap<>();

    private final Set<Pattern> ignoreReqs = new HashSet<>();
    private final Set<Pattern> ignoreDeps = new HashSet<>();
    private final Set<String> ignoredDeps = new HashSet<>();

    private enum Image {
        OBS("^/re[qc]/obs.*"),
        SAM("^/re[qc]/sam.*"),
        NONE("^$");
        public final Pattern definitionPattern;

        private Image(String definitionRegex) {
            this.definitionPattern = Pattern.compile(definitionRegex);
        }

        public static Set<Image> imagesMatchingDef(String definition) {
            Set<Image> result = emptySet();
            for (Image image : Image.values()) {
                if (image.definitionPattern.matcher(definition).matches()) {
                    result.add(image);
                }
            }
            if (result.isEmpty()) {
                result.add(NONE);
            }
            return result;
        }

        public static Set<Image> emptySet() {
            return EnumSet.noneOf(Image.class);
        }
    }

    public Generator(String source, String target) {
        this.source = source;
        this.target = target;
        ignoreReqs.add(Pattern.compile(".*[{].*"));
        ignoreDeps.add(Pattern.compile("ISO 19103.*"));
        ignoreDeps.add(Pattern.compile("ISO 19107.*"));
        ignoreDeps.add(Pattern.compile("ISO 19108.*"));
        ignoreDeps.add(Pattern.compile(Pattern.quote("Unified Modeling Language (UML). Version 2.3. May 2010")));
    }

    public void process() throws IOException, SAXException, ParserConfigurationException, XPathExpressionException {
        LOGGER.info("Working on: {}", source);
        LOGGER.info(" Output to: {}", target);
        File sourceFile = new File(source);

        parseSource(sourceFile);

        for (Image image : Image.values()) {
            File targetFileFull = new File(target + "_" + image.name().toLowerCase() + ".dot");
            generateDot(image, targetFileFull, false);
            File targetFileClass = new File(target + "_" + image.name().toLowerCase() + "_cls.dot");
            generateDot(image, targetFileClass, true);
        }

        for (RequerementClass reqClass : requirementClasses.values()) {
            File targetFileFull = new File(target + "_" + StringUtils.replace(reqClass.definition, "/", "_") + ".dot");
            generateDotFromClass(reqClass, targetFileFull);
        }

        generateReqHtml(new File(target + "_requirements.html"));
        generateTtl(new File(target + ".ttl"));
        LOGGER.info("Found {} RequirementClasses.", requirementClasses.size());
        LOGGER.info("Found {} Requirements.", requirements.size());

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
                .append("    dcterms:dateAccepted \"2099-08-02\"^^xsd:date ;\n")
                .append("    dcterms:dateSubmitted \"2021-07-08\"^^xsd:date ;\n")
                .append("    dcterms:identifier \"http://www.opengis.net/doc/is/OMS/3.0\" ;\n")
                .append("    reg:status reg:statusValid ;\n")
                .append("    na:doctype ogcdt:ip ;\n")
                .append("    spec:authority \"Open Geospatial Consortium\" ;\n");
        boolean first = true;
        for (ConformanceClass confClass : conformanceClasses.values()) {
            if (first) {
                first = false;
                sb.append("    spec:class ");
            } else {
                sb.append(",\n		");
            }
            sb.append("<http://www.opengis.net/spec/OMS/3.0").append(confClass.definition).append(">");
        }
        sb.append(" ;\n")
                .append("    spec:date \"2021-12-20\"^^xsd:date ;\n")
                .append("    specrel:implementation <http://www.opengis.net/def/docs/20-082r2> ;\n")
                .append("    skos:notation \"20-082r2\"^^na:doc_no ;\n")
                .append("    skos:prefLabel \"OGC® Abstract Specification Topic 20 - Observations and measurements\" ;\n")
                .append("    adms:version \"3.0\" ;\n")
                .append("    dcat:landingPage <http://docs.opengeospatial.org/is/20-082r2/20-082r2.html> .");

        sb.append("\n\n");

        // Conformance Tests
        for (Requerement req : requirements.values()) {
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
        for (RequerementClass reqClass : requirementClasses.values()) {
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
        for (Requerement req : requirements.values()) {
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
        for (ConformanceClass confClass : conformanceClasses.values()) {
            RequerementClass reqClass = requirementClasses.get(confClass.definition.replace("/conf/", "/req/"));
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
                .append("    dcterms:created \"2021-07-08\"^^xsd:date ;\n")
                .append("    dcterms:modified \"2021-07-08\"^^xsd:date ;\n")
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
        for (Requerement req : requirements.values()) {
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
        for (Recommendation rec : recommendations.values()) {
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
        for (RequerementClass confCls : requirementClasses.values()) {
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
        for (ConformanceClass confCls : conformanceClasses.values()) {
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
        Map<String, RequerementClass> classes = new LinkedHashMap<>();
        Map<String, Requerement> reqs = new LinkedHashMap<>();
        Map<String, Recommendation> reccs = new LinkedHashMap<>();
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
            RequerementClass imprtCls = findOrCreateRequirementClass(imprt);
            gatherFrom(imprtCls, classes, reqs, reccs);
        }
    }

    private void generateDot(Image image, File targetFile, boolean classesOnly) throws IOException {
        generateDot(image, targetFile, classesOnly, requirementClasses, requirements, recommendations);
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

    private void parseSource(File sourceFile) throws IOException, ParserConfigurationException, XPathExpressionException, DOMException, SAXException {
        HtmlCleaner cleaner = new HtmlCleaner();
        CleanerProperties props = cleaner.getProperties();
        LOGGER.info("Cleaning input...");
        TagNode clean = cleaner.clean(sourceFile);
        LOGGER.info("Writing clean input...");
        String cleanString = new PrettyXmlSerializer(props).getAsString(clean, StandardCharsets.UTF_8.toString());

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        LOGGER.info("Parsing input...");
        Document doc = builder.parse(IOUtils.toInputStream(cleanString, StandardCharsets.UTF_8));

        XPathFactory xpathfactory = XPathFactory.newInstance();
        XPath xpath = xpathfactory.newXPath();
        exprTablesList = xpath.compile("//table");
        exprRowList = xpath.compile("//tr");
        exprCellList = xpath.compile("//td");

        NodeList stationList = (NodeList) exprTablesList.evaluate(doc, XPathConstants.NODESET);
        int total = stationList.getLength();
        LOGGER.info("Found {} tables.", total);
        for (int i = 0; i < total; i++) {
            Node tableNode = stationList.item(i).cloneNode(true);
            NodeList rowList = (NodeList) exprRowList.evaluate(tableNode, XPathConstants.NODESET);
            int rowCount = rowList.getLength();

            Node firstRow = rowList.item(0).cloneNode(true);
            NodeList cellList = (NodeList) exprCellList.evaluate(firstRow, XPathConstants.NODESET);
            int colCount = cellList.getLength();

            if (colCount != 2) {
                continue;
            }
            Node firstCell = cellList.item(0).cloneNode(true);
            String type = cleanContent(firstCell.getTextContent(), true);
            LOGGER.debug("  Rows: {}, Cols: {}, Type: '{}'", rowCount, colCount, type);
            if ("RequirementsClass".equalsIgnoreCase(type)) {
                parseRequirementsClassTable(rowList);
            } else if ("RequirementsSub-class".equalsIgnoreCase(type)) {
                parseRequirementsClassTable(rowList);
            } else if ("ConformanceClass".equalsIgnoreCase(type)) {
                parseConformanceClassTable(rowList);
            } else if ((type.startsWith("Requirement/req") || type.startsWith("/req") || type.startsWith("req")) && rowCount == 1) {
                parseRequirementTable(rowList);
            } else if ((type.startsWith("Recommendation/rec") || type.startsWith("/rec")) && rowCount == 1) {
                parseRecommendationTable(rowList);
            } else {
                LOGGER.warn("    Unknown table type: {}, {} rows", type, rowCount);
            }
        }

        LOGGER.info("Ignored Dependencies:");
        for (String ignoredDep : ignoredDeps) {
            LOGGER.info("  '{}'", ignoredDep);
        }
    }

    private void parseRequirementsClassTable(NodeList rowList) throws XPathExpressionException {
        int rowCount = rowList.getLength();
        RequerementClass reqClass = null;
        Set<Image> mainImages = Image.emptySet();
        for (int i = 0; i < rowCount; i++) {
            Node row = rowList.item(i).cloneNode(true);
            NodeList cellList = (NodeList) exprCellList.evaluate(row, XPathConstants.NODESET);
            int cellCount = cellList.getLength();
            if (cellCount != 2) {
                LOGGER.error("    Requirement row found with {} cells, expected 2", cellCount);
                continue;
            }
            Node nameCell = cellList.item(0).cloneNode(true);
            Node valueCell = cellList.item(1).cloneNode(true);
            String name = cleanContent(nameCell.getTextContent(), true);
            String value;
            switch (name.toLowerCase()) {
                case "requirementsclass":
                case "requirementssub-class":
                    value = cleanContent(valueCell.getTextContent(), true);
                    if (matchesAnyOf(value, ignoreReqs)) {
                        return;
                    }
                    reqClass = findOrCreateRequirementClass(value);
                    mainImages.addAll(Image.imagesMatchingDef(value));
                    break;

                case "targettype":
                    value = cleanContent(valueCell.getTextContent(), false);
                    reqClass.targetType = value;
                    break;

                case "name":
                    value = cleanContent(valueCell.getTextContent(), false);
                    reqClass.name = value;
                    break;

                case "dependency":
                    value = cleanContent(valueCell.getTextContent(), false);
                    if (value.startsWith("/")) {
                        value = cleanContent(valueCell.getTextContent(), true);
                    }
                    if (matchesAnyOf(value, ignoreDeps)) {
                        ignoredDeps.add(value);
                    } else {
                        reqClass.addDependency(value);
                        checkImageForRelation(value, mainImages);
                    }
                    break;

                case "imports":
                    value = cleanContent(valueCell.getTextContent(), true);
                    if (matchesAnyOf(value, ignoreReqs)) {
                        continue;
                    }
                    findOrCreateRequirementClass(value);
                    reqClass.addImport(value);
                    checkImageForRelation(value, mainImages);
                    break;

                case "requirement":
                    value = cleanContent(valueCell.getTextContent(), true);
                    if (matchesAnyOf(value, ignoreReqs)) {
                        continue;
                    }
                    Requerement req = findOrCreateRequirement(value);
                    reqClass.addRequirement(req);
                    req.inClass.add(reqClass);
                    checkImageForRelation(value, mainImages);
                    break;

                case "recommendation":
                    value = cleanContent(valueCell.getTextContent(), true);
                    if (matchesAnyOf(value, ignoreReqs)) {
                        continue;
                    }
                    Recommendation rec = findOrCreateRecommendation(value);
                    reqClass.addRecommendation(rec);
                    checkImageForRelation(value, mainImages);
                    break;

                default:
                    value = cleanContent(valueCell.getTextContent(), false);
                    LOGGER.warn("Unknown row: {} - {}", name, value);
            }
        }
    }

    private void parseConformanceClassTable(NodeList rowList) throws XPathExpressionException {
        int rowCount = rowList.getLength();
        ConformanceClass confClass = null;
        for (int i = 0; i < rowCount; i++) {
            Node row = rowList.item(i).cloneNode(true);
            NodeList cellList = (NodeList) exprCellList.evaluate(row, XPathConstants.NODESET);
            int cellCount = cellList.getLength();
            if (cellCount != 2) {
                LOGGER.error("    Requirement row found with {} cells, expected 2", cellCount);
                continue;
            }
            Node nameCell = cellList.item(0).cloneNode(true);
            Node valueCell = cellList.item(1).cloneNode(true);
            String name = cleanContent(nameCell.getTextContent(), true);
            String value;
            switch (name.toLowerCase()) {
                case "conformanceclass":
                    value = cleanContent(valueCell.getTextContent(), true);
                    if (matchesAnyOf(value, ignoreReqs)) {
                        return;
                    }
                    confClass = findOrCreateConformanceClass(value);
                    break;

                case "testpurpose":
                    value = cleanContent(valueCell.getTextContent(), false);
                    confClass.purpose = value;
                    break;

                case "testmethod":
                    value = cleanContent(valueCell.getTextContent(), false);
                    confClass.method = value;
                    break;

                case "testtype":
                    value = cleanContent(valueCell.getTextContent(), false);
                    confClass.type = value;
                    break;

                case "requirements":
                    value = cleanContent(valueCell.getTextContent(), true);
                    if (matchesAnyOf(value, ignoreReqs)) {
                        continue;
                    }
                    RequerementClass req = findOrCreateRequirementClass(value);
                    confClass.addRequirement(req);
                    break;

                default:
                    value = cleanContent(valueCell.getTextContent(), false);
                    LOGGER.warn("Unknown row: {} - {}", name, value);
            }
        }
    }

    private void checkImageForRelation(String value, Set<Image> mainImages) {
        RequerementClass reqClass = requirementClasses.get(value);
        if (reqClass != null) {
            reqClass.inImage.addAll(mainImages);
        }
        Requerement req = requirements.get(value);
        if (req != null) {
            req.inImage.addAll(mainImages);
        }
    }

    private void parseRequirementTable(NodeList rowList) throws XPathExpressionException {
        int rowCount = rowList.getLength();
        if (rowCount > 1) {
            LOGGER.warn("Requirements Table with multiple rows found");
        }
        for (int i = 0; i < rowCount; i++) {
            Node row = rowList.item(i).cloneNode(true);
            NodeList cellList = (NodeList) exprCellList.evaluate(row, XPathConstants.NODESET);
            int cellCount = cellList.getLength();
            if (cellCount != 2) {
                LOGGER.error("Requirement row found with {} cells, expected 2", cellCount);
                continue;
            }
            Node defCell = cellList.item(0).cloneNode(true);
            Node descCell = cellList.item(1).cloneNode(true);
            String def = cleanContent(defCell.getTextContent(), true);
            if (def.startsWith("Requirement")) {
                def = def.substring("Requirement".length());
            }
            if (matchesAnyOf(def, ignoreReqs)) {
                continue;
            }
            String desc = cleanContent(descCell.getTextContent(), false);
            Requerement req = findOrCreateRequirement(def);
            if (!req.description.isEmpty()) {
                LOGGER.warn("Requirement {} already has a description: {}", def, req.description);
            }
            req.description = desc;
        }
    }

    private void parseRecommendationTable(NodeList rowList) throws XPathExpressionException {
        int rowCount = rowList.getLength();
        if (rowCount > 1) {
            LOGGER.warn("Recommendation Table with multiple rows found");
        }
        for (int i = 0; i < rowCount; i++) {
            Node row = rowList.item(i).cloneNode(true);
            NodeList cellList = (NodeList) exprCellList.evaluate(row, XPathConstants.NODESET);
            int cellCount = cellList.getLength();
            if (cellCount != 2) {
                LOGGER.error("Recommendation row found with {} cells, expected 2", cellCount);
                continue;
            }
            Node defCell = cellList.item(0).cloneNode(true);
            Node descCell = cellList.item(1).cloneNode(true);
            String def = cleanContent(defCell.getTextContent(), true);
            if (def.startsWith("Recommendation")) {
                def = def.substring("Recommendation".length());
            }
            if (matchesAnyOf(def, ignoreReqs)) {
                continue;
            }
            String desc = cleanContent(descCell.getTextContent(), false);
            Recommendation rec = findOrCreateRecommendation(def);
            if (!rec.description.isEmpty()) {
                LOGGER.warn("Recommendation {} already has a description: {}", def, rec.description);
            }
            rec.description = desc;
        }
    }

    private Requerement findOrCreateRequirement(String definition) {
        final Requerement item = requirements.computeIfAbsent(definition, t -> new Requerement(definition));
        item.refCount++;
        return item;
    }

    private Recommendation findOrCreateRecommendation(String definition) {
        final Recommendation item = recommendations.computeIfAbsent(definition, t -> new Recommendation(definition));
        item.refCount++;
        return item;
    }

    private RequerementClass findOrCreateRequirementClass(String definition) {
        final RequerementClass item = requirementClasses.computeIfAbsent(definition, t -> new RequerementClass(definition));
        item.refCount++;
        return item;
    }

    private ConformanceClass findOrCreateConformanceClass(String definition) {
        return conformanceClasses.computeIfAbsent(definition, t -> new ConformanceClass(definition));
    }

    private String cleanContent(String data, boolean noSpaces) {
        String clean = StringUtils.replaceChars(data.trim(), "\n\t\r", "   ");
        if (noSpaces) {
            clean = RegExUtils.removeAll(clean, PATTERN_SPACE);
        } else {
            clean = RegExUtils.replaceAll(clean, PATTERN_SPACES, " ");
        }
        return clean;
    }

    private static final Pattern PATTERN_SPACES = Pattern.compile("[ ]{2,}");
    private static final Pattern PATTERN_SPACE = Pattern.compile("([ ]+)|(\\[[^ ]+\\])");

    private static class Requerement {

        final String definition;
        String description = "";
        final Set<Image> inImage = Image.emptySet();
        final Set<RequerementClass> inClass = new HashSet<>();
        int refCount = -1;

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

    private static class Recommendation {

        final String definition;
        String description = "";
        final Set<Image> inImage = Image.emptySet();
        int refCount = -1;

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

    }

    private static class RequerementClass {

        final String definition;
        String targetType;
        String name = "'name'";
        final List<String> dependencies = new ArrayList<>();
        final List<String> imports = new ArrayList<>();
        final List<Requerement> requirements = new ArrayList<>();
        final List<Recommendation> recommendations = new ArrayList<>();
        final Set<Image> inImage = Image.emptySet();
        int refCount = -1;

        public RequerementClass(String definition) {
            this.definition = definition;
            inImage.addAll(Image.imagesMatchingDef(definition));
        }

        public void addDependency(String dependency) {
            dependencies.add(dependency);
        }

        public void addImport(String dependency) {
            imports.add(dependency);
        }

        public void addRequirement(Requerement req) {
            requirements.add(req);
        }

        public void addRecommendation(Recommendation rec) {
            recommendations.add(rec);
        }
    }

    private static class ConformanceClass {

        final String definition;
        String purpose;
        String method;
        String type;
        RequerementClass requirement;

        public ConformanceClass(String definition) {
            this.definition = definition;
        }

        public void addRequirement(RequerementClass req) {
            if (requirement != null) {
                LOGGER.error("Conformance Class {} already has a requirement {}, overwriting with {}", definition, requirement.definition, req.definition);
            }
            requirement = req;
        }

    }

    private static boolean matchesAnyOf(String value, Set<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(value).matches()) {
                return true;
            }
        }
        return false;
    }
}
