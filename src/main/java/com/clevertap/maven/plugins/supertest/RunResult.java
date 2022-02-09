package com.clevertap.maven.plugins.supertest;

import java.util.ArrayList;
import java.util.List;

public class RunResult {

    final String className;
    final List<String> testCases;

    public RunResult(String className) {
        this.className = className;
        this.testCases = new ArrayList<>();
    }

    public void addTestCase(final String testCase) {
        testCases.add(testCase);
    }

    public List<String> getTestCases() {
        return testCases;
    }

    public String getClassName() {
        return className;
    }
}
