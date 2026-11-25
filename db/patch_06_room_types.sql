IF OBJECT_ID(N'room_types', N'U') IS NULL
BEGIN
CREATE TABLE [room_types] (
 [room_type_id] INT IDENTITY(1,1) PRIMARY KEY,
 [property_id] INT NOT NULL FOREIGN KEY REFERENCES [properties]([property_id]) ON DELETE CASCADE,
 [type_name] NVARCHAR(100) NOT NULL,
 [description] NVARCHAR(255) NULL,
 [default_rent_price] DECIMAL(18,2) NOT NULL,
 [default_deposit_amount] DECIMAL(18,2) NOT NULL,
 CONSTRAINT [UQ_room_type_property_name] UNIQUE([property_id], [type_name])
);
END;
