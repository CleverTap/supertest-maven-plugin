package com.clevertap.maven.plugins.supertest;

import java.util.ArrayList;
import java.util.List;

public class RunResult {

    private final String className;
    private final List<String> failedTestCases = new ArrayList<>();

    public RunResult(String className) {
        this.className = className;
    }

    public void addFailedTestCase(final String testCase) {
        failedTestCases.add(testCase);
    }

    public List<String> getFailedTestCases() {
        return failedTestCases;
    }

    public String getClassName() {
        return className;
    }
}
