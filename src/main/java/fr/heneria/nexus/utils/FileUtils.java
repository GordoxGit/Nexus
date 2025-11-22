package fr.heneria.nexus.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.stream.Stream;

public class FileUtils {

    public static void copyDirectory(File source, File target) throws IOException {
        if (!source.exists()) {
            return;
        }

        if (!target.exists()) {
            target.mkdirs();
        }

        try (Stream<Path> paths = Files.walk(source.toPath())) {
            paths.forEach(sourcePath -> {
                Path targetPath = target.toPath().resolve(source.toPath().relativize(sourcePath));
                try {
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    public static void deleteDirectory(File directory) throws IOException {
        if (!directory.exists()) {
            return;
        }

        try (Stream<Path> pathStream = Files.walk(directory.toPath())) {
            pathStream.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }
}
