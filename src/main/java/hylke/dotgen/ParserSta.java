package hylke.dotgen;

import com.google.gson.JsonElement;
import de.fraunhofer.iosb.ilt.configurable.ConfigEditor;
import de.fraunhofer.iosb.ilt.configurable.ConfigurationException;
import de.fraunhofer.iosb.ilt.configurable.annotations.ConfigurableField;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorClass;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorList;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorString;
import hylke.dotgen.model.Data;
import hylke.dotgen.model.ConformanceClass;
import hylke.dotgen.model.Image;
import hylke.dotgen.model.Recommendation;
import hylke.dotgen.model.Requerement;
import hylke.dotgen.model.RequerementClass;
import hylke.dotgen.model.ShortenCombo;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
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
public class ParserSta implements Parser {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParserSta.class.getName());
    private static final Pattern PATTERN_SPACES = Pattern.compile("[ ]{2,}");
    private static final Pattern PATTERN_SPACE = Pattern.compile("([ ]+)|(\\[[^ ]+\\])");
    private static final Pattern TABLE_REQ = Pattern.compile("^Req([0-9]+):.*");
    private static final Pattern TABLE_IGNORE = Pattern.compile("^Name|Entitytype|Operator|Function|Scenario$");

    private XPathExpression exprTablesList;
    private XPathExpression exprRowList;
    private XPathExpression exprCellList;
    private XPathExpression exprHeaderList;

    @ConfigurableField(editor = EditorString.class, label = "namespace", description = "Namespace is removed from definitions.")
    @EditorString.EdOptsString()
    private String nameSpace;

    @ConfigurableField(editor = EditorList.class, label = "IgnoreReqs", description = "Regexes to requirements to ignore.")
    @EditorList.EdOptsList(editor = EditorString.class)
    @EditorString.EdOptsString()
    private List<String> ignoreReqRegexes;

    @ConfigurableField(editor = EditorList.class, label = "IgnoreDeps", description = "Regexes to dependencies to ignore.")
    @EditorList.EdOptsList(editor = EditorString.class)
    @EditorString.EdOptsString()
    private List<String> ignoreDepRegexes;

    @ConfigurableField(editor = EditorList.class, optional = true,
            label = "Shortenings", description = "Shortenings for dependencies")
    @EditorList.EdOptsList(editor = EditorClass.class)
    @EditorClass.EdOptsClass(clazz = ShortenCombo.class)
    private List<ShortenCombo> depShorenings;

    private final Set<Pattern> ignoreReqs = new HashSet<>();
    private final Set<Pattern> ignoreDeps = new HashSet<>();

    private final Set<String> ignoredDeps = new HashSet<>();

    private Data documentData;

    @Override
    public Data getDocumentData() {
        return documentData;
    }

    @Override
    public ParserSta reset() {
        documentData = new Data(nameSpace);
        ignoredDeps.clear();
        return this;
    }

    @Override
    public void configure(JsonElement config, Void context, Void edtCtx, ConfigEditor<?> configEditor) throws ConfigurationException {
        Parser.super.configure(config, context, edtCtx, configEditor);
        documentData = new Data(nameSpace);
        ignoreReqRegexes.stream().forEach(t -> addIgnoreReq(t));
        ignoreDepRegexes.stream().forEach(t -> addIgnoreDep(t));
    }

    public ParserSta addIgnoreReq(String regex) {
        ignoreReqs.add(Pattern.compile(regex));
        return this;
    }

    public ParserSta addIgnoreDep(String regex) {
        ignoreDeps.add(Pattern.compile(regex));
        return this;
    }

    private NodeList getCellsFromRow(Node row) throws XPathExpressionException {
        NodeList cellList = (NodeList) exprCellList.evaluate(row, XPathConstants.NODESET);
        int colCount = cellList.getLength();
        if (colCount != 0) {
            return cellList;
        }
        return (NodeList) exprHeaderList.evaluate(row, XPathConstants.NODESET);
    }

    @Override
    public ParserSta parseSource(File sourceFile) throws IOException, ParserConfigurationException, XPathExpressionException, DOMException, SAXException {
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
        exprHeaderList = xpath.compile("//th");

        NodeList stationList = (NodeList) exprTablesList.evaluate(doc, XPathConstants.NODESET);
        int total = stationList.getLength();
        LOGGER.info("Found {} tables.", total);
        for (int i = 0; i < total; i++) {
            Node tableNode = stationList.item(i).cloneNode(true);
            NodeList rowList = (NodeList) exprRowList.evaluate(tableNode, XPathConstants.NODESET);
            int rowCount = rowList.getLength();

            Node firstRow = rowList.item(0).cloneNode(true);
            NodeList cellList = getCellsFromRow(firstRow);
            int colCount = cellList.getLength();
            if (colCount == 0) {
                LOGGER.warn("    Emtpy first row, {} rows", rowCount);
                continue;
            }

            Node firstCell = cellList.item(0).cloneNode(true);
            String type = cleanContent(firstCell.getTextContent(), true);
            LOGGER.debug("  Rows: {}, Cols: {}, Type: '{}'", rowCount, colCount, type);
            if ("RequirementsClass".equalsIgnoreCase(type)) {
                parseRequirementsClassTable(rowList);
            } else if ("ConformanceClass".equalsIgnoreCase(type)) {
                parseConformanceClassTable(rowList);
            } else if (TABLE_REQ.matcher(type).matches()) {
                parseRequirementTable(rowList);
            } else if ((type.startsWith("Recommendation/rec") || type.startsWith("/rec")) && rowCount == 1) {
                parseRecommendationTable(rowList);
            } else if (TABLE_IGNORE.matcher(type).matches()) {
                // Ignore
            } else {
                LOGGER.warn("    Unknown table type: {}, {} rows", type, rowCount);
            }
        }

        LOGGER.info("Ignored Dependencies:");
        for (String ignoredDep : ignoredDeps) {
            LOGGER.info("  '{}'", ignoredDep);
        }
        return this;
    }

    private String getCellContent(NodeList rowList, int rowNr, int columnNr) throws XPathExpressionException {
        Node row = rowList.item(rowNr).cloneNode(true);
        NodeList cellList = getCellsFromRow(row);
        Node cell = cellList.item(columnNr).cloneNode(true);
        return cell.getTextContent();
    }

    private String checkDepReplaces(String dep) {
        for (ShortenCombo shortenCombo : depShorenings) {
            dep = shortenCombo.maybeReplace(dep);
        }
        return dep;
    }

    private void parseRequirementsClassTable(NodeList rowList) throws XPathExpressionException {
        Set<Image> mainImages = Image.emptySet();
        String definition = cleanContent(getCellContent(rowList, 1, 0), true);
        if (Utils.matchesAnyOf(definition, ignoreReqs)) {
            return;
        }
        RequerementClass reqClass = documentData.findOrCreateRequirementClass(definition);
        mainImages.addAll(Image.imagesMatchingDef(reqClass.definition));

        int rowCount = rowList.getLength();
        for (int i = 2; i < rowCount; i++) {
            Node row = rowList.item(i).cloneNode(true);
            NodeList cellList = getCellsFromRow(row);
            int cellCount = cellList.getLength();

            if (cellCount != 2) {
                LOGGER.error("    Requirement row found with {} cells, expected 2", cellCount);
                continue;
            }
            Node nameCell = cellList.item(0).cloneNode(true);
            Node valueCell = cellList.item(1).cloneNode(true);
            String name = cleanContent(nameCell.getTextContent(), true);
            switch (name.toLowerCase()) {
                case "targettype": {
                    String value = cleanContent(valueCell.getTextContent(), false);
                    reqClass.targetType = value;
                    break;
                }

                case "type":
                case "name": {
                    String value = cleanContent(valueCell.getTextContent(), false);
                    reqClass.name = value;
                    LOGGER.info("Class {} - Name {}", reqClass.definition, reqClass.name);
                    break;
                }

                case "dependency": {
                    String value = cleanContent(valueCell.getTextContent(), false);
                    if (value.isEmpty()) {
                        continue;
                    }
                    if (value.startsWith(nameSpace)) {
                        value = cleanContent(valueCell.getTextContent(), true);
                        linkRequirementToClass(value, reqClass, mainImages);
                    } else if (Utils.matchesAnyOf(value, ignoreDeps)) {
                        ignoredDeps.add(value);
                    } else {
                        value = checkDepReplaces(value);
                        reqClass.addDependency(value);
                        documentData.checkImageForRelation(value, mainImages);
                    }
                    break;
                }

                case "requirementsclass":
                case "requirementssub-class":
                case "imports": {
                    String value = cleanContent(valueCell.getTextContent(), true);
                    linkRequirementClassToClass(value, reqClass, mainImages);
                    break;
                }

                case "requirement": {
                    String value = cleanContent(valueCell.getTextContent(), true);
                    linkRequirementToClass(value, reqClass, mainImages);
                    break;
                }

                case "recommendation": {
                    String value = cleanContent(valueCell.getTextContent(), true);
                    if (Utils.matchesAnyOf(value, ignoreReqs)) {
                        continue;
                    }
                    Recommendation rec = documentData.findOrCreateRecommendation(value);
                    reqClass.addRecommendation(rec);
                    documentData.checkImageForRelation(value, mainImages);
                    break;
                }

                default: {
                    String value = cleanContent(valueCell.getTextContent(), false);
                    LOGGER.warn("Unknown row: {} - {}", name, value);
                }
            }
        }
    }

    private void linkRequirementToClass(String value, RequerementClass reqClass, Set<Image> mainImages) {
        if (Utils.matchesAnyOf(value, ignoreReqs)) {
            return;
        }
        Requerement req = documentData.findOrCreateRequirement(value);
        reqClass.addRequirement(req);
        req.inClass.add(reqClass);
        documentData.checkImageForRelation(value, mainImages);
        return;
    }

    private void linkRequirementClassToClass(String value, RequerementClass reqClass, Set<Image> mainImages) {
        if (Utils.matchesAnyOf(value, ignoreReqs)) {
            return;
        }
        RequerementClass importedReq = documentData.findOrCreateRequirementClass(value);
        reqClass.addImport(importedReq);
        documentData.checkImageForRelation(importedReq.definition, mainImages);
        return;
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
                    if (Utils.matchesAnyOf(value, ignoreReqs)) {
                        return;
                    }
                    confClass = documentData.findOrCreateConformanceClass(value);
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
                    if (Utils.matchesAnyOf(value, ignoreReqs)) {
                        continue;
                    }
                    RequerementClass req = documentData.findOrCreateRequirementClass(value);
                    confClass.addRequirement(req);
                    break;

                default:
                    value = cleanContent(valueCell.getTextContent(), false);
                    LOGGER.warn("Unknown row: {} - {}", name, value);
            }
        }
    }

    private void parseRequirementTable(NodeList rowList) throws XPathExpressionException {
        String definition = cleanContent(getCellContent(rowList, 2, 0), true);
        if (Utils.matchesAnyOf(definition, ignoreReqs)) {
            return;
        }
        Requerement req = documentData.findOrCreateRequirement(definition);
        String description = cleanContent(getCellContent(rowList, 1, 0), true);
        req.description = description;
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
            if (Utils.matchesAnyOf(def, ignoreReqs)) {
                continue;
            }
            String desc = cleanContent(descCell.getTextContent(), false);
            Recommendation rec = documentData.findOrCreateRecommendation(def);
            if (!rec.description.isEmpty()) {
                LOGGER.warn("Recommendation {} already has a description: {}", def, rec.description);
            }
            rec.description = desc;
        }
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

}
