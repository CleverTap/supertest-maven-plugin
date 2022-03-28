package com.clevertap.maven.plugins.supertest;

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

/**
 * Created by Jude Pereira, at 12:48 on 04/02/2022.
 */
public class SurefireReportParserTest {

    @Test
    void parse() throws IOException, ParserConfigurationException, SAXException {
        final String xmlReport = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<testsuite xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"https://maven.apache.org/surefire/maven-surefire-plugin/xsd/surefire-test-report-3.0.xsd\" version=\"3.0\" name=\"com.example.FooTest\" time=\"0.353\" tests=\"0\" errors=\"0\" skipped=\"0\" failures=\"0\">\n"
                + "</testsuite>";

        final Path xmlFile = Files.createTempFile("surefire", ".xml");
        Files.write(xmlFile, xmlReport.getBytes(StandardCharsets.UTF_8));

        final RunResult result = new SurefireReportParser(xmlFile.toFile()).parse();
        assertEquals("com.example.FooTest", result.getClassName());
    }
}