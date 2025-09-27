package com.heneria.nexus.admin;

import java.util.Locale;
import java.util.Set;

/**
 * Supported serialization formats for player data exports.
 */
public enum PlayerDataFormat {

    /** JSON export format. */
    JSON(Set.of("json")),
    /** YAML export format. */
    YAML(Set.of("yaml", "yml"));

    private final Set<String> extensions;

    PlayerDataFormat(Set<String> extensions) {
        this.extensions = extensions;
    }

    /**
     * Returns the primary extension associated with this format.
     *
     * @return default file extension without the leading dot
     */
    public String primaryExtension() {
        return extensions.iterator().next();
    }

    /**
     * Resolves the format from a file name.
     *
     * @param fileName file name to inspect
     * @return matching format
     * @throws IllegalArgumentException if the extension is unsupported
     */
    public static PlayerDataFormat fromFileName(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (PlayerDataFormat format : values()) {
            for (String extension : format.extensions) {
                if (lower.endsWith('.' + extension)) {
                    return format;
                }
            }
        }
        throw new IllegalArgumentException("Extension de fichier non prise en charge : " + fileName);
    }

    /**
     * Resolves the format from a token (for example provided on the command line).
     *
     * @param token token to parse
     * @return matching format
     * @throws IllegalArgumentException if no matching format exists
     */
    public static PlayerDataFormat fromToken(String token) {
        String normalized = token.toLowerCase(Locale.ROOT);
        for (PlayerDataFormat format : values()) {
            if (format.name().equalsIgnoreCase(normalized) || format.extensions.contains(normalized)) {
                return format;
            }
        }
        throw new IllegalArgumentException("Format inconnu : " + token);
    }
}
