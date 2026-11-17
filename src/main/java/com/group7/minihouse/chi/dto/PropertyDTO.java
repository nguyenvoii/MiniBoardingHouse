package com.group7.minihouse.chi.dto;

public class PropertyDTO {
    private Integer propertyId;
    private String propertyName;
    private String address;
    private Integer totalFloors;
    private String supportPhone;
    private long roomCount;
    private java.util.List<Floor> floors = new java.util.ArrayList<>();

    public Integer getPropertyId() { return propertyId; }
    public void setPropertyId(Integer propertyId) { this.propertyId = propertyId; }

    public String getPropertyName() { return propertyName; }
    public void setPropertyName(String propertyName) { this.propertyName = propertyName; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public Integer getTotalFloors() { return totalFloors; }
    public void setTotalFloors(Integer totalFloors) { this.totalFloors = totalFloors; }

    public String getSupportPhone() { return supportPhone; }
    public void setSupportPhone(String supportPhone) { this.supportPhone = supportPhone; }

    public java.util.List<Floor> getFloors() { return floors; }
    public void setFloors(java.util.List<Floor> floors) { this.floors = floors; }

    public long getRoomCount() { return roomCount; }
    public void setRoomCount(long roomCount) { this.roomCount = roomCount; }

    public static class Floor {
        private Integer floorId;
        private Integer floorNumber;
        private String floorName;

        public Floor(Integer floorId, Integer floorNumber, String floorName) {
            this.floorId = floorId;
            this.floorNumber = floorNumber;
            this.floorName = floorName;
        }

        public Integer getFloorId() { return floorId; }
        public Integer getFloorNumber() { return floorNumber; }
        public String getFloorName() { return floorName; }
    }
}
