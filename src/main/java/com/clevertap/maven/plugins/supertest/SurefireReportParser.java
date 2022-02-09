package com.clevertap.maven.plugins.supertest;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class SurefireReportParser {

    private final File xmlFile;

    SurefireReportParser(File file) {
        xmlFile = file;
    }
    

    public RunResult parse() throws ParserConfigurationException, IOException, SAXException {
        final List<String> failureTagsList = Arrays.asList("failure", "error", "rerunFailure",
                "rerunError");

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        final Document doc = builder.parse(xmlFile);

        doc.getDocumentElement().normalize();
        final Node testsuite = doc.getElementsByTagName("testsuite").item(0);

        final RunResult result = new RunResult(((Element) testsuite).getAttribute("name"));
        final NodeList testCaseList = doc.getElementsByTagName("testcase");

        for (int i = 0; i < testCaseList.getLength(); i++) {
            Node testCase = testCaseList.item(i);
            Node n = testCase.getChildNodes()
                    .item(1); // TODO: 04/02/2022 will fail if retry count is 0
            if (testCase.hasChildNodes() && failureTagsList.contains(n.getNodeName())) {
                Element testCaseElement = (Element) testCase;
                result.addTestCase(testCaseElement.getAttribute("name"));
            }
        }
        return result;
    }
}
