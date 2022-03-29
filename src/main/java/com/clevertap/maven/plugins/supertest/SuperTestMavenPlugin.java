package com.clevertap.maven.plugins.supertest;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.xml.sax.SAXException;

@Mojo(name = "supertest")
public class SuperTestMavenPlugin extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    MavenProject project;

    @Parameter(property = "mvnTestOpts", readonly = true)
    String mvnTestOpts;

    @Parameter(property = "rerunProfile", readonly = true)
    String rerunProfile;

    @Parameter(property = "retryRunCount", readonly = true, defaultValue = "1")
    Integer retryRunCount;

    public void execute() throws MojoExecutionException {

        if (mvnTestOpts == null) {
            mvnTestOpts = "";
        }

        mvnTestOpts = mvnTestOpts.trim();
        if (mvnTestOpts.startsWith("\"")) {
            mvnTestOpts = mvnTestOpts.substring(1);
        }

        if (mvnTestOpts.endsWith("\"")) {
            mvnTestOpts = mvnTestOpts.substring(0, mvnTestOpts.length() - 1);
        }

        final File baseDir = project.getBasedir();
        final String artifactId = project.getArtifactId();
        final String groupId = project.getGroupId();

        final StringBuilder processedMvnTestOpts = new StringBuilder(" ");
        processedMvnTestOpts.append(mvnTestOpts);
        processedMvnTestOpts.append(" -pl ").append(groupId);
        processedMvnTestOpts.append(":").append(artifactId);

        int exitCode;
        final String command = "mvn test " + processedMvnTestOpts;
        try {
            exitCode = runShellCommand(command, "supertest run#1");
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("Failed to run " + command, e);
        }

        if (exitCode == 0) {
            return;
        }

        for (int retryRunNumber = 1; retryRunNumber <= retryRunCount.intValue(); retryRunNumber++) {
            final File[] xmlFileList = getXmlFileList(baseDir);
            final Map<String, List<String>> classnameToTestcaseList = new HashMap<>();
            for (File file : xmlFileList) {
                final SurefireReportParser parser = new SurefireReportParser(file);
                final RunResult runResult;
                try {
                    runResult = parser.parse();
                } catch (ParserConfigurationException | IOException | SAXException e) {
                    throw new MojoExecutionException(
                            "Failed to parse surefire report! file=" + file, e);
                }
                classnameToTestcaseList.put(runResult.getClassName(), runResult.getTestCases());
            }

            final String runCommand = createRerunCommand(classnameToTestcaseList);
            final StringBuilder rerunCommand = new StringBuilder(runCommand);
            rerunCommand.append(processedMvnTestOpts);
            if (rerunProfile != null) {
                String trimmedRerunProfile = rerunProfile.replaceAll("\"", "");
                rerunCommand.append(" -P ").append(trimmedRerunProfile);
            }

            try {
                exitCode = runShellCommand(rerunCommand.toString(),
                        "supertest retry run#" + retryRunNumber);
            } catch (IOException | InterruptedException e) {
                throw new MojoExecutionException("Failed to retry tests! command=" + rerunCommand,
                        e);
            }

            if (exitCode == 0) {
                break;
            }
        }

        if (exitCode != 0) {
            System.exit(1);
        }
    }

    /**
     * @param command shell command to be executed
     * @return process exit value, returns 1 if failure
     */
    public int runShellCommand(final String command, final String commandDescriptor)
            throws IOException, InterruptedException {
        Process proc = Runtime.getRuntime().exec(command);
        InputStream inputStream = proc.getInputStream();
        InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            getLog().info(commandDescriptor + ": " + line);
        }
        proc.waitFor();
        return proc.exitValue();
    }

    /**
     * @param baseDir
     * @return list of surefire reports
     */
    public File[] getXmlFileList(File baseDir) {
        final File surefireReportsDirectory = new File(baseDir, "target/surefire-reports");
        if (surefireReportsDirectory.isDirectory()) {
            return surefireReportsDirectory.listFiles(
                    file -> file.getName().toLowerCase().endsWith(".xml"));
        }
        return new File[0];
    }

    /**
     * @param classnameToTestcaseList map of classname and list of failed test cases
     * @return rerunCommand
     */
    public String createRerunCommand(Map<String, List<String>> classnameToTestcaseList) {
        final StringBuilder retryRun = new StringBuilder("mvn test");
        retryRun.append(" -Dtest=");
        // TODO: 04/02/2022 replace with Java 8 streams
        for (String className : classnameToTestcaseList.keySet()) {
            List<String> failedTestCaseList = classnameToTestcaseList.get(className);
            if (!failedTestCaseList.isEmpty()) {
                retryRun.append(className);
                if(failedTestCaseList.contains("")) {
                    retryRun.append(",");
                    continue;
                }
                retryRun.append("#");
                for (int i = 0; i < failedTestCaseList.size(); i++) {
                    retryRun.append(failedTestCaseList.get(i)).append("*");
                    if (i == failedTestCaseList.size() - 1) {
                        retryRun.append(",");
                    } else {
                        retryRun.append("+");
                    }
                }
            }
        }
        return retryRun.toString();
    }
}
