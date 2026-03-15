package com.amrts.fridahelper.core.batch;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads .smali files and extracts method entries with access flags.
 *
 * <p>For each .smali file, parses:
 * <ul>
 *   <li>{@code .class} directive → class descriptor (e.g. {@code Lcom/example/Foo;})</li>
 *   <li>{@code .method} directives → method name, params, return type, access flags</li>
 * </ul>
 *
 * <p>Constructs full smali signatures ready for {@link
 * com.amrts.fridahelper.core.parser.SmaliSignatureParser}.
 */
public final class SmaliFileReader {

    private static final Pattern CLASS_PATTERN =
            Pattern.compile("^\\.class\\s+.+?(L[\\S]+;)\\s*$");

    private static final Pattern METHOD_PATTERN =
            Pattern.compile("^\\.method\\s+(.+)\\(([^)]*)\\)(\\S+)\\s*$");

    /**
     * Reads a single .smali file and extracts all method entries.
     *
     * @param path path to the .smali file
     * @return list of method entries; empty if no class directive or no methods found
     * @throws IOException if the file cannot be read
     */
    public List<SmaliMethodEntry> readFile(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        return parseLines(lines);
    }

    /**
     * Walks a directory tree, reads all .smali files, and returns all method entries.
     * Individual file read failures are silently skipped.
     *
     * @param directory root directory to scan
     * @return all method entries found across all .smali files
     * @throws IOException if the directory cannot be walked
     * @throws IllegalArgumentException if path is not a directory
     */
    public List<SmaliMethodEntry> readDirectory(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Not a directory: " + directory);
        }

        List<Path> smaliFiles = findSmaliFiles(directory);
        List<SmaliMethodEntry> allEntries = new ArrayList<>();

        for (Path file : smaliFiles) {
            try {
                allEntries.addAll(readFile(file));
            } catch (IOException ignored) {
                // Skip unreadable files — batch processing should be resilient
            }
        }
        return allEntries;
    }

    /**
     * Parses lines from a .smali file into method entries.
     * Public so callers can feed lines from any source (NIO, java.io, SAF InputStream, etc.).
     */
    public List<SmaliMethodEntry> parseLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return Collections.emptyList();
        }

        String classDescriptor = null;
        List<SmaliMethodEntry> entries = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();

            Matcher classMatcher = CLASS_PATTERN.matcher(trimmed);
            if (classMatcher.matches()) {
                classDescriptor = classMatcher.group(1);
                continue;
            }

            if (classDescriptor == null) continue;

            Matcher methodMatcher = METHOD_PATTERN.matcher(trimmed);
            if (methodMatcher.matches()) {
                String flagsAndName = methodMatcher.group(1).trim();
                String params = methodMatcher.group(2);
                String returnType = methodMatcher.group(3);

                int lastSpace = flagsAndName.lastIndexOf(' ');
                String methodName;
                Set<String> flags;

                if (lastSpace < 0) {
                    methodName = flagsAndName;
                    flags = Collections.emptySet();
                } else {
                    methodName = flagsAndName.substring(lastSpace + 1);
                    flags = parseFlags(flagsAndName.substring(0, lastSpace));
                }

                String signature = classDescriptor + "->" + methodName
                        + "(" + params + ")" + returnType;

                entries.add(new SmaliMethodEntry(
                        classDescriptor, methodName, signature, flags));
            }
        }

        return entries;
    }

    private static Set<String> parseFlags(String flagString) {
        Set<String> flags = new HashSet<>();
        for (String flag : flagString.trim().split("\\s+")) {
            if (!flag.isEmpty()) {
                flags.add(flag.toLowerCase(Locale.US));
            }
        }
        return flags;
    }

    private static List<Path> findSmaliFiles(Path directory) throws IOException {
        final List<Path> result = new ArrayList<>();
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().endsWith(".smali")) {
                    result.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
        return result;
    }
}
