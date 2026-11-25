-- Chay trong co so du lieu Stayly.
IF OBJECT_ID(N'dbo.room_meter_changes', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.room_meter_changes (
        change_id INT IDENTITY(1,1) PRIMARY KEY,
        room_id INT NOT NULL REFERENCES dbo.rooms(room_id),
        meter_kind VARCHAR(10) NOT NULL CHECK (meter_kind IN ('ELECTRIC', 'WATER')),
        installed_on DATE NOT NULL,
        serial_number NVARCHAR(80) NOT NULL,
        previous_reading INT NULL CHECK (previous_reading >= 0),
        installed_reading INT NOT NULL CHECK (installed_reading >= 0),
        CONSTRAINT UQ_room_meter_change UNIQUE (room_id, meter_kind, installed_on, serial_number)
    );
END;
