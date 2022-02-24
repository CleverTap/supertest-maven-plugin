package com.clevertap.maven.plugins.supertest;

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

import static junit.framework.Assert.assertEquals;

class SuperTestMavenPluginTest {
    @Test
    void createRerunCommandTest() throws IOException, ParserConfigurationException, SAXException, URISyntaxException {
        SuperTestMavenPlugin bv = new SuperTestMavenPlugin();
        ClassLoader classLoader = getClass().getClassLoader();
        URL FooTest = classLoader.getResource("FooTest.xml");
        URL BarTest = classLoader.getResource("BarTest.xml");
        final RunResult FooTestResult = new SurefireReportParser(new File(FooTest.toURI())).parse();
        final RunResult BarTestResult = new SurefireReportParser(new File(BarTest.toURI())).parse();

        final Map<String, List<String>> classnameToTestcaseList = new HashMap<>();
        classnameToTestcaseList.put(FooTestResult.getClassName(), FooTestResult.getTestCases());
        classnameToTestcaseList.put(BarTestResult.getClassName(), BarTestResult.getTestCases());

        String rerunCommand = bv.createRerunCommand(classnameToTestcaseList);
        System.out.println(rerunCommand);
        assertEquals("mvn test -Dtest=com.example.FooTest,com.example.BarTest#barTest1+barTest2,", rerunCommand);
    }
}