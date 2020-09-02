/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package hylke.dotgen;

import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

/**
 *
 * @author hylke
 */
public class DotGen {

    private static final Logger LOGGER = LoggerFactory.getLogger(DotGen.class.getName());

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException, SAXException, ParserConfigurationException, XPathExpressionException {
        if (args.length == 2) {
            Generator gen = new Generator(args[0], args[1]);
            gen.process();
        } else {
            LOGGER.warn("Usage: DotGen [sourcefile] [targetfile]");
        }
    }

}
