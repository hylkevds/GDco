package hylke.dotgen;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final Map<String, RequerementClass> requirementClasses = new LinkedHashMap<>();

    public Generator(String source, String target) {
        this.source = source;
        this.target = target;
    }

    public void process() throws IOException, SAXException, ParserConfigurationException, XPathExpressionException {
        File sourceFile = new File(source);
        File targetFile = new File(target);

        parseSource(sourceFile);
        generateDot(targetFile);

        LOGGER.info("Found {} RequirementClasses.", requirementClasses.size());
        LOGGER.info("Found {} Requirements.", requirements.size());

    }

    private void generateDot(File targetFile) throws IOException {
        StringBuilder sb = new StringBuilder("digraph G {\n");
        sb.append("rankdir=LR;\n");
        sb.append("  node [shape=box];\n");
        sb.append("  {rank=same;\n");
        for (Requerement req : requirements.values()) {
            sb.append("    ")
                    .append('"').append(req.definition).append('"')
                    //.append(" -> ")
                    //.append('"').append(req.description).append('"')
                    .append("\n");
        }
        sb.append("  };\n\n");
        sb.append("  node [shape=none];\n");
        
        sb.append("  {\n");
        for (RequerementClass rq : requirementClasses.values()) {
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
        sb.append("  node [shape=ellipse];\n");
        for (RequerementClass rq : requirementClasses.values()) {
            for (Requerement req : rq.requirements) {
                sb.append("      ")
                        .append('"').append(rq.definition).append('"')
                        .append(" -> ")
                        .append('"').append(req.definition).append('"')
                        .append(";\n");
            }
            for (String dep : rq.dependencies) {
                sb.append("      ")
                        .append('"').append(rq.definition).append('"')
                        .append(" -> ")
                        .append('"').append(dep).append('"')
                        .append("[style=dashed];\n");
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
            LOGGER.info("  Rows: {}, Cols: {}, Type: '{}'", rowCount, colCount, type);
            if ("RequirementsClass".equalsIgnoreCase(type)) {
                parseRequirementsClassTable(rowList);
            } else if ("RequirementsSub-class".equalsIgnoreCase(type)) {
                parseRequirementsClassTable(rowList);
            } else if ((type.startsWith("/req") || type.startsWith("req")) && rowCount == 1) {
                parseRequirementTable(rowList);
            } else {
                LOGGER.warn("    Unknown table type: {}", type);
            }
        }
    }

    private void parseRequirementsClassTable(NodeList rowList) throws XPathExpressionException {
        int rowCount = rowList.getLength();
        RequerementClass reqClass = null;
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
                    reqClass.addDependency(value);
                    break;

                case "requirement":
                    value = cleanContent(valueCell.getTextContent(), true);
                    Requerement req = findOrCreateRequirement(value);
                    reqClass.addRequirement(req);
                    break;

                default:
                    value = cleanContent(valueCell.getTextContent(), false);
                    LOGGER.warn("Unknown row: {} - {}", name, value);
            }
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
            String desc = cleanContent(descCell.getTextContent(), false);
            Requerement req = findOrCreateRequirement(def);
            if (req.description != null) {
                LOGGER.warn("Requirement {} already has a description: {}", def, req.description);
            }
            req.description = desc;
        }
    }

    private Requerement findOrCreateRequirement(String definition) {
        return requirements.computeIfAbsent(definition, t -> new Requerement(definition));
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

        public Requerement(String definition) {
            this.definition = definition;
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
        final List<Requerement> requirements = new ArrayList<>();

        public RequerementClass(String definition) {
            this.definition = definition;
        }

        public void addDependency(String dependency) {
            dependencies.add(dependency);
        }

        public void addRequirement(Requerement req) {
            requirements.add(req);
        }
    }
}
