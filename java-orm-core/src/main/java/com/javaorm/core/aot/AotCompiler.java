package com.javaorm.core.aot;

import com.javaorm.core.metadata.EntityMetadataManager;
import com.javaorm.core.proxy.ProxyBytecodeBuilder;
import net.bytebuddy.dynamic.DynamicType;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * AOT 编译期字节码固化工具。
 * 扫描 target/classes 下的所有实体，调用 ProxyBytecodeBuilder.buildUnloaded(clazz).saveIn(outputDir)
 * 并生成全局预热注册表配置。
 */
public class AotCompiler {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("[JavaORM AOT] Missing outputDirectory argument!");
            return;
        }

        File outputDir = new File(args[0]);
        if (!outputDir.exists() || !outputDir.isDirectory()) {
            System.err.println("[JavaORM AOT] Output directory does not exist: " + outputDir.getAbsolutePath());
            return;
        }

        System.out.println("[JavaORM AOT] Scanning compiled classes in: " + outputDir.getAbsolutePath());
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        List<Class<?>> entityClasses = new ArrayList<>();
        scanClasses(outputDir, outputDir, classLoader, entityClasses);

        System.out.println("[JavaORM AOT] Found " + entityClasses.size() + " entity classes to solidify.");

        for (Class<?> entityClass : entityClasses) {
            DynamicType.Unloaded<?> unloaded = ProxyBytecodeBuilder.buildUnloaded(entityClass);
            unloaded.saveIn(outputDir);
            System.out.println("[JavaORM AOT] Pre-compiled proxy saved: " + ProxyBytecodeBuilder.getProxyClassName(entityClass));
        }

        // 写入静态实体索引名单，供 Native Image 启动时秒级预热
        saveEntityList(outputDir, entityClasses);

        System.out.println("[JavaORM AOT] AOT compilation completed successfully.");
    }

    private static void scanClasses(File rootDir, File currentDir, ClassLoader cl, List<Class<?>> result) {
        File[] files = currentDir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanClasses(rootDir, file, cl, result);
            } else if (file.getName().endsWith(".class") && !file.getName().contains("$")) {
                String relative = rootDir.toURI().relativize(file.toURI()).getPath();
                String className = relative.replace('/', '.').replace('\\', '.').replace(".class", "");
                try {
                    Class<?> clazz = cl.loadClass(className);
                    int mods = clazz.getModifiers();
                    if (Modifier.isAbstract(mods) || Modifier.isInterface(mods) || clazz.isEnum()) {
                        continue;
                    }
                    if (EntityMetadataManager.hasEntityAnnotation(clazz)) {
                        result.add(clazz);
                    }
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void saveEntityList(File outputDir, List<Class<?>> entityClasses) throws IOException {
        File metaInf = new File(outputDir, "META-INF/javaorm");
        if (!metaInf.exists()) {
            metaInf.mkdirs();
        }
        File listFile = new File(metaInf, "aot-entities.txt");
        List<String> lines = new ArrayList<>();
        for (Class<?> clazz : entityClasses) {
            lines.add(clazz.getName());
        }
        Files.write(listFile.toPath(), lines);
    }
}
