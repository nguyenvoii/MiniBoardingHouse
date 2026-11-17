package com.group7.minihouse.chi.dto;

public class RoomMediaDTO {
    private Integer mediaId;
    private Integer roomId;
    private String roomCode;
    private String mediaUrl;
    private Boolean isCover;

    public RoomMediaDTO() {}

    public Integer getMediaId() { return mediaId; }
    public void setMediaId(Integer mediaId) { this.mediaId = mediaId; }

    public Integer getRoomId() { return roomId; }
    public void setRoomId(Integer roomId) { this.roomId = roomId; }

    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }

    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }

    public Boolean getIsCover() { return isCover; }
    public void setIsCover(Boolean isCover) { this.isCover = isCover; }

    public static class CreateRequest {
        private Integer roomId;
        private String mediaUrl;
        private Boolean isCover;

        public CreateRequest() {}

        public Integer getRoomId() { return roomId; }
        public void setRoomId(Integer roomId) { this.roomId = roomId; }

        public String getMediaUrl() { return mediaUrl; }
        public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }

        public Boolean getIsCover() { return isCover; }
        public void setIsCover(Boolean isCover) { this.isCover = isCover; }
    }
}
