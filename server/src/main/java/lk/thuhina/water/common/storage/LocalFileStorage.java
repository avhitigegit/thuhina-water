package lk.thuhina.water.common.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.regex.Pattern;

import lk.thuhina.water.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * Files on the server's disk under {@code app.storage.dir} (FILES_DIR; a Docker volume in deploy/docker-compose.yml,
 * so it is kept across restarts and included in the server snapshot).
 */
@Component
public class LocalFileStorage implements FileStorage {

    private static final Pattern KEY = Pattern.compile("^[a-z0-9][a-z0-9._-]*(/[a-z0-9][a-z0-9._-]*)*$");

    private final Path root;

    public LocalFileStorage(AppProperties properties) {
        this.root = Path.of(properties.storage().dir()).toAbsolutePath().normalize();
    }

    @Override
    public void put(String key, byte[] content) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            Files.write(temp, content);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store " + key, e);
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        try {
            return Optional.of(Files.readAllBytes(resolve(key)));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete " + key, e);
        }
    }

    /** The file's path; refuses keys that could point outside the storage folder. */
    private Path resolve(String key) {
        if (key == null || !KEY.matcher(key).matches() || key.contains("..")) {
            throw new IllegalArgumentException("Invalid file key: " + key);
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid file key: " + key);
        }
        return path;
    }
}
