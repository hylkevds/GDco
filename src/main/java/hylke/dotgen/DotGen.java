package hylke.dotgen;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import de.fraunhofer.iosb.ilt.configurable.ConfigurationException;
import java.io.File;
import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

/**
 *
 * @author hylke
 */
public class DotGen {

    private static final Logger LOGGER = LoggerFactory.getLogger(DotGen.class.getName());

    public static void main(String[] args) throws ConfigurationException, IOException, SAXException, ParserConfigurationException, XPathExpressionException {

        switch (args.length) {
            case 0:
                Gui.main(args);
                break;
            case 1: {
                String config = FileUtils.readFileToString(new File(args[0]), "UTF-8");
                JsonElement json = JsonParser.parseString(config);
                Generator gen = new Generator();
                gen.configure(json, null, null, null);
                gen.process();
                break;
            }
            case 2: {
                Generator gen = new Generator(args[0], args[1]);
                gen.process();
                break;
            }
            default: {
                LOGGER.warn("Usage: DotGen [configfile]");
                LOGGER.warn("Usage: DotGen [source] [target]");
            }
        }
    }

}
