package com.group7.minihouse.chi.service;

import com.group7.minihouse.chi.dto.RoomMediaDTO;
import com.group7.minihouse.chi.repository.RoomMediaRepository;
import com.group7.minihouse.chi.repository.RoomRepository;
import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.entity.RoomMedia;
import com.group7.minihouse.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class RoomMediaService {

    private final RoomMediaRepository roomMediaRepository;
    private final RoomRepository roomRepository;

    public RoomMediaService(RoomMediaRepository roomMediaRepository, RoomRepository roomRepository) {
        this.roomMediaRepository = roomMediaRepository;
        this.roomRepository = roomRepository;
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

        Path uploadDir = Paths.get("uploads", "rooms");
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }

        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.lastIndexOf('.') > 0) {
            ext = originalFilename.substring(originalFilename.lastIndexOf('.'));
        }
        String savedFileName = UUID.randomUUID().toString().replace("-", "") + ext;
        Path destination = uploadDir.resolve(savedFileName);

        Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

        String mediaUrl = "/uploads/rooms/" + savedFileName;

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
        Room room = roomRepository.findById(req.getRoomId())
                .orElseThrow(() -> BusinessException.notFound("Phòng"));

        if (Boolean.TRUE.equals(req.getIsCover())) {
            roomMediaRepository.findByRoomRoomIdAndIsCoverTrue(req.getRoomId())
                    .forEach(m -> m.setIsCover(false));
        }

        RoomMedia media = new RoomMedia();
        media.setRoom(room);
        media.setMediaUrl(req.getMediaUrl());
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
        if (!roomMediaRepository.existsById(mediaId)) {
            throw BusinessException.notFound("Ảnh");
        }
        roomMediaRepository.deleteById(mediaId);
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
