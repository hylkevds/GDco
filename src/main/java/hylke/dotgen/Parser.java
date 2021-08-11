package hylke.dotgen;

import de.fraunhofer.iosb.ilt.configurable.AnnotatedConfigurable;
import hylke.dotgen.model.Data;
import java.io.File;
import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import org.w3c.dom.DOMException;
import org.xml.sax.SAXException;

/**
 *
 * @author hylke
 */
public interface Parser extends AnnotatedConfigurable<Void, Void> {

    public Data getDocumentData();

    public Parser parseSource(File sourceFile) throws IOException, ParserConfigurationException, XPathExpressionException, DOMException, SAXException;

    public Parser reset();
}
