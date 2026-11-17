package com.group7.minihouse.chi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class ChiWebConfig implements WebMvcConfigurer {

    private final Path uploadRoot;

    public ChiWebConfig(@Value("${app.upload.dir:uploads}") String uploadDir) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Chỉ phục vụ ảnh phòng. Không mở cả thư mục uploads/ vì trong đó còn chữ ký hợp đồng, ảnh đại diện, ảnh QR
        // (các file này phải đi qua API có kiểm tra quyền).
        String roomsUri = uploadRoot.resolve("rooms").toUri().toString();
        if (!roomsUri.endsWith("/")) {
            roomsUri += "/";
        }
        registry.addResourceHandler("/uploads/rooms/**").addResourceLocations(roomsUri);
    }
}
