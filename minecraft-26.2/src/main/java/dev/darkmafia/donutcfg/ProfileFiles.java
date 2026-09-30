package dev.darkmafia.donutcfg;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Bounded local file operations; hidden state and deleted profiles are never listed. */
final class ProfileFiles {
    private ProfileFiles() { }

    static boolean validName(String name) {
        return name != null && name.matches("[A-Za-z0-9_-]{1,48}");
    }

    static List<String> list(Path dir) throws IOException {
        if (!Files.exists(dir)) return List.of();
        try (var files = Files.list(dir)) {
            return files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - 5))
                    .filter(ProfileFiles::validName)
                    .sorted(Comparator.<String, Boolean>comparing(name -> !name.equals("default"))
                            .thenComparing(String.CASE_INSENSITIVE_ORDER).thenComparing(Comparator.naturalOrder()))
                    .toList();
        }
    }

    static Path archive(Path dir, String name) throws IOException {
        if (!validName(name)) throw new IllegalArgumentException("invalid config name");
        if (name.equalsIgnoreCase("default"))
            throw new IllegalArgumentException("default is reserved and cannot be deleted");
        Path target = dir.resolve(name + ".json");
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("config not found: " + name);
        Path deleted = dir.resolve(".deleted");
        if (Files.exists(deleted, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(deleted, LinkOption.NOFOLLOW_LINKS))
            throw new IOException(".deleted must be a regular directory, not a link or file");
        Files.createDirectories(deleted);
        int suffix = 1;
        Path backup;
        do { backup = deleted.resolve(name + "-" + suffix++ + ".json"); }
        while (Files.exists(backup));
        // No REPLACE_EXISTING: never overwrite an earlier recoverable deletion.
        return Files.move(target, backup);
    }
}
