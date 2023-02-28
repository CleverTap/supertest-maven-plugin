package com.clevertap.maven.plugins.supertest.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.util.collections.Sets;

public class ProcessHelperTest {
    private Runtime runtime;
    private ProcessHelper processHelper;

    @Before
    public void setUp() {
        runtime = mock(Runtime.class);
        processHelper = new ProcessHelper(runtime);
    }

    @Test
    public void testGetPid() throws IOException {
        assumeTrue(OSUtil.isUnix());

        Process process = Runtime.getRuntime().exec("ls");
        long pid = processHelper.getPid(process);
        assertTrue(pid > 0);
    }

    @Test
    public void testIsUnixProcess() throws IOException {
        assertTrue(OSUtil.isUnix());
        assertTrue(processHelper.isUnixProcess(Runtime.getRuntime().exec("ls")));
    }

    @Test
    public void testGetChildren() throws IOException {
        assumeTrue(OSUtil.isUnix());

        doReturn(mockProcess("54321\n65432\n")).when(runtime).exec("pgrep -P 12345");
        List<Long> children = processHelper.getChildren(12345L);
        assertEquals(Arrays.asList(54321L, 65432L), children);
    }

    @Test
    public void testGetLeaves() throws IOException {
        assumeTrue(OSUtil.isUnix());

        doReturn(mockProcess("54321\n65432\n")).when(runtime).exec("pgrep -P 12345");
        doReturn(mockProcess("111\n222\n")).when(runtime).exec("pgrep -P 54321");
        doReturn(mockProcess("")).when(runtime).exec("pgrep -P 65432");
        doReturn(mockProcess("")).when(runtime).exec("pgrep -P 111");
        doReturn(mockProcess("")).when(runtime).exec("pgrep -P 222");

        Set<Long> expectedLeaves = Sets.newSet(222L, 111L, 65432L);
        Set<Long> actualLeaves = new HashSet<>(processHelper.getLeaves(12345L));

        assertEquals(expectedLeaves, actualLeaves);
    }

    @Test
    public void testSendSIGINT() throws Exception {
        Process killProcess = mock(Process.class);
        when(runtime.exec("kill -SIGINT 12345")).thenReturn(killProcess);

        Process process = processHelper.sendSIGINT(12345L);
        assertEquals(killProcess, process);
    }

    @Test
    public void testSendSIGTERM() throws Exception {
        Process killProcess = mock(Process.class);
        when(runtime.exec("kill 12345")).thenReturn(killProcess);

        Process process = processHelper.sendSIGTERM(12345L);
        assertEquals(killProcess, process);
    }

    private Process mockProcess(String output) {
        Process process = mock(Process.class);
        when(process.getInputStream())
                .thenReturn(new ByteArrayInputStream(output.getBytes()));

        return process;
    }
}
