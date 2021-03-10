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
        ignoreDeps.add(Pattern.compile("ISO 19103.*"));
        ignoreDeps.add(Pattern.compile("ISO 19107.*"));
        ignoreDeps.add(Pattern.compile("ISO 19108.*"));
        ignoreDeps.add(Pattern.compile(Pattern.quote("Unified Modeling Language (UML). Version 2.3. May 2010")));
    }

    public void process() throws IOException, SAXException, ParserConfigurationException, XPathExpressionException {
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

        File targetFile = new File(target + "_requirements.html");
        generateReqHtml(targetFile);
        LOGGER.info("Found {} RequirementClasses.", requirementClasses.size());
        LOGGER.info("Found {} Requirements.", requirements.size());

    }

    private void generateReqHtml(File targetFile) throws IOException {
        StringBuilder sb = new StringBuilder("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd\"><html>");
        sb.append("<head>\n");
        sb.append("  <title>All Requirements</title>\n");
        sb.append("  <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n");
        sb.append("  <style>\n");
        sb.append("    .def {white-space:nowrap}\n");
        sb.append("    td {border-top:1px solid #999; vertical-align:top;padding:3px;}\n");
        sb.append("  </style>\n");
        sb.append("</head>\n");
        sb.append("<body>\n");
        sb.append("  <table>\n");
        sb.append("    <tr><th colspan=\"2\">Requirements</th></tr>\n");
        sb.append("    <tr><th>definition</th><th>name</th><th>description</th></tr>\n");
        for (Requerement req : requirements.values()) {
            String name = req.definition.substring(1 + req.definition.lastIndexOf('/'));
            sb.append("    ")
                    .append("<tr>")
                    .append("<td class='def'>").append(req.definition).append("</td>")
                    .append("<td class='def'>").append(name).append("</td>")
                    .append("<td>").append(req.description).append("</td>")
                    .append("</tr>\n");
        }
        sb.append("  </table>\n");
        sb.append("  <table>\n");
        sb.append("    <tr><th colspan=\"2\">Recommendations</th></tr>\n");
        sb.append("    <tr><th>definition</th><th>description</th></tr>\n");
        for (Recommendation rec : recommendations.values()) {
            sb.append("    ")
                    .append("<tr>")
                    .append("<td class='def'>").append(rec.definition).append("</td>")
                    .append("<td>").append(rec.description).append("</td>")
                    .append("</tr>\n");
        }
        sb.append("  </table>\n");
        sb.append("</body>");
        sb.append("</html>");
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
        StringBuilder sb = new StringBuilder("digraph G {\n");
        sb.append("  rankdir=LR;splines=polyline;\n");
        if (!classesOnly) {
            sb.append("  node [shape=box];\n");
            sb.append("  {\n");
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
            sb.append("  node [shape=box;style=dotted];\n");
            sb.append("  {\n");
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

        sb.append("  node [shape=plain];\n");
        sb.append("  {\n");
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
        sb.append("  };\n\n");
        sb.append("  node [shape=ellipse;style=solid];\n");
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
        TagNode clean = cleaner.clean(sourceFile);
        String cleanString = new PrettyXmlSerializer(props).getAsString(clean, StandardCharsets.UTF_8.toString());

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
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
            } else if ((type.startsWith("Requirement/req") || type.startsWith("/req") || type.startsWith("req")) && rowCount == 1) {
                parseRequirementTable(rowList);
            } else if ((type.startsWith("Recommendation/rec") || type.startsWith("/rec")) && rowCount == 1) {
                parseRecommendationTable(rowList);
            } else {
                LOGGER.warn("    Unknown table type: {}", type);
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
                    boolean ignore = false;
                    for (Pattern ignoreDep : ignoreDeps) {
                        if (ignoreDep.matcher(value).matches()) {
                            ignore = true;
                            break;
                        }
                    }
                    if (ignore) {
                        ignoredDeps.add(value);
                    } else {
                        reqClass.addDependency(value);
                        checkImageForRelation(value, mainImages);
                    }
                    break;

                case "imports":
                    value = cleanContent(valueCell.getTextContent(), true);
                    reqClass.addImport(value);
                    checkImageForRelation(value, mainImages);
                    break;

                case "requirement":
                    value = cleanContent(valueCell.getTextContent(), true);
                    Requerement req = findOrCreateRequirement(value);
                    reqClass.addRequirement(req);
                    checkImageForRelation(value, mainImages);
                    break;

                case "recommendation":
                    value = cleanContent(valueCell.getTextContent(), true);
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
            String desc = cleanContent(descCell.getTextContent(), false);
            Requerement req = findOrCreateRequirement(def);
            if (req.description != null) {
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
            String desc = cleanContent(descCell.getTextContent(), false);
            Recommendation rec = findOrCreateRecommendation(def);
            if (rec.description != null) {
                LOGGER.warn("Recommendation {} already has a description: {}", def, rec.description);
            }
            rec.description = desc;
        }
    }

    private Requerement findOrCreateRequirement(String definition) {
        return requirements.computeIfAbsent(definition, t -> new Requerement(definition));
    }

    private Recommendation findOrCreateRecommendation(String definition) {
        return recommendations.computeIfAbsent(definition, t -> new Recommendation(definition));
    }

    private RequerementClass findOrCreateRequirementClass(String definition) {
        return requirementClasses.computeIfAbsent(definition, t -> new RequerementClass(definition));
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
        String description;
        final Set<Image> inImage = Image.emptySet();

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
        String description;
        final Set<Image> inImage = Image.emptySet();

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
}
