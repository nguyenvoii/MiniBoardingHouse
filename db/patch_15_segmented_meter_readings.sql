SET XACT_ABORT ON;

IF NOT EXISTS (
    SELECT 1 FROM sys.key_constraints
    WHERE parent_object_id = OBJECT_ID(N'dbo.meter_readings')
      AND name = N'UQ_period_room_contract_reading'
)
BEGIN
    BEGIN TRANSACTION;
    ALTER TABLE dbo.meter_readings ADD CONSTRAINT UQ_period_room_contract_reading
        UNIQUE (period_id, room_id, closing_contract_id);
    ALTER TABLE dbo.meter_readings DROP CONSTRAINT UQ_period_room_reading;
    COMMIT TRANSACTION;
END;
