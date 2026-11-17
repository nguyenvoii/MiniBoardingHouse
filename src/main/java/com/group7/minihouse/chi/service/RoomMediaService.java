package com.group7.minihouse.chi.service;

import com.group7.minihouse.chi.dto.RoomMediaDTO;
import com.group7.minihouse.chi.repository.RoomMediaRepository;
import com.group7.minihouse.chi.repository.RoomRepository;
import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.entity.RoomMedia;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.storage.FileStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class RoomMediaService {

    static final String FOLDER = "rooms";
    static final String URL_PREFIX = "/uploads/rooms/";
    static final long MAX_BYTES = 5L * 1024 * 1024;

    private final RoomMediaRepository roomMediaRepository;
    private final RoomRepository roomRepository;
    private final FileStorage fileStorage;

    public RoomMediaService(RoomMediaRepository roomMediaRepository, RoomRepository roomRepository,
                            FileStorage fileStorage) {
        this.roomMediaRepository = roomMediaRepository;
        this.roomRepository = roomRepository;
        this.fileStorage = fileStorage;
    }

    @Transactional(readOnly = true)
    public List<RoomMediaDTO> getByRoomId(Integer roomId) {
        return roomMediaRepository.findByRoomRoomId(roomId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public RoomMediaDTO uploadMedia(Integer roomId, MultipartFile file, Boolean isCover) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Vui lòng chọn file ảnh");
        }

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> BusinessException.notFound("Phòng"));

        // Chỉ nhận ảnh PNG/JPG/WebP (kiểm tra theo nội dung file, không tin tên file), tên lưu ngẫu nhiên
        String savedFileName = fileStorage.saveImage(FOLDER, file.getBytes(), MAX_BYTES);
        String mediaUrl = URL_PREFIX + savedFileName;

        if (Boolean.TRUE.equals(isCover)) {
            roomMediaRepository.findByRoomRoomIdAndIsCoverTrue(roomId)
                    .forEach(m -> m.setIsCover(false));
        }

        RoomMedia media = new RoomMedia();
        media.setRoom(room);
        media.setMediaUrl(mediaUrl);
        media.setIsCover(Boolean.TRUE.equals(isCover));

        return toDTO(roomMediaRepository.save(media));
    }

    public RoomMediaDTO addMedia(RoomMediaDTO.CreateRequest req) {
        if (req == null || req.getRoomId() == null) {
            throw new BusinessException("Chưa chọn phòng");
        }
        String url = req.getMediaUrl() == null ? "" : req.getMediaUrl().trim();
        if (url.isEmpty() || url.length() > 500) {
            throw new BusinessException("Đường dẫn ảnh không được để trống và tối đa 500 ký tự");
        }
        if (!url.startsWith(URL_PREFIX) && !url.startsWith("https://") && !url.startsWith("http://")) {
            throw new BusinessException("Đường dẫn ảnh phải bắt đầu bằng http://, https:// hoặc " + URL_PREFIX);
        }

        Room room = roomRepository.findById(req.getRoomId())
                .orElseThrow(() -> BusinessException.notFound("Phòng"));

        if (Boolean.TRUE.equals(req.getIsCover())) {
            roomMediaRepository.findByRoomRoomIdAndIsCoverTrue(req.getRoomId())
                    .forEach(m -> m.setIsCover(false));
        }

        RoomMedia media = new RoomMedia();
        media.setRoom(room);
        media.setMediaUrl(url);
        media.setIsCover(Boolean.TRUE.equals(req.getIsCover()));

        return toDTO(roomMediaRepository.save(media));
    }

    public void setCover(Integer mediaId) {
        RoomMedia media = roomMediaRepository.findById(mediaId)
                .orElseThrow(() -> BusinessException.notFound("Ảnh"));
        Integer roomId = media.getRoom().getRoomId();

        roomMediaRepository.findByRoomRoomIdAndIsCoverTrue(roomId)
                .forEach(m -> m.setIsCover(false));

        media.setIsCover(true);
        roomMediaRepository.save(media);
    }

    public void deleteMedia(Integer mediaId) {
        RoomMedia media = roomMediaRepository.findById(mediaId)
                .orElseThrow(() -> BusinessException.notFound("Ảnh"));
        String url = media.getMediaUrl();
        roomMediaRepository.delete(media);
        // Ảnh do hệ thống lưu thì xóa luôn file, tránh rác trong thư mục uploads
        if (url != null && url.startsWith(URL_PREFIX)) {
            fileStorage.delete(FOLDER, url.substring(URL_PREFIX.length()));
        }
    }

    private RoomMediaDTO toDTO(RoomMedia entity) {
        RoomMediaDTO dto = new RoomMediaDTO();
        dto.setMediaId(entity.getMediaId());
        dto.setRoomId(entity.getRoom().getRoomId());
        dto.setRoomCode(entity.getRoom().getRoomCode());
        dto.setMediaUrl(entity.getMediaUrl());
        dto.setIsCover(Boolean.TRUE.equals(entity.getIsCover()));
        return dto;
    }
}
