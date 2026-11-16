package com.group7.minihouse.shared.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.group7.minihouse.shared.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

/** Luu anh upload: nhan dang bang noi dung file (khong tin ten/duoi file client gui), chong path traversal. */
class FileStorageTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
    static final byte[] WEBP = {'R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P', 0};

    @TempDir Path dir;
    FileStorage storage;

    @BeforeEach
    void setUp() {
        storage = new FileStorage(dir.toString());
    }

    @Test
    void savesImagesWithExtensionFromContent() throws Exception {
        String png = storage.saveImage("avatars", PNG, 1024);
        String jpg = storage.saveImage("avatars", JPG, 1024);
        String webp = storage.saveImage("avatars", WEBP, 1024);

        assertTrue(png.endsWith(".png"));
        assertTrue(jpg.endsWith(".jpg"));
        assertTrue(webp.endsWith(".webp"));
        assertTrue(Files.exists(dir.resolve("avatars").resolve(png)));
    }

    @Test
    void rejectsNonImageEmptyAndTooLarge() {
        byte[] script = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class,
                () -> storage.saveImage("avatars", script, 1024)).getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class,
                () -> storage.saveImage("avatars", new byte[0], 1024)).getStatus());
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, assertThrows(BusinessException.class,
                () -> storage.saveImage("avatars", PNG, 5)).getStatus());
    }

    @Test
    void findsOnlyInsideFolderAndRejectsTraversal() throws Exception {
        String name = storage.saveImage("avatars", PNG, 1024);
        Files.writeString(dir.resolve("secret.txt"), "x");

        assertTrue(storage.find("avatars", name).isPresent());
        assertFalse(storage.find("avatars", "../secret.txt").isPresent());
        assertFalse(storage.find("avatars", "..\\secret.txt").isPresent());
        assertFalse(storage.find("avatars", "khong-co.png").isPresent());
    }

    @Test
    void deleteRemovesFileAndIgnoresMissingOrUnsafeNames() throws Exception {
        String name = storage.saveImage("avatars", PNG, 1024);
        storage.delete("avatars", name);
        assertFalse(Files.exists(dir.resolve("avatars").resolve(name)));

        storage.delete("avatars", "khong-co.png"); // khong nem loi
        storage.delete("avatars", "../secret.txt");
    }

    @Test
    void contentTypeFollowsExtension() {
        assertEquals("image/png", FileStorage.contentTypeOf("a.png"));
        assertEquals("image/jpeg", FileStorage.contentTypeOf("a.jpg"));
        assertEquals("image/webp", FileStorage.contentTypeOf("a.webp"));
        assertEquals("application/octet-stream", FileStorage.contentTypeOf("a.bin"));
    }
}
