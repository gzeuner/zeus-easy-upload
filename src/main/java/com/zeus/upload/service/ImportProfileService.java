package com.zeus.upload.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeus.upload.config.AppProperties;
import com.zeus.upload.domain.ImportProfile;
import com.zeus.upload.domain.ImportRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class ImportProfileService {

    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");

    private final ObjectMapper objectMapper;
    private final Path profileDirectory;

    @Autowired
    public ImportProfileService(ObjectMapper objectMapper, AppProperties appProperties) {
        this(objectMapper, Path.of(appProperties.getProfileDirectory()));
    }

    ImportProfileService(ObjectMapper objectMapper, Path profileDirectory) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.profileDirectory = Objects.requireNonNull(profileDirectory).toAbsolutePath().normalize();
    }

    public ImportProfile save(String name, ImportRequest request) throws IOException {
        String safeName = validateName(name);
        Objects.requireNonNull(request, "request must not be null");
        Files.createDirectories(profileDirectory);
        ImportProfile profile = new ImportProfile(safeName, request);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(profilePath(safeName).toFile(), profile);
        return profile;
    }

    public ImportProfile load(String name) throws IOException {
        return objectMapper.readValue(profilePath(validateName(name)).toFile(), ImportProfile.class);
    }

    public List<String> list() throws IOException {
        if (!Files.isDirectory(profileDirectory)) {
            return List.of();
        }
        try (var paths = Files.list(profileDirectory)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
    }

    public void delete(String name) throws IOException {
        Files.deleteIfExists(profilePath(validateName(name)));
    }

    private Path profilePath(String name) {
        return profileDirectory.resolve(name + ".json").normalize();
    }

    private String validateName(String name) {
        if (!StringUtils.hasText(name) || !SAFE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Profile name must match [A-Za-z0-9][A-Za-z0-9_-]{0,63}.");
        }
        return name;
    }
}
