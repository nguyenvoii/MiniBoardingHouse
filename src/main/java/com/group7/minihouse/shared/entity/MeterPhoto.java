package com.group7.minihouse.shared.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.DynamicInsert;

/** Bang `meter_photos`. */
@DynamicInsert
@Entity
@Table(name = "meter_photos")
public class MeterPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "photo_id")
    private Integer photoId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reading_id", nullable = false)
    private MeterReading reading;

    @Column(name = "meter_type", nullable = false, length = 10)
    private String meterType;

    @Column(name = "photo_url", nullable = false, length = 500)
    private String photoUrl;

    public MeterPhoto() {}

    public Integer getPhotoId() { return photoId; }
    public void setPhotoId(Integer photoId) { this.photoId = photoId; }

    public MeterReading getReading() { return reading; }
    public void setReading(MeterReading reading) { this.reading = reading; }

    public String getMeterType() { return meterType; }
    public void setMeterType(String meterType) { this.meterType = meterType; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

}
