# MiniBoardingHouse (Group 7)

Website quan ly nha tro mini. Java 17, Spring Boot 3, Spring Data JPA, SQL Server.

## Cau truc package (com.group7.minihouse)
- entity      : 56 class = 56 bang trong db/group-7.sql (+ UserRoleId la khoa phuc hop, khong phai entity)
- repository  : Spring Data JPA repository
- service     : nghiep vu
- controller  : REST / web controller
- dto         : request/response object
- config      : cau hinh (security, web...)
- util        : ham tien ich
- exception   : exception tuy chinh

## Chay
1. Chay db/group-7.sql tren SQL Server.
2. Tao src/main/resources/application-local.properties (file nay bi git ignore):
   spring.datasource.username=...
   spring.datasource.password=...
3. mvn spring-boot:run  (ddl-auto=validate se bao loi neu entity lech DB)

## Quy uoc lam chung
- Moi thay doi DB phai sua db/group-7.sql VA entity tuong ung trong cung 1 commit.
- So class trong package entity phai bang so bang trong DB.
- Pull truoc khi push, lam tren branch rieng roi tao Pull Request vao main.
