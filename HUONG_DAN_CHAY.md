HUONG DAN CHAY PROJECT MINIBOARDINGHOUSE (NHOM 7)

File nay danh cho cac thanh vien trong nhom. Lam lan luot tu tren xuong duoi nhe.


YEU CAU CAI DAT

- JDK 17
- SQL Server (ban Developer hoac Express deu duoc) va SQL Server Management Studio (SSMS)
- VS Code voi 2 extension: "Extension Pack for Java" va "Spring Boot Extension Pack"
- Khong can cai Maven rieng, VS Code tu lo.


BUOC 1: TAO DATABASE

1. Mo SSMS, ket noi: Server name = localhost, Authentication = Windows Authentication, tick "Trust server certificate".
2. Mo file db/group-7.sql, bam Execute (F5).
3. Kiem tra: trong Databases co MiniBoardingHouseDB va co 56 bang.


BUOC 2: BAT TCP/IP CHO SQL SERVER

Spring Boot ket noi SQL Server qua TCP/IP, nhung nhieu may mac dinh tat. Neu ban chay app ma bao loi "The TCP/IP connection to the host localhost, port 1433 has failed" thi can bat no:

1. Mo SQL Server Configuration Manager.
2. SQL Server Network Configuration > Protocols for MSSQLSERVER > TCP/IP > Enable.
3. SQL Server Services > bam phai SQL Server (MSSQLSERVER) > Restart.

Neu Configuration Manager bao "There are no items to show in this view", ban co the bat bang SQL: chay lenh sau trong SSMS, roi restart service MSSQLSERVER trong Services (services.msc):

EXEC xp_instance_regwrite N'HKEY_LOCAL_MACHINE', N'Software\Microsoft\Microsoft SQL Server\MSSQLServer\SuperSocketNetLib\Tcp', N'Enabled', REG_DWORD, 1;

Neu may ban co nhieu instance SQL Server, hay dung instance mac dinh (MSSQLSERVER) de port la 1433.


BUOC 3: TAO TAI KHOAN DANG NHAP CHO APP

App dang nhap bang tai khoan SQL (user + password). Trong SSMS, mo New Query va chay (doi mat khau theo y ban):

CREATE LOGIN mbh_app WITH PASSWORD = 'MatKhauCuaBan@123', CHECK_POLICY = OFF;
USE MiniBoardingHouseDB;
CREATE USER mbh_app FOR LOGIN mbh_app;
ALTER ROLE db_owner ADD MEMBER mbh_app;

Neu da co san tai khoan sa va biet mat khau thi co the dung luon, bo qua buoc nay.


BUOC 4: TAO FILE CAU HINH CA NHAN

Tao file src/main/resources/application-local.properties voi noi dung:

spring.datasource.username=mbh_app
spring.datasource.password=MatKhauCuaBan@123

Luu y: file nay da nam trong .gitignore, KHONG commit len git. Moi nguoi tu tao file cua minh tren may minh.


BUOC 5: CHAY APP TRONG VS CODE

1. File > Open Folder, chon thu muc MiniBoardingHouse (mo ca thu muc, khong mo tung file le).
2. Doi goc duoi ben phai het chu "Java: Building / Importing". Lan dau co the mat vai phut vi phai tai thu vien.
3. Mo file src/main/java/com/group7/minihouse/MiniBoardingHouseApplication.java.
4. Bam chu "Run" nho hien ngay tren ham main. Hoac dung nut play trong tab Spring Boot Dashboard.
5. Thanh cong khi log cuoi cung co dong: Started MiniBoardingHouseApplication
6. Mo trinh duyet vao http://localhost:8080

Hien tai project chua co controller nen trang se bao 404 (Whitelabel Error Page). Day la binh thuong, khong phai loi.


CAC LOI THUONG GAP

1. "package org.springframework.boot does not exist" hoac "mvn is not recognized"
   Ban dang bam nut tam giac cua extension Code Runner (goc tren ben phai). Hay tat Code Runner (Extensions > Code Runner > Disable) va chay bang nut Run cua Java nhu Buoc 5.

2. "Port 8080 was already in use"
   Co mot ban cua app dang chay san. Tat terminal cu dang chay app, hoac mo Task Manager tat tien trinh java roi chay lai.

3. "The TCP/IP connection to the host localhost, port 1433 has failed"
   Chua bat TCP/IP, lam lai Buoc 2.

4. "Login failed for user"
   Sai user hoac password trong application-local.properties, hoac chua tao login o Buoc 3.

5. Loi Hibernate bao "missing table" / "wrong column type" luc khoi dong
   Do cau hinh ddl-auto=validate: entity khong khop voi DB. Kiem tra lai entity vua viet hoac chay lai db/group-7.sql tren DB moi.


GHI CHU

- Neu muon chay bang dong lenh thay vi VS Code thi can cai Maven roi chay: mvn spring-boot:run
- Neu sua file db/group-7.sql, hay bao ca nhom de cung cap nhat DB.
