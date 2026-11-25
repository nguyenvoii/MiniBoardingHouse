IF COL_LENGTH(N'dbo.booking_requests', N'guest_name') IS NULL
BEGIN
    ALTER TABLE dbo.booking_requests ALTER COLUMN tenant_id INT NULL;
    ALTER TABLE dbo.booking_requests ADD guest_name NVARCHAR(100) NULL, guest_phone VARCHAR(20) NULL, hold_until DATETIME2 NULL;
END;
IF COL_LENGTH(N'dbo.rooms', N'held_booking_id') IS NULL
    ALTER TABLE dbo.rooms ADD held_booking_id INT NULL;
IF OBJECT_ID(N'dbo.FK_rooms_held_booking', N'F') IS NULL
    ALTER TABLE dbo.rooms ADD CONSTRAINT FK_rooms_held_booking FOREIGN KEY (held_booking_id) REFERENCES dbo.booking_requests(booking_id);
