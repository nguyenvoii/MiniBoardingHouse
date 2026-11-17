package com.group7.minihouse.chi.controller;

import com.group7.minihouse.chi.dto.RoomMediaDTO;
import com.group7.minihouse.chi.service.RoomMediaService;
import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.exception.BusinessException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/chi/media")
public class RoomMediaController {

    private final RoomMediaService roomMediaService;

    public RoomMediaController(RoomMediaService roomMediaService) {
        this.roomMediaService = roomMediaService;
    }

    @GetMapping("/room/{roomId}")
    public ApiResponse<List<RoomMediaDTO>> getByRoomId(@PathVariable Integer roomId) {
        return ApiResponse.ok(roomMediaService.getByRoomId(roomId));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<RoomMediaDTO> uploadImage(
            @RequestParam("roomId") Integer roomId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "isCover", defaultValue = "false") Boolean isCover) {
        try {
            return ApiResponse.ok(roomMediaService.uploadMedia(roomId, file, isCover));
        } catch (IOException e) {
            throw new BusinessException("Lỗi lưu file ảnh: " + e.getMessage());
        }
    }

    @PostMapping
    public ApiResponse<RoomMediaDTO> addMedia(@RequestBody RoomMediaDTO.CreateRequest req) {
        return ApiResponse.ok(roomMediaService.addMedia(req));
    }

    @PatchMapping("/{id}/cover")
    public ApiResponse<Void> setCover(@PathVariable Integer id) {
        roomMediaService.setCover(id);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteMedia(@PathVariable Integer id) {
        roomMediaService.deleteMedia(id);
        return ApiResponse.ok(null);
    }
}
