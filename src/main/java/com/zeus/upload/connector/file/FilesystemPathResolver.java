package com.zeus.upload.connector.file;

import java.nio.file.Path;
import java.util.Objects;

final class FilesystemPathResolver {
    private FilesystemPathResolver() { }

    static Path resolve(Path rootDirectory, String relativeFile) {
        Path root = Objects.requireNonNull(rootDirectory, "rootDirectory must not be null")
                .toAbsolutePath().normalize();
        Path requested = Path.of(Objects.requireNonNull(relativeFile, "relativeFile must not be null"));
        if (requested.isAbsolute()) throw new IllegalArgumentException("Filesystem connector requires a relative file path");
        Path resolved = root.resolve(requested).normalize();
        if (!resolved.startsWith(root)) throw new IllegalArgumentException("Filesystem path escapes the configured root directory");
        return resolved;
    }
}
