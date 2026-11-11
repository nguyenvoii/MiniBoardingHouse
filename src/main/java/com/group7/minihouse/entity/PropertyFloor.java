package com.group7.minihouse.entity;

import jakarta.persistence.*;

/** Bang `property_floors`. */
@Entity
@Table(name = "property_floors")
public class PropertyFloor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "floor_id")
    private Integer floorId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Column(name = "floor_number", nullable = false)
    private Integer floorNumber;

    @Column(name = "floor_name", nullable = false, length = 50)
    private String floorName;

    public PropertyFloor() {}

    public Integer getFloorId() { return floorId; }
    public void setFloorId(Integer floorId) { this.floorId = floorId; }

    public Property getProperty() { return property; }
    public void setProperty(Property property) { this.property = property; }

    public Integer getFloorNumber() { return floorNumber; }
    public void setFloorNumber(Integer floorNumber) { this.floorNumber = floorNumber; }

    public String getFloorName() { return floorName; }
    public void setFloorName(String floorName) { this.floorName = floorName; }

}
