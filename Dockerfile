# ---- Tầng 1: build giao diện (Vite ghi vào ../src/main/resources/static) ----
FROM node:20-alpine AS fe
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# ---- Tầng 2: build backend (nhúng luôn giao diện vào JAR) ----
FROM maven:3.9-eclipse-temurin-17 AS be
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
COPY --from=fe /app/src/main/resources/static ./src/main/resources/static
RUN mvn -B -q -DskipTests package

# ---- Tầng 3: image chạy thật ----
FROM eclipse-temurin:17-jre
# Font DejaVu để xuất PDF có dấu tiếng Việt (TableExporter tự tìm /usr/share/fonts/truetype/dejavu)
RUN apt-get update && apt-get install -y --no-install-recommends fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=be /app/target/*.jar app.jar

# Cấu hình KHÔNG phải bí mật. Bí mật (mật khẩu DB, JWT secret...) do quản trị CapRover đặt, KHÔNG ghi vào đây.
ENV APP_SECURITY_ENABLED=true \
    APP_NOTIFICATION_LOG_OTP=false \
    APP_UPLOAD_DIR=/data/uploads \
    SERVER_FORWARD_HEADERS_STRATEGY=framework \
    SPRINGDOC_SWAGGER_UI_ENABLED=false \
    SPRINGDOC_API_DOCS_ENABLED=false

EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","app.jar"]
