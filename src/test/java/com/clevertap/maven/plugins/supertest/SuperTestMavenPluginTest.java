package com.clevertap.maven.plugins.supertest;

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static junit.framework.Assert.assertEquals;

class SuperTestMavenPluginTest {
    @Test
    void createRerunCommand() throws IOException, ParserConfigurationException, SAXException  {
        SuperTestMavenPlugin bv = new SuperTestMavenPlugin();
        final String classXML1 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<testsuite xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"https://maven.apache.org/surefire/maven-surefire-plugin/xsd/surefire-test-report-3.0.xsd\" version=\"3.0\" name=\"com.example.FooTest\" time=\"0.353\" tests=\"0\" errors=\"0\" skipped=\"0\" failures=\"0\">\n"
                + "<testcase name=\"\" classname=\"com.example.FooTest\" time=\"14.945\">\n"
                + "<error message=\"Could not initialize class com.example.FooTest\" type=\"java.lang.NoClassDefFoundError\">\n"
                + "at com.example.FooTest\n"
                + "</error>\n"
                + "</testcase>\n"
                + "<testcase name=\"fooTest1\" classname=\"com.example.FooTest\" time=\"14.945\">\n"
                + "<error message=\"Could not initialize class com.example.FooTest\" type=\"java.lang.NoClassDefFoundError\">\n"
                + "at com.example.FooTest\n"
                + "</error>\n"
                + "</testcase>\n"
                + "</testsuite>";
        final String classXML2 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<testsuite xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"https://maven.apache.org/surefire/maven-surefire-plugin/xsd/surefire-test-report-3.0.xsd\" version=\"3.0\" name=\"com.example.BarTest\" time=\"0.353\" tests=\"0\" errors=\"0\" skipped=\"0\" failures=\"0\">\n"
                + "<testcase name=\"barTest1\" classname=\"com.example.BarTest\" time=\"14.945\">\n"
                + "<error message=\"Could not initialize class com.example.BarTest\" type=\"java.lang.NoClassDefFoundError\">]\n"
                + "at com.example.BarTest\n"
                + "</error>\n"
                + "</testcase>\n"
                + "<testcase name=\"barTest2\" classname=\"com.example.BarTest\" time=\"14.945\">\n"
                + "<error message=\"Could not initialize class com.example.BarTest\" type=\"java.lang.NoClassDefFoundError\">\n"
                + "at com.example.BarTest\n"
                + "</error>\n"
                + "</testcase>\n"
                + "</testsuite>";

        final Path xmlFile1 = Files.createTempFile("surefire", ".xml");
        Files.write(xmlFile1, classXML1.getBytes(StandardCharsets.UTF_8));
        final RunResult result1 = new SurefireReportParser(xmlFile1.toFile()).parse();

        final Path xmlFile2 = Files.createTempFile("surefire", ".xml");
        Files.write(xmlFile2, classXML2.getBytes(StandardCharsets.UTF_8));
        final RunResult result2 = new SurefireReportParser(xmlFile2.toFile()).parse();

        final Map<String, List<String>> classnameToTestcaseList = new HashMap<>();
        classnameToTestcaseList.put(result1.getClassName(), result1.getTestCases());
        classnameToTestcaseList.put(result2.getClassName(), result2.getTestCases());

        String rerunCommand = bv.createRerunCommand(classnameToTestcaseList);
        System.out.println(rerunCommand);
        assertEquals("mvn test -Dtest=com.example.FooTest,com.example.BarTest#barTest1+barTest2,", rerunCommand);
    }
}