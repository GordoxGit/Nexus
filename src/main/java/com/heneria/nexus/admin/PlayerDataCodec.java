package com.heneria.nexus.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Handles serialization and deserialization of player data snapshots.
 */
public final class PlayerDataCodec {

    private final Map<PlayerDataFormat, ObjectMapper> mappers = new EnumMap<>(PlayerDataFormat.class);

    public PlayerDataCodec() {
        ObjectMapper jsonMapper = new ObjectMapper();
        configure(jsonMapper);
        mappers.put(PlayerDataFormat.JSON, jsonMapper);

        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        configure(yamlMapper);
        mappers.put(PlayerDataFormat.YAML, yamlMapper);
    }

    private void configure(ObjectMapper mapper) {
        mapper.findAndRegisterModules();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    private ObjectMapper mapper(PlayerDataFormat format) {
        ObjectMapper mapper = mappers.get(format);
        if (mapper == null) {
            throw new IllegalStateException("Aucun mapper pour le format " + format);
        }
        return mapper;
    }

    /**
     * Serialises the provided snapshot and writes it to disk.
     *
     * @param target  file to write
     * @param snapshot snapshot to serialise
     * @param format  output format
     * @throws IOException if the snapshot cannot be written
     */
    public void write(Path target, PlayerDataSnapshot snapshot, PlayerDataFormat format) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(format, "format");
        ObjectMapper mapper = mapper(format);
        String payload = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tempFile = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
        Files.writeString(tempFile, payload, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Reads the snapshot stored at the provided location.
     *
     * @param source file containing the snapshot
     * @param format format of the file
     * @return deserialised snapshot
     * @throws IOException if the file cannot be read or parsed
     */
    public PlayerDataSnapshot read(Path source, PlayerDataFormat format) throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(format, "format");
        ObjectMapper mapper = mapper(format);
        try {
            byte[] bytes = Files.readAllBytes(source);
            return mapper.readValue(bytes, PlayerDataSnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new IOException("Format de fichier invalide", exception);
        }
    }
}
