package com.group7.minihouse.chi.dto;

public class PropertyFloorDTO {

    private Integer floorId;
    private Integer propertyId;
    private Integer floorNumber;
    private String floorName;

    public PropertyFloorDTO() {}

    public PropertyFloorDTO(Integer floorId, Integer propertyId, Integer floorNumber, String floorName) {
        this.floorId = floorId;
        this.propertyId = propertyId;
        this.floorNumber = floorNumber;
        this.floorName = floorName;
    }

    public Integer getFloorId() { return floorId; }
    public void setFloorId(Integer floorId) { this.floorId = floorId; }

    public Integer getPropertyId() { return propertyId; }
    public void setPropertyId(Integer propertyId) { this.propertyId = propertyId; }

    public Integer getFloorNumber() { return floorNumber; }
    public void setFloorNumber(Integer floorNumber) { this.floorNumber = floorNumber; }

    public String getFloorName() { return floorName; }
    public void setFloorName(String floorName) { this.floorName = floorName; }

    // DTO trả về kết quả
    public static class Response {
        private Integer floorId;
        private Integer propertyId;
        private Integer floorNumber;
        private String floorName;

        public Response() {}

        public Response(Integer floorId, Integer propertyId, Integer floorNumber, String floorName) {
            this.floorId = floorId;
            this.propertyId = propertyId;
            this.floorNumber = floorNumber;
            this.floorName = floorName;
        }

        public Integer getFloorId() { return floorId; }
        public void setFloorId(Integer floorId) { this.floorId = floorId; }

        public Integer getPropertyId() { return propertyId; }
        public void setPropertyId(Integer propertyId) { this.propertyId = propertyId; }

        public Integer getFloorNumber() { return floorNumber; }
        public void setFloorNumber(Integer floorNumber) { this.floorNumber = floorNumber; }

        public String getFloorName() { return floorName; }
        public void setFloorName(String floorName) { this.floorName = floorName; }
    }

    // DTO nhận dữ liệu tạo mới tầng
    public static class CreateRequest {
        private Integer propertyId;
        private Integer floorNumber;
        private String floorName;

        public CreateRequest() {}

        public Integer getPropertyId() { return propertyId; }
        public void setPropertyId(Integer propertyId) { this.propertyId = propertyId; }

        public Integer getFloorNumber() { return floorNumber; }
        public void setFloorNumber(Integer floorNumber) { this.floorNumber = floorNumber; }

        public String getFloorName() { return floorName; }
        public void setFloorName(String floorName) { this.floorName = floorName; }
    }

    // DTO nhận dữ liệu cập nhật tầng
    public static class UpdateRequest {
        private Integer floorNumber;
        private String floorName;

        public UpdateRequest() {}

        public Integer getFloorNumber() { return floorNumber; }
        public void setFloorNumber(Integer floorNumber) { this.floorNumber = floorNumber; }

        public String getFloorName() { return floorName; }
        public void setFloorName(String floorName) { this.floorName = floorName; }
    }
}
