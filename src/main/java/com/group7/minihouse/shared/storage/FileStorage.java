package com.group7.minihouse.shared.storage;

import com.group7.minihouse.shared.exception.BusinessException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Luu file upload (anh dai dien, anh QR, anh dong ho dien nuoc...) vao thu muc app.upload.dir (mac dinh ./uploads).
 * Moi file luu duoi ten ngau nhien (UUID), duoi file suy ra tu NOI DUNG (khong tin ten client gui).
 * Dung chung cho moi chuc nang upload anh; caller chi giu ten file tra ve.
 */
@Component
public class FileStorage {

    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");
    private static final Pattern SAFE_FOLDER = Pattern.compile("[a-z0-9-]+");

    private final Path root;

    public FileStorage(@Value("${app.upload.dir:uploads}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    /** Luu anh PNG/JPEG/WebP toi da maxBytes. Tra ve ten file da luu (vd "3f2a....png"). */
    public String saveImage(String folder, byte[] data, long maxBytes) {
        if (data == null || data.length == 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "File rỗng");
        }
        if (data.length > maxBytes) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "File quá lớn, tối đa " + (maxBytes / 1024) + " KB");
        }
        String ext = detectImageExtension(data);
        if (ext == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Chỉ nhận ảnh PNG, JPG hoặc WebP");
        }
        Path dir = folderPath(folder);
        String name = UUID.randomUUID() + "." + ext;
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(name), data);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return name;
    }

    /** File co ton tai trong thu muc folder khong; ten khong an toan (../, dau gach cheo) thi coi nhu khong co. */
    public Optional<Path> find(String folder, String name) {
        Path p = resolveSafe(folder, name);
        return p != null && Files.isRegularFile(p) ? Optional.of(p) : Optional.empty();
    }

    public void delete(String folder, String name) {
        Path p = resolveSafe(folder, name);
        if (p == null) {
            return;
        }
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String contentTypeOf(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }

    private Path resolveSafe(String folder, String name) {
        if (name == null || !SAFE_NAME.matcher(name).matches()) {
            return null;
        }
        Path dir = folderPath(folder);
        Path p = dir.resolve(name).normalize();
        return p.startsWith(dir) ? p : null;
    }

    private Path folderPath(String folder) {
        if (!SAFE_FOLDER.matcher(folder).matches()) {
            throw new IllegalArgumentException("Tên thư mục không hợp lệ: " + folder);
        }
        return root.resolve(folder);
    }

    private static String detectImageExtension(byte[] d) {
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') {
            return "png";
        }
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "webp";
        }
        return null;
    }
}
