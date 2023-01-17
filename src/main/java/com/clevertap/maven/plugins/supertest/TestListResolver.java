package com.clevertap.maven.plugins.supertest;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.surefire.util.DirectoryScanner;

public class TestListResolver {
    private static final String[] DEFAULT_INCLUDES = new String[] {
            "**/Test*.java", "**/*Test.java", "**/*Tests.java", "**/*TestCase.java"};
    private static final String[] DEFAULT_EXCLUDES = new String[] {"**/*$*"};

    private final DirectoryScanner scanner;

    public TestListResolver(
            List<String> includes, List<String> excludes, String test, String testClassDir)
            throws MojoFailureException {
        scanner = new DirectoryScanner(
                new File(testClassDir),
                new org.apache.maven.surefire.testset.TestListResolver(
                        getIncludeList(test, includes), getExcludeList(test, excludes)));
    }

    public List<String> scanDirectories() {
        return scanner.scan().getClasses();
    }

    private List<String> getIncludeList(String test, List<String> includes)
            throws MojoFailureException {
        return getFilterList(test, includes, DEFAULT_INCLUDES, x -> x.split(","));
    }

    private List<String> getExcludeList(String test, List<String> excludes)
            throws MojoFailureException {
        return getFilterList(test, excludes, DEFAULT_EXCLUDES, x -> new String[] {});
    }

    private List<String> getFilterList(
            String test,
            List<String> filterData,
            String[] defaultFilterData,
            Function<String, String[]> parseTestFunc) throws MojoFailureException {
        List<String> filterList = new ArrayList<>();

        if (isSpecificTestSpecified(test)) {
            Collections.addAll(filterList, parseTestFunc.apply(test));
        } else {
            if (filterData != null) {
                filterList.addAll(filterData);
                checkMethodFilterInIncludesExcludes(filterList);
            }

            if (filterList.isEmpty()) {
                Collections.addAll(filterList, defaultFilterData);
            }
        }

        return filterNulls(filterList);
    }

    private static boolean isSpecificTestSpecified(String test) {
        return test != null && !test.isEmpty();
    }

    private static void checkMethodFilterInIncludesExcludes(Iterable<String> patterns)
            throws MojoFailureException {
        for (String pattern : patterns) {
            if (pattern != null && pattern.contains( "#" )) {
                throw new MojoFailureException(
                        "Method filter prohibited in includes|excludes parameter: " + pattern);
            }
        }
    }

    private static List<String> filterNulls(List<String> toFilter) {
        return toFilter.stream()
                .filter(x -> x != null && !x.trim().isEmpty())
                .collect(Collectors.toList());
    }
}
