package com.javaorm.core.codefirst;

import java.io.File;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 实体类包扫描器（零第三方重型依赖，支持目录与 Jar 包）。
 */
public class PackageScanner {

    public static List<Class<?>> scan(String packageName) {
        List<Class<?>> classes = new ArrayList<>();
        if (packageName == null || packageName.trim().isEmpty()) {
            return classes;
        }
        String packagePath = packageName.replace('.', '/');
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader == null) {
                classLoader = PackageScanner.class.getClassLoader();
            }
            Enumeration<URL> resources = classLoader.getResources(packagePath);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                String protocol = resource.getProtocol();
                if ("file".equals(protocol)) {
                    String filePath = URLDecoder.decode(resource.getFile(), "UTF-8");
                    findClassesInDirectory(packageName, new File(filePath), classes);
                } else if ("jar".equals(protocol)) {
                    JarURLConnection jarConn = (JarURLConnection) resource.openConnection();
                    try (JarFile jar = jarConn.getJarFile()) {
                        Enumeration<JarEntry> entries = jar.entries();
                        while (entries.hasMoreElements()) {
                            JarEntry entry = entries.nextElement();
                            String name = entry.getName();
                            if (name.startsWith(packagePath) && name.endsWith(".class") && !entry.isDirectory()) {
                                String className = name.substring(0, name.length() - 6).replace('/', '.');
                                tryLoadClass(className, classes);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return classes;
    }

    private static void findClassesInDirectory(String packageName, File dir, List<Class<?>> classes) {
        if (!dir.exists() || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                findClassesInDirectory(packageName + "." + file.getName(), file, classes);
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + "." + file.getName().substring(0, file.getName().length() - 6);
                tryLoadClass(className, classes);
            }
        }
    }

    private static void tryLoadClass(String className, List<Class<?>> classes) {
        try {
            Class<?> clazz = Class.forName(className);
            classes.add(clazz);
        } catch (Throwable ignored) {
        }
    }
}
