# Đặt lại DB về đúng lúc sao lưu (xóa sạch những gì vừa demo: chị An, hợp đồng, hóa đơn mới...).
# TẮT ỨNG DỤNG (Ctrl+C ở cửa sổ mvn) trước khi chạy file này, rồi bật lại app.
$sqlcmd = (Get-Command sqlcmd.exe -ErrorAction SilentlyContinue).Source
if (-not $sqlcmd) { $sqlcmd = 'C:\Program Files\Microsoft SQL Server\Client SDK\ODBC\180\Tools\Binn\SQLCMD.EXE' }
$dir = (& $sqlcmd -S localhost -E -C -h -1 -W -Q "SET NOCOUNT ON; SELECT CAST(SERVERPROPERTY('InstanceDefaultBackupPath') AS NVARCHAR(400))").Trim()
$file = Join-Path $dir 'MBH_truoc_demo.bak'
$sql = "USE master; ALTER DATABASE [MiniBoardingHouseDB] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; RESTORE DATABASE [MiniBoardingHouseDB] FROM DISK = N'$file' WITH REPLACE, STATS = 25; ALTER DATABASE [MiniBoardingHouseDB] SET MULTI_USER;"
& $sqlcmd -S localhost -E -C -b -Q $sql
if ($LASTEXITCODE -ne 0) { Write-Host 'LOI: dat lai that bai (chua sao luu truoc-demo, hoac app con chay?)'; exit 1 }
Write-Host 'Da dat lai DB ve luc sao luu. Bat lai app roi demo tiep.'
