package org.dimdev.toomanycrashes;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.TinyUtils;
import net.minecraft.SharedConstants;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public final class StacktraceDeobfuscator {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Path MAPPINGS_DIRECTORY = FabricLoader.getInstance().getConfigDirectory().toPath().resolve("toomanycrashes");
    private static final long MAPPING_CACHE_TIME = 14 * 24 * 60 * 60 * 1000;
    private static final Map<String, String> mappings = new HashMap<>();

    static {
        try {
            Files.createDirectories(MAPPINGS_DIRECTORY);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void init() {
        mappings.clear();
        String version = SharedConstants.getGameVersion().getName();
        Path infoFile = MAPPINGS_DIRECTORY.resolve("mapping-info");
        String[] mappingInfo = read(infoFile, "none 0 0").split(" ");
        Path mappingsFile = MAPPINGS_DIRECTORY.resolve(mappingInfo[0] + "-" + mappingInfo[1] + ".tinyv2");

        if (!mappingInfo[0].equals(version) || System.currentTimeMillis() - Integer.parseInt(mappingInfo[2]) > MAPPING_CACHE_TIME || !Files.exists(mappingsFile)) {
            try {
                LOGGER.info("Updating mappings");

                Map<String, int[]> builds = new Gson().fromJson(
                        IOUtils.toString(new URL("https://maven.fabricmc.net/net/fabricmc/yarn/versions.json"), StandardCharsets.UTF_8),
                        TypeToken.getParameterized(Map.class, String.class, int[].class).getType()
                );

                int[] buildsForVersion = builds.get(version);

                if (buildsForVersion == null) {
                    throw new RuntimeException("no yarn version for " + version);
                }

                int build = buildsForVersion[buildsForVersion.length - 1];

                if (Integer.parseInt(mappingInfo[1]) != build) {
                    String mavenVersion = version + "+build." + build;
                    String mappings = null;

                    try (JarInputStream jin = new JarInputStream(
                            new URL("https://maven.fabricmc.net/net/fabricmc/yarn/" + mavenVersion + "/yarn-" + mavenVersion + "-v2.jar").openStream())) {
                        JarEntry entry;
                        while ((entry = jin.getNextJarEntry()) != null) {
                            if (entry.getName().equals("mappings/mappings.tiny")) {
                                mappings = IOUtils.toString(jin, StandardCharsets.UTF_8);
                                break;
                            }
                        }
                    }

                    if (mappings == null) {
                        throw new RuntimeException("mappings jar didn't contain mappings");
                    }

                    Files.write(infoFile, (version + " " + build + " " + System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
                    Files.write(mappingsFile, mappings.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
                }
            } catch (Throwable t) {
                if (Files.exists(mappingsFile)) {
                    LOGGER.error("failed to update mappings", t);
                } else {
                    throw new RuntimeException("failed to update mappings", t);
                }
            }
        }

        TinyUtils.createTinyMappingProvider(mappingsFile, "intermediary", "named").load(new IMappingProvider.MappingAcceptor() {
            @Override
            public void acceptClass(String srcName, String dstName) {
                mappings.put(srcName, dstName);
            }

            @Override
            public void acceptMethod(IMappingProvider.Member method, String dstName) {
                mappings.put(method.name, dstName);
            }

            @Override
            public void acceptMethodArg(IMappingProvider.Member method, int lvIndex, String dstName) {}

            @Override
            public void acceptMethodVar(IMappingProvider.Member method, int lvIndex, int startOpIdx, int asmIndex, String dstName) {}

            @Override
            public void acceptField(IMappingProvider.Member field, String dstName) {
                mappings.put(field.name, dstName);
            }
        });
    }

    private static String read(Path path, String fallback) {
        if (!Files.exists(path)) {
            return fallback;
        }

        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void deobfuscateThrowable(Throwable t) {
        Deque<Throwable> queue = new ArrayDeque<>();
        queue.add(t);
        while (!queue.isEmpty()) {
            t = queue.remove();
            t.setStackTrace(deobfuscateStacktrace(t.getStackTrace()));
            if (t.getCause() != null) {
                queue.add(t.getCause());
            }
            Collections.addAll(queue, t.getSuppressed());
        }
    }

    public static StackTraceElement[] deobfuscateStacktrace(StackTraceElement[] stackTrace) {
        if (mappings == null) {
            return stackTrace;
        }

        int index = 0;
        for (StackTraceElement el : stackTrace) {
            String remappedClass = mappings.get(el.getClassName());
            String remappedMethod = mappings.get(el.getMethodName());
            stackTrace[index++] = new StackTraceElement(
                    remappedClass != null ? remappedClass : el.getClassName(),
                    remappedMethod != null ? remappedMethod : el.getMethodName(),
                    remappedClass != null ? getFileName(remappedClass) : el.getFileName(),
                    el.getLineNumber()
            );
        }
        return stackTrace;
    }

    public static String getFileName(String className) {
        String remappedFile = className;
        int lastDot = className.lastIndexOf('.');
        if (lastDot != -1) {
            remappedFile = remappedFile.substring(lastDot + 1);
        }

        int firstDollar = className.indexOf('$');
        if (firstDollar != -1) {
            remappedFile = remappedFile.substring(0, firstDollar);
        }

        return remappedFile;
    }

    public static void main(String[] args) {
        init();

        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            System.out.println(entry.getKey() + " <=> " + entry.getValue());
        }
    }
}
