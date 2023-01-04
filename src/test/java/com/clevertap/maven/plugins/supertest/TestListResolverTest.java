package com.clevertap.maven.plugins.supertest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TestListResolverTest {
    private static final String TEST_CLASS_DIR = "target/sample-test-classes";

    @BeforeAll
    public static void setUpBeforeClass() throws IOException {
        createSampleTestClasses();
    }

    @Test
    void testScanDirectoriesWithDefaultIncludes() throws MojoFailureException {
        TestListResolver testListResolver = new TestListResolver(null, null, null, TEST_CLASS_DIR);
        List<String> actualTestClasses = testListResolver.scanDirectories();
        List<String> expectedTestClasses = Arrays.asList(
                "RootLevelClassTest",
                "Test1",
                "nested1.NestedClass1Test",
                "nested1.AnotherNestedTest",
                "nested1.SampleTests",
                "nested1.nested2.NestedLevel2Test",
                "nested1.nested2.NestedLevel2TestCase");

        assertEquals(new HashSet<>(expectedTestClasses), new HashSet<>(actualTestClasses));
    }

    @Test
    void testScanDirectoriesWithCustomPattern() throws MojoFailureException {
        TestListResolver testListResolver = new TestListResolver(
                Arrays.asList("nested1/**/*Test", "nested1/**/*Pattern"),
                null,
                null,
                TEST_CLASS_DIR);

        List<String> actualTestClasses = testListResolver.scanDirectories();
        List<String> expectedTestClasses = Arrays.asList(
                "nested1.NestedClass1Test",
                "nested1.AnotherNestedTest",
                "nested1.NonMatchingPattern",
                "nested1.nested2.NestedLevel2Test");

        assertEquals(new HashSet<>(expectedTestClasses), new HashSet<>(actualTestClasses));
    }

    @Test
    void testScanDirectoriesWithExcludes() throws MojoFailureException {
        TestListResolver testListResolver = new TestListResolver(
                null,
                Collections.singletonList("nested1/**/*Test"),
                null,
                TEST_CLASS_DIR);

        List<String> actualTestClasses = testListResolver.scanDirectories();
        List<String> expectedTestClasses = Arrays.asList(
                "RootLevelClassTest",
                "Test1",
                "nested1.SampleTests",
                "nested1.nested2.NestedLevel2TestCase");

        assertEquals(new HashSet<>(expectedTestClasses), new HashSet<>(actualTestClasses));
    }

    @Test
    void testScanDirectoriesWhenTestParamProvided() throws MojoFailureException {
        TestListResolver testListResolver = new TestListResolver(
                null,
                null,
                "RootLevelClassTest,SampleTests,nested1.nested2.NestedLevel2TestCase",
                TEST_CLASS_DIR);

        List<String> actualTestClasses = testListResolver.scanDirectories();
        List<String> expectedTestClasses = Arrays.asList(
                "RootLevelClassTest",
                "nested1.SampleTests",
                "nested1.nested2.NestedLevel2TestCase");

        assertEquals(new HashSet<>(expectedTestClasses), new HashSet<>(actualTestClasses));
    }

    @Test
    void testScanDirectoriesWithRegexInclude() throws MojoFailureException {
        TestListResolver testListResolver = new TestListResolver(
                Arrays.asList("%regex[.*Another.*]", "%regex[nested1.*Pattern.*.class]"),
                null,
                null,
                TEST_CLASS_DIR);

        List<String> actualTestClasses = testListResolver.scanDirectories();
        List<String> expectedTestClasses = Arrays.asList(
                "nested1.AnotherNestedTest",
                "nested1.NonMatchingPattern");

        assertEquals(new HashSet<>(expectedTestClasses), new HashSet<>(actualTestClasses));
    }

    @Test
    void testScanDirectoriesWithMethodFilterInTest() throws MojoFailureException {
        TestListResolver testListResolver = new TestListResolver(
                null,
                null,
                "AnotherNestedTest#test()",
                TEST_CLASS_DIR);
        List<String> actualTestClasses = testListResolver.scanDirectories();
        List<String> expectedTestClasses = Collections.singletonList("nested1.AnotherNestedTest");

        assertEquals(new HashSet<>(expectedTestClasses), new HashSet<>(actualTestClasses));
    }

    @Test
    void testScanDirectoriesWithMethodFilterInIncludes() {
        assertThrows(
                MojoFailureException.class,
                () -> new TestListResolver(
                        Collections.singletonList("AnotherNestedTest#test()"),
                        null,
                        null,
                        TEST_CLASS_DIR),
                "Method filter prohibited in includes|excludes parameter:AnotherNestedTest#test()");
    }

    @Test
    void testScanDirectoriesWithMethodFilterInExcludes() {
        assertThrows(
                MojoFailureException.class,
                () -> new TestListResolver(
                        null,
                        Collections.singletonList("AnotherNestedTest#test()"),
                        null,
                        TEST_CLASS_DIR),
                "Method filter prohibited in includes|excludes parameter:AnotherNestedTest#test()");
    }

    // creates a few classes to be used for testing the resolver
    private static void createSampleTestClasses() throws IOException {
        // clean-up any old copies of the sample test dir
        deleteDir(TEST_CLASS_DIR);

        Files.createDirectories(Paths.get(TEST_CLASS_DIR));
        Files.createDirectories(Paths.get(TEST_CLASS_DIR, "nested1"));
        Files.createDirectories(Paths.get(TEST_CLASS_DIR, "nested1", "nested2"));

        Files.createFile(Paths.get(TEST_CLASS_DIR, "RootLevelClassTest.class"));
        Files.createFile(Paths.get(TEST_CLASS_DIR, "NonClassExtensionTest.java"));
        Files.createFile(Paths.get(TEST_CLASS_DIR, "Test1.class"));
        Files.createFile(Paths.get(TEST_CLASS_DIR, "nested1", "NestedClass1Test.class"));
        Files.createFile(Paths.get(TEST_CLASS_DIR, "nested1", "AnotherNestedTest.class"));
        Files.createFile(Paths.get(TEST_CLASS_DIR, "nested1", "NonMatchingPattern.class"));
        Files.createFile(Paths.get(TEST_CLASS_DIR, "nested1", "SampleTests.class"));
        Files.createFile(Paths.get(
                TEST_CLASS_DIR, "nested1", "nested2", "NestedLevel2Test.class"));
        Files.createFile(Paths.get(
                TEST_CLASS_DIR, "nested1", "nested2", "NestedLevel2TestCase.class"));
    }

    private static void deleteDir(String dir) throws IOException {
        if (!new File(dir).exists()) {
            return;
        }

        try (Stream<Path> paths = Files.walk(Paths.get(dir))) {
            paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }
}
