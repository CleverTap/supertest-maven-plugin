package com.clevertap.maven.plugins.supertest.util;

import java.util.Locale;

public class OSUtil {
    private static final String OS = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);

    private OSUtil() {
        // prevents instance creation
    }

    public static boolean isUnix() {
        return (OS.contains("nix") || OS.contains("nux") || OS.contains("aix")
                || OS.contains("mac"));
    }
}
