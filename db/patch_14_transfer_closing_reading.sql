IF COL_LENGTH(N'dbo.meter_readings', N'closing_contract_id') IS NULL
    ALTER TABLE dbo.meter_readings ADD closing_contract_id INT NULL
        CONSTRAINT FK_meter_readings_closing_contract REFERENCES dbo.rental_contracts(contract_id);
