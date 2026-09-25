package org.opendiplom.printing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Finds a Cyrillic serif font for the blanks.
 *
 * <p>PT Astra Serif (Astra Linux) and Liberation Serif (РЕД ОС, Альт) have the
 * metrics of Times New Roman (Windows), the font of the CyberDiploma templates,
 * so a field is as wide on every platform.
 */
public final class Fonts {
    private static final List<String> FILES = Arrays.asList(
        "PTAstraSerif-Regular.ttf", "times.ttf", "Times New Roman.ttf",
        "LiberationSerif-Regular.ttf"
    );
    private static final List<String> FOLDERS = Arrays.asList(
        "C:\\Windows\\Fonts", "/usr/share/fonts", "/usr/local/share/fonts",
        System.getProperty("user.home") + "/.fonts", "/Library/Fonts",
        "/System/Library/Fonts/Supplemental"
    );
    private static final int DEPTH = 4;

    private Fonts() {
    }

    /**
     * The configured font, or the first known one installed.
     *
     * @param configured path from the settings, may be {@code null}
     */
    public static Optional<Path> serif(final String configured) {
        if (configured != null && !configured.isBlank()) {
            final Path path = Paths.get(configured);
            return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
        }
        for (final String file : FILES) {
            for (final String folder : FOLDERS) {
                final Optional<Path> found = find(Paths.get(folder), file);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Path> find(final Path folder, final String file) {
        if (!Files.isDirectory(folder)) {
            return Optional.empty();
        }
        try (Stream<Path> paths = Files.find(
            folder, DEPTH, (path, attributes) -> attributes.isRegularFile()
                && path.getFileName().toString().equalsIgnoreCase(file)
        )) {
            return paths.findFirst();
        } catch (final IOException | RuntimeException error) {
            return Optional.empty();
        }
    }
}
