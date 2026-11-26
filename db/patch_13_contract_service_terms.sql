IF COL_LENGTH(N'dbo.rental_contracts', N'service_terms') IS NULL
    ALTER TABLE dbo.rental_contracts ADD service_terms NVARCHAR(255) NULL;
