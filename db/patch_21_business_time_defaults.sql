-- Các cột DATETIME2 lưu giờ Việt Nam. Tính từ UTC để độc lập với múi giờ của máy SQL Server.
-- Chỉ thay mặc định cho dữ liệu mới. Dữ liệu cũ có thể chứa cả giờ UTC lẫn giờ địa phương.
SET XACT_ABORT ON;
BEGIN TRANSACTION;

DECLARE @schema sysname, @table sysname, @column sysname, @constraint sysname, @sql nvarchar(max);
DECLARE time_defaults CURSOR LOCAL STATIC READ_ONLY FOR
    SELECT s.name, t.name, c.name, d.name
    FROM sys.default_constraints d
    JOIN sys.tables t ON d.parent_object_id = t.object_id
    JOIN sys.schemas s ON t.schema_id = s.schema_id
    JOIN sys.columns c ON c.object_id = t.object_id AND c.column_id = d.parent_column_id
    WHERE s.name = N'dbo' AND c.system_type_id = TYPE_ID(N'datetime2')
        AND REPLACE(REPLACE(REPLACE(LOWER(d.definition), N'(', N''), N')', N''), N' ', N'')
            IN (N'getdate', N'sysdatetime');

OPEN time_defaults;
FETCH NEXT FROM time_defaults INTO @schema, @table, @column, @constraint;
WHILE @@FETCH_STATUS = 0
BEGIN
    SET @sql = N'ALTER TABLE ' + QUOTENAME(@schema) + N'.' + QUOTENAME(@table)
        + N' DROP CONSTRAINT ' + QUOTENAME(@constraint) + N'; ALTER TABLE '
        + QUOTENAME(@schema) + N'.' + QUOTENAME(@table) + N' ADD CONSTRAINT ' + QUOTENAME(@constraint)
        + N' DEFAULT (DATEADD(MINUTE, 420, SYSUTCDATETIME())) FOR ' + QUOTENAME(@column) + N';';
    EXEC sys.sp_executesql @sql;
    FETCH NEXT FROM time_defaults INTO @schema, @table, @column, @constraint;
END;
CLOSE time_defaults;
DEALLOCATE time_defaults;
COMMIT TRANSACTION;
GO
