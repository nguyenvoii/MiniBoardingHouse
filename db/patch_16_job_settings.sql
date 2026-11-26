SET XACT_ABORT ON;

-- UC120: lich chay cua cac tac vu tu dong, chinh duoc tren giao dien (ghi de gia tri mac dinh trong cau hinh).
IF OBJECT_ID(N'dbo.job_settings', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.job_settings (
        job_name    NVARCHAR(60)  NOT NULL CONSTRAINT PK_job_settings PRIMARY KEY,
        cron        NVARCHAR(60)  NOT NULL,
        enabled     BIT           NOT NULL CONSTRAINT DF_job_settings_enabled DEFAULT 1,
        updated_by  INT           NULL,
        updated_at  DATETIME2     NOT NULL CONSTRAINT DF_job_settings_updated DEFAULT SYSDATETIME()
    );
END;
