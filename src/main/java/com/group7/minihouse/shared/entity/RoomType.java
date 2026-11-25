package com.group7.minihouse.shared.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity
@Table(name="room_types")
public class RoomType {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="room_type_id") private Integer roomTypeId;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="property_id") private Property property;
 @Column(name="type_name",nullable=false,length=100) private String typeName;
 @Column(length=255) private String description;
 @Column(name="default_rent_price",nullable=false,precision=18,scale=2) private BigDecimal defaultRentPrice;
 @Column(name="default_deposit_amount",nullable=false,precision=18,scale=2) private BigDecimal defaultDepositAmount;
 public Integer getRoomTypeId(){return roomTypeId;} public void setRoomTypeId(Integer v){roomTypeId=v;}
 public Property getProperty(){return property;} public void setProperty(Property v){property=v;}
 public String getTypeName(){return typeName;} public void setTypeName(String v){typeName=v;}
 public String getDescription(){return description;} public void setDescription(String v){description=v;}
 public BigDecimal getDefaultRentPrice(){return defaultRentPrice;} public void setDefaultRentPrice(BigDecimal v){defaultRentPrice=v;}
 public BigDecimal getDefaultDepositAmount(){return defaultDepositAmount;} public void setDefaultDepositAmount(BigDecimal v){defaultDepositAmount=v;}
}
