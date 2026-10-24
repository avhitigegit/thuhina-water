package lk.thuhina.water.settings.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.common.storage.FileStorage;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Company logo (M01): PNG or JPG up to 1 MB, kept in {@link FileStorage}; the {@code company.logo} setting holds the
 * stored file's key. Printed documents (M06 onwards) read it from here. The type is checked from the file's
 * content, not from its name.
 */
@Service
public class CompanyLogoService {

    public static final long MAX_BYTES = 1024 * 1024;
    public static final String FIELD = "file";
    public static final String MSG_TYPE = "Logo must be a PNG or JPG image.";
    public static final String MSG_SIZE = "Logo must be 1 MB or smaller.";

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    /** The logo's bytes and media type. */
    public record Logo(byte[] content, MediaType mediaType) {
    }

    private final FileStorage storage;
    private final SettingsService settings;

    public CompanyLogoService(FileStorage storage, SettingsService settings) {
        this.storage = storage;
        this.settings = settings;
    }

    @Transactional
    public ObjectNode upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException(FIELD, "Choose a PNG or JPG image.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ValidationException(FIELD, MSG_SIZE);
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String ext = startsWith(content, PNG) ? "png" : startsWith(content, JPG) ? "jpg" : null;
        if (ext == null) {
            throw new ValidationException(FIELD, MSG_TYPE);
        }
        String key = "company/logo-" + UUID.randomUUID() + "." + ext;
        storage.put(key, content);
        long kb = Math.max(1, Math.round(content.length / 1024.0));
        return settings.setManaged(SettingsGroup.COMPANY, "logo", JsonNodeFactory.instance.stringNode(key),
                "Company logo uploaded (" + ext.toUpperCase() + ", " + kb + " KB)");
    }

    @Transactional
    public ObjectNode remove() {
        return settings.setManaged(SettingsGroup.COMPANY, "logo", JsonNodeFactory.instance.nullNode(), "Company logo removed");
    }

    /** The current logo, if one is set and its file exists. */
    @Transactional(readOnly = true)
    public Optional<Logo> current() {
        String key = currentKey();
        if (key == null) {
            return Optional.empty();
        }
        MediaType type = key.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return storage.get(key).map(bytes -> new Logo(bytes, type));
    }

    @Transactional(readOnly = true)
    public String currentKey() {
        var logo = settings.get(SettingsGroup.COMPANY.key()).get("logo");
        return logo != null && logo.isString() && !logo.asString().isBlank() ? logo.asString() : null;
    }

    private static boolean startsWith(byte[] content, byte[] magic) {
        return content.length >= magic.length && Arrays.equals(content, 0, magic.length, magic, 0, magic.length);
    }
}
