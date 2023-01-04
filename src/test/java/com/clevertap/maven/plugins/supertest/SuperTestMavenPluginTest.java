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

import static org.junit.Assert.assertTrue;

class SuperTestMavenPluginTest {
    @Test
    void createRerunCommandTest()
            throws IOException, ParserConfigurationException, SAXException, URISyntaxException {
        SuperTestMavenPlugin bv = new SuperTestMavenPlugin();
        ClassLoader classLoader = getClass().getClassLoader();
        URL FooTest = classLoader.getResource("FooTest.xml");
        URL BarTest = classLoader.getResource("BarTest.xml");
        final RunResult FooTestResult = new SurefireReportParser(new File(FooTest.toURI())).parse();
        final RunResult BarTestResult = new SurefireReportParser(new File(BarTest.toURI())).parse();

        final Map<String, List<String>> classnameToTestcaseList = new HashMap<>();
        classnameToTestcaseList.put(
                FooTestResult.getClassName(), FooTestResult.getFailedTestCases());
        classnameToTestcaseList.put(
                BarTestResult.getClassName(), BarTestResult.getFailedTestCases());

        Set<String> allTestClasses = new HashSet<>();
        allTestClasses.add(FooTestResult.getClassName());
        allTestClasses.add(BarTestResult.getClassName());

        String rerunCommand = bv.createRerunCommand(allTestClasses, classnameToTestcaseList);
        System.out.println(rerunCommand);
        // added additional condition because hashset can give any order
        assertTrue("mvn test -Dtest=com.example.FooTest,com.example.BarTest#barTest1*+barTest2*,".equals(rerunCommand)
                || "mvn test -Dtest=com.example.FooTest,com.example.BarTest#barTest2*+barTest1*,".equals(rerunCommand));
    }
}
