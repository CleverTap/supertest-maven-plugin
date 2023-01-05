package com.clevertap.maven.plugins.supertest;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.xml.sax.SAXException;

@Mojo(name = "supertest")
public class SuperTestMavenPlugin extends AbstractMojo {
    // this is the max time to wait in seconds for process termination after the stdout read is
    // finished or terminated
    private static final int STDOUT_POST_READ_WAIT_TIMEOUT = 10;
    private static final String TEST_REGEX = "-Dtest=(.*?)(\\s|$)";
    private static final Pattern TEST_REGEX_PATTERN = Pattern.compile(TEST_REGEX);

    private ExecutorService pool;

    @Parameter(defaultValue = "${project}", readonly = true)
    MavenProject project;

    @Parameter(property = "mvnTestOpts", readonly = true)
    String mvnTestOpts;

    @Parameter(property = "rerunProfile", readonly = true)
    String rerunProfile;

    @Parameter(property = "retryRunCount", readonly = true, defaultValue = "1")
    Integer retryRunCount;

    // in seconds
    @Parameter(property = "shellNoActivityTimeout", readonly = true, defaultValue = "300")
    Integer shellNoActivityTimeout;

    @Parameter(property = "includes" )
    List<String> includes;

    @Parameter(property = "excludes" )
    List<String> excludes;

    public void execute() throws MojoExecutionException, MojoFailureException {
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

        pool = Executors.newFixedThreadPool(1);
        Set<String> allTestClasses = new HashSet<>(new TestListResolver(
                includes,
                excludes,
                getTest(),
                new File(baseDir, "target/test-classes").getAbsolutePath()).scanDirectories());

        getLog().debug("Test classes dir: "
                + new File(baseDir, "target/test-classes").getAbsolutePath());
        getLog().debug("Test classes found: " + String.join(",", allTestClasses));

        int exitCode;
        final String command = "mvn test " + buildProcessedMvnTestOpts(artifactId, groupId);
        try {
            exitCode = runShellCommand(command, "supertest run#1");
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("Failed to run " + command, e);
        }

        if (exitCode == 0) {
            return;
        }

        // Strip -Dtest=... from the Maven opts if specified, since these were valid for the very first run only.
        mvnTestOpts = mvnTestOpts.replaceAll(TEST_REGEX, "");

        for (int retryRunNumber = 1; retryRunNumber <= retryRunCount; retryRunNumber++) {
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
                classnameToTestcaseList.put(
                        runResult.getClassName(), runResult.getFailedTestCases());
            }

            final String runCommand = createRerunCommand(allTestClasses, classnameToTestcaseList);

            // previous run exited with code > 0, but all tests were actually run successfully
            if (runCommand == null) {
                return;
            }

            final StringBuilder rerunCommand = new StringBuilder(runCommand);
            rerunCommand.append(buildProcessedMvnTestOpts(artifactId, groupId));
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

        pool.shutdown();

        if (exitCode != 0) {
            System.exit(1);
        }
    }

    public String getTest() {
        if (mvnTestOpts == null) {
            return "";
        }

        Matcher matcher = TEST_REGEX_PATTERN.matcher(mvnTestOpts);
        return matcher.find() ? matcher.group(1) : "";
    }

    private StringBuilder buildProcessedMvnTestOpts(String artifactId, String groupId) {
        final StringBuilder processedMvnTestOpts = new StringBuilder(" ");
        processedMvnTestOpts.append(mvnTestOpts);
        processedMvnTestOpts.append(" -pl ").append(groupId);
        processedMvnTestOpts.append(":").append(artifactId);
        return processedMvnTestOpts;
    }

    /**
     * @param command shell command to be executed
     * @return process exit value, returns 1 if failure
     */
    public int runShellCommand(final String command, final String commandDescriptor)
            throws IOException, InterruptedException {
        getLog().info("Running " + command);
        ProcessBuilder pb = new ProcessBuilder(getShellCommandAsArray(command));
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        readProcessStdOut(proc, commandDescriptor);

        // we don't want to wait forever, if something breaks
        boolean exited = proc.waitFor(STDOUT_POST_READ_WAIT_TIMEOUT, TimeUnit.SECONDS);

        if (exited) {
            return proc.exitValue();
        } else {
            proc.destroyForcibly();
            return 1;
        }
    }

    private String[] getShellCommandAsArray(String command) {
        // this is what Runtime.getRuntime().exec(...) is doing internally
        StringTokenizer tokenizer = new StringTokenizer(command);
        String[] cmdArray = new String[tokenizer.countTokens()];
        for (int i = 0; tokenizer.hasMoreTokens(); i++) {
            cmdArray[i] = tokenizer.nextToken();
        }

        return cmdArray;
    }

    private void readProcessStdOut(Process proc, String commandDescriptor) {
        InputStream inputStream = proc.getInputStream();
        InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        AtomicLong lastOutputTime = new AtomicLong(System.currentTimeMillis());
        CountDownLatch countDownLatch = new CountDownLatch(1);

        Future<?> task = pool.submit(() -> {
            String line;

            try {
                while ((line = bufferedReader.readLine()) != null) {
                    getLog().info(commandDescriptor + ": " + line);
                    lastOutputTime.set(System.currentTimeMillis());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            // task has finished
            countDownLatch.countDown();
        });

        boolean isDone = task.isDone();

        while (!isDone && hasRecentShellActivity(lastOutputTime.get())) {
            try {
                isDone = countDownLatch.await(shellNoActivityTimeout, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        try {
            // task is either done or it timed out, no need to wait much
            task.get(1, TimeUnit.SECONDS);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            getLog().info(commandDescriptor + ": Read stdout error - " + getStackTrace(e));
        }
    }

    private boolean hasRecentShellActivity(long lastTime) {
        return System.currentTimeMillis() - lastTime
                < TimeUnit.SECONDS.toMillis(shellNoActivityTimeout);
    }

    private String getStackTrace(Exception e) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        e.printStackTrace(printWriter);

        return stringWriter.getBuffer().toString();
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
    public String createRerunCommand(
            Set<String> allTestClasses, Map<String, List<String>> classnameToTestcaseList) {
        final StringBuilder retryRun = new StringBuilder("mvn test");
        Set<String> incompleteTests = new HashSet<>(allTestClasses);

        retryRun.append(" -Dtest=");
        int emptyRetryRunLen = retryRun.length();

        // TODO: 04/02/2022 replace with Java 8 streams
        for (String className : classnameToTestcaseList.keySet()) {
            // if a test class is in the surefire report, it means that all its tests were executed
            incompleteTests.remove(className);
            List<String> failedTestCaseList = classnameToTestcaseList.get(className);

            if (!failedTestCaseList.isEmpty()) {
                appendFailedTestCases(className, failedTestCaseList, retryRun);
            } else {
                // passing tests will not be re-run anymore
                allTestClasses.remove(className);
            }
        }

        if (retryRun.length() != emptyRetryRunLen
                && retryRun.charAt(retryRun.length() - 1) != ','
                && !incompleteTests.isEmpty()) {
            retryRun.append(",");
        }

        retryRun.append(String.join(",", incompleteTests));

        return retryRun.length() != emptyRetryRunLen ? retryRun.toString() : null;
    }

    private void appendFailedTestCases(
            String className, List<String> failedTestCaseList, StringBuilder retryRun) {
        retryRun.append(className);

        if (failedTestCaseList.contains("")) {
            retryRun.append(",");
            return;
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
