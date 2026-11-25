-- Chạy trong cơ sở dữ liệu Stayly sau patch 06.
IF COL_LENGTH(N'dbo.rooms', N'room_type_id') IS NULL
    ALTER TABLE dbo.rooms ADD room_type_id INT NULL;

IF OBJECT_ID(N'dbo.FK_rooms_room_type', N'F') IS NULL
    ALTER TABLE dbo.rooms ADD CONSTRAINT FK_rooms_room_type
        FOREIGN KEY (room_type_id) REFERENCES dbo.room_types(room_type_id);
