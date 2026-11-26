SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF COL_LENGTH(N'dbo.tenants', N'residence_start_date') IS NULL
    ALTER TABLE dbo.tenants ADD residence_start_date DATE NULL;
IF COL_LENGTH(N'dbo.tenants', N'residence_end_date') IS NULL
    ALTER TABLE dbo.tenants ADD residence_end_date DATE NULL;

IF OBJECT_ID(N'dbo.CK_tenants_residence_dates', N'C') IS NULL
    EXEC(N'ALTER TABLE dbo.tenants WITH CHECK ADD CONSTRAINT CK_tenants_residence_dates
        CHECK (residence_end_date IS NULL
            OR (residence_start_date IS NOT NULL AND residence_end_date >= residence_start_date))');

COMMIT TRANSACTION;
