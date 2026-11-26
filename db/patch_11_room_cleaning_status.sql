SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.CK_rooms_status', N'C') IS NULL
BEGIN
    DECLARE @legacy SYSNAME;
    SELECT @legacy = name
    FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID(N'dbo.rooms')
      AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'dbo.rooms'), N'status', 'ColumnId')
      AND definition LIKE N'%REPAIRING%' AND definition LIKE N'%VACANT%';

    IF @legacy IS NOT NULL
    BEGIN
        DECLARE @drop NVARCHAR(300) = N'ALTER TABLE dbo.rooms DROP CONSTRAINT ' + QUOTENAME(@legacy);
        EXEC sys.sp_executesql @drop;
    END;

    ALTER TABLE dbo.rooms WITH CHECK ADD CONSTRAINT CK_rooms_status
        CHECK (status IN ('VACANT', 'RENTED', 'REPAIRING', 'CLEANING', 'SUSPENDED'));
END;

COMMIT TRANSACTION;
