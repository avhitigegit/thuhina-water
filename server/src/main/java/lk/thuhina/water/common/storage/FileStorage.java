package lk.thuhina.water.common.storage;

import java.util.Optional;

/**
 * Stored files – the company logo (M01), scanned supplier quotations (M09) … (design 3.2).
 * Keys are relative paths such as {@code company/logo-….png}: lower-case letters, digits, '.', '_', '-' and '/'.
 * Local disk now ({@link LocalFileStorage}); S3 can be added later behind the same interface.
 */
public interface FileStorage {

    void put(String key, byte[] content);

    Optional<byte[]> get(String key);

    void delete(String key);
}
