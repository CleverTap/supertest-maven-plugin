package com.clevertap.maven.plugins.supertest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

class SuperTestMavenPluginTest {
    @Test
    void createRerunCommandTest()
            throws IOException, ParserConfigurationException, SAXException, URISyntaxException {
        SuperTestMavenPlugin plugin = new SuperTestMavenPlugin();
        ClassLoader classLoader = getClass().getClassLoader();
        URL FooTest = classLoader.getResource("FooTest.xml");
        URL BarTest = classLoader.getResource("BarTest.xml");
        final RunResult FooTestResult = new SurefireReportParser(new File(FooTest.toURI())).parse();
        final RunResult BarTestResult = new SurefireReportParser(new File(BarTest.toURI())).parse();

        final Map<String, List<String>> classNameToTestCaseList = new HashMap<>();
        classNameToTestCaseList.put(
                FooTestResult.getClassName(), FooTestResult.getFailedTestCases());
        classNameToTestCaseList.put(
                BarTestResult.getClassName(), BarTestResult.getFailedTestCases());

        Set<String> allTestClasses = new HashSet<>();
        allTestClasses.add(FooTestResult.getClassName());
        allTestClasses.add(BarTestResult.getClassName());
        allTestClasses.add("com.example.NotRun1");
        allTestClasses.add("com.example.NotRun2");

        String rerunCommand = plugin.createRerunCommand(allTestClasses, classNameToTestCaseList);
        assertTrue(rerunCommand.startsWith("mvn test -Dtest="));
        assertEquals(
                getRunCommandTestValue(
                        "mvn test -Dtest=com.example.FooTest,"
                                + "com.example.BarTest#barTest1*+barTest2*,"
                                + "com.example.NotRun1,com.example.NotRun2"),
                getRunCommandTestValue(rerunCommand));
    }

    @Test
    void testCreateRerunCommandWhenNoTestFailedButSomeDidNotComplete() {
        SuperTestMavenPlugin plugin = new SuperTestMavenPlugin();

        final Map<String, List<String>> classNameToTestCaseList = new HashMap<>();

        Set<String> allTestClasses = new HashSet<>();
        allTestClasses.add("com.example.Test");

        String rerunCommand = plugin.createRerunCommand(allTestClasses, classNameToTestCaseList);
        assertTrue(rerunCommand.startsWith("mvn test -Dtest="));
        assertEquals(
                getRunCommandTestValue("mvn test -Dtest=com.example.Test"),
                getRunCommandTestValue(rerunCommand));
    }

    @Test
    void testCreateRerunCommandWhenAllTestsArePassing() {
        SuperTestMavenPlugin plugin = new SuperTestMavenPlugin();

        final Map<String, List<String>> classNameToTestCaseList = new HashMap<>();
        classNameToTestCaseList.put("com.example.Test", new ArrayList<>());

        Set<String> allTestClasses = new HashSet<>();
        allTestClasses.add("com.example.Test");

        String rerunCommand = plugin.createRerunCommand(allTestClasses, classNameToTestCaseList);
        assertNull(rerunCommand);
    }

    /**
     * Extracts test param from mvn test -Dtest=... and converts it to a set of test classes, where
     * each test class is represented as a pair of name + set of test methods to be run.
     */
    private Set<Pair<String, Set<String>>> getRunCommandTestValue(String runCommand) {
        // E.g. com.example.BarTest#barTest1*+barTest2* is converted to
        // <com.example.BarTest, [barTest1*, barTest2*]>
        String test = runCommand.substring("mvn test -Dtest=".length());
        return Arrays.stream(test.split(",")).map(x -> {
            if (x.contains("#")) {
                int methodsStartIndex = x.indexOf('#');
                return Pair.of(
                        x.substring(0, methodsStartIndex),
                        Arrays.stream(x.substring(methodsStartIndex + 1).split("\\+"))
                                .collect(Collectors.toSet()));
            } else {
                return Pair.of(x, (Set<String>) new HashSet<String>());
            }
        }).collect(Collectors.toSet());
    }

    @Test
    void testGetTestWhenProvided() {
        SuperTestMavenPlugin plugin = new SuperTestMavenPlugin();
        plugin.mvnTestOpts = "-PdontReuseForks -Dtest=**PowerMock** jacoco:report";
        assertEquals("**PowerMock**", plugin.getTest());
    }

    @Test
    void testGetTestWhenNotProvided() {
        SuperTestMavenPlugin plugin = new SuperTestMavenPlugin();
        assertEquals("", plugin.getTest());
        plugin.mvnTestOpts = "";
        assertEquals("", plugin.getTest());
    }
}
