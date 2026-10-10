package lk.thuhina.water.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import lk.thuhina.water.common.storage.LocalFileStorage;
import lk.thuhina.water.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageTest {

    @TempDir
    Path dir;

    private LocalFileStorage storage() {
        return new LocalFileStorage(new AppProperties(null, null, null, null, new AppProperties.Storage(dir.toString())));
    }

    @Test
    void storesReadsReplacesAndDeletesFiles() {
        LocalFileStorage storage = storage();
        storage.put("company/logo-1.png", new byte[] {1, 2, 3});
        assertThat(Files.exists(dir.resolve("company/logo-1.png"))).isTrue();
        assertThat(storage.get("company/logo-1.png")).hasValueSatisfying(b -> assertThat(b).containsExactly(1, 2, 3));

        storage.put("company/logo-1.png", new byte[] {9});
        assertThat(storage.get("company/logo-1.png")).hasValueSatisfying(b -> assertThat(b).containsExactly(9));

        storage.delete("company/logo-1.png");
        assertThat(storage.get("company/logo-1.png")).isEmpty();
        storage.delete("company/logo-1.png"); // deleting a missing file is fine
    }

    @Test
    void missingFileIsEmpty() {
        assertThat(storage().get("company/none.png")).isEmpty();
    }

    @Test
    void keysCannotLeaveTheStorageFolder() {
        LocalFileStorage storage = storage();
        for (String bad : new String[] {"../x.png", "company/../../x.png", "/etc/passwd", "Company/Logo.png",
                "company\\x.png", "", "company//x.png", "c:/x.png"}) {
            assertThatThrownBy(() -> storage.put(bad, new byte[] {1})).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
