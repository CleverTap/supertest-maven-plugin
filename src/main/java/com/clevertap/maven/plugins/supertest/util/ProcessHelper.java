package com.clevertap.maven.plugins.supertest.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ProcessHelper {
    private final Runtime runtime;

    public ProcessHelper() {
        this(Runtime.getRuntime());
    }

    ProcessHelper(Runtime runtime) {
        this.runtime = runtime;
    }

    /**
     * Gets the process id of a java process.
     *
     * @return the pid found or -1 in case of an error on unsupported OS
     */
    @SuppressWarnings("java:S3011")
    public long getPid(Process process) {
        // java 8 does not have a handy util for getting pid, that's why reflection is needed
        // (since java 9, process management is much better)
        long pid = -1;

        try {
            if (isUnixProcess(process)) {
                Field field = process.getClass().getDeclaredField("pid");
                field.setAccessible(true);

                try {
                    pid = field.getLong(process);
                } finally {
                    field.setAccessible(false);
                }
            }
        } catch (Exception e) {
            pid = -1;
        }

        return pid;
    }

    @SuppressWarnings({"java:S1872"})
    public boolean isUnixProcess(Process process) {
        return process.getClass().getName().equals("java.lang.UNIXProcess");
    }

    public Process sendSIGINT(Process process) throws IOException {
        return sendSIGINT(getPid(process));
    }

    public Process sendSIGINT(long pid) throws IOException {
        return runtime.exec("kill -SIGINT " + pid);
    }

    public Process sendSIGTERM(Process process) throws IOException {
        return sendSIGTERM(getPid(process));
    }

    public Process sendSIGTERM(long pid) throws IOException {
        return runtime.exec("kill " + pid);
    }

    // depends on the presence of pgrep command
    public List<Long> getChildren(long pid) {
        if (!OSUtil.isUnix()) {
            throw new UnsupportedOperationException("Not supported on non-Unix OS.");
        }

        String children = runCommand("pgrep -P " + pid);
        return Arrays.stream(children.split("\n")).filter(c -> !c.isEmpty()).map(Long::parseLong)
                .collect(Collectors.toList());
    }

    /**
     * Gets the process ids of all leaves in the process tree of the provided pid.
     */
    public List<Long> getLeaves(long pid) {
        List<Long> leaves = new ArrayList<>();
        List<Long> children = getChildren(pid);

        for (long child : children) {
            List<Long> childLeaves = getLeaves(child);

            if (childLeaves.isEmpty()) {
                leaves.add(child);
            } else {
                leaves.addAll(childLeaves);
            }
        }

        return leaves;
    }

    private String runCommand(String command) {
        try {
            Process proc = runtime.exec(command);
            StringBuilder output = new StringBuilder();

            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;

                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }

            return output.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
