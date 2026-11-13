package com.group7.minihouse.shared.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.DynamicInsert;

/** Bang `room_media`. */
@DynamicInsert
@Entity
@Table(name = "room_media")
public class RoomMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "media_id")
    private Integer mediaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "media_url", nullable = false, length = 500)
    private String mediaUrl;

    @Column(name = "is_cover")
    private Boolean isCover;

    public RoomMedia() {}

    public Integer getMediaId() { return mediaId; }
    public void setMediaId(Integer mediaId) { this.mediaId = mediaId; }

    public Room getRoom() { return room; }
    public void setRoom(Room room) { this.room = room; }

    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }

    public Boolean getIsCover() { return isCover; }
    public void setIsCover(Boolean isCover) { this.isCover = isCover; }

}
