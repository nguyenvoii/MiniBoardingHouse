# Sao lưu DB gốc TRƯỚC khi demo (chạy 1 lần sau khi đã nạp demo-thuyet-trinh.sql). Đặt lại sau demo: dat-lai-sau-demo.ps1
$sqlcmd = (Get-Command sqlcmd.exe -ErrorAction SilentlyContinue).Source
if (-not $sqlcmd) { $sqlcmd = 'C:\Program Files\Microsoft SQL Server\Client SDK\ODBC\180\Tools\Binn\SQLCMD.EXE' }
$dir = (& $sqlcmd -S localhost -E -C -h -1 -W -Q "SET NOCOUNT ON; SELECT CAST(SERVERPROPERTY('InstanceDefaultBackupPath') AS NVARCHAR(400))").Trim()
$file = Join-Path $dir 'MBH_truoc_demo.bak'
& $sqlcmd -S localhost -E -C -b -Q "BACKUP DATABASE [MiniBoardingHouseDB] TO DISK = N'$file' WITH INIT, FORMAT, STATS = 25"
if ($LASTEXITCODE -ne 0) { Write-Host 'LOI: sao luu that bai'; exit 1 }
Write-Host "Da sao luu: $file"
