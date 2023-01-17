package com.clevertap.maven.plugins.supertest;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

class ValidIdentifierTest {
    @Test
    void testValidIdentifier()
            throws IOException, ParserConfigurationException, SAXException, URISyntaxException {
        SuperTestMavenPlugin bv = new SuperTestMavenPlugin();
        ClassLoader classLoader = getClass().getClassLoader();
        URL validIdentifierTest = classLoader.getResource("ValidIdentifierTest.xml");
        final RunResult validIdentifierTestResult =
                new SurefireReportParser(new File(validIdentifierTest.toURI())).parse();
        Set<String> allTestClasses = new HashSet<>();
        allTestClasses.add("com.validIdentifierTest");

        final Map<String, List<String>> classnameToTestcaseList = new HashMap<>();
        classnameToTestcaseList.put(
                validIdentifierTestResult.getClassName(),
                validIdentifierTestResult.getFailedTestCases());

        String rerunCommand = bv.createRerunCommand(allTestClasses, classnameToTestcaseList);
        System.out.println(rerunCommand);
        assertEquals("mvn test -Dtest=com.validIdentifierTest#fooTest*,", rerunCommand);
    }
}
