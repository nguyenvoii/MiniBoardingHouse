package com.group7.minihouse.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.group7.minihouse.MiniBoardingHouseApplication;
import com.group7.minihouse.phi.dto.PaymentDtos.ConfirmRequest;
import com.group7.minihouse.phi.service.PaymentService;
import com.group7.minihouse.shared.exception.BusinessException;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SqlServerIT {
    private static final String QA_PASSWORD = "SqlIntegrationPassword42!";
    private final String database = "StaylyIT_" + UUID.randomUUID().toString().replace("-", "");
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    @TempDir static Path uploads;
    private ServletWebServerApplicationContext context;
    private MSSQLServerContainer sqlServer;
    private JdbcTemplate db;
    private boolean created;
    private String username;
    private String password;
    private int port;

    @BeforeAll
    void startFromSqlServerSchema() throws Exception {
        if (System.getenv("STAYLY_SQL_TEST_USER") == null) {
            sqlServer = new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-CU20-ubuntu-22.04")
                    .acceptLicense().withPassword("StaylyIT!_" + UUID.randomUUID());
            sqlServer.start();
            username = sqlServer.getUsername();
            password = sqlServer.getPassword();
            port = sqlServer.getMappedPort(1433);
        } else {
            username = requiredEnv("STAYLY_SQL_TEST_USER");
            password = requiredEnv("STAYLY_SQL_TEST_PASSWORD");
            port = Integer.parseInt(System.getenv().getOrDefault("STAYLY_SQL_TEST_PORT", "1433"));
        }
        try (var con = waitForSqlServer(); var stmt = con.createStatement()) {
            stmt.execute("CREATE DATABASE [" + database + "]");
            created = true;
            runSqlScript(stmt, "minihouse-full.sql", database);
        }

        context = (ServletWebServerApplicationContext) new SpringApplicationBuilder(MiniBoardingHouseApplication.class).run(
                "--server.port=0", "--spring.profiles.active=integration",
                "--spring.datasource.url=" + url(database),
                "--spring.datasource.username=" + username,
                "--spring.datasource.password=" + password,
                "--spring.jpa.hibernate.ddl-auto=validate", "--spring.jpa.show-sql=false",
                "--app.jwt.secret=" + UUID.randomUUID(), "--app.security.enabled=true",
                "--app.upload.dir=" + uploads, "--app.jobs.enabled=false", "--app.backup.enabled=false",
                "--app.mail.enabled=false", "--app.sms.enabled=false", "--app.payment.payos.enabled=false");
        db = context.getBean(JdbcTemplate.class);
    }

    @Test
    void productionSeedCreatesOnlyTheExplicitOwnerAndSampleImportSkipsRotatedCredentials() throws Exception {
        String freshDb = database + "_seed";
        try (var con = connect("master"); var stmt = con.createStatement()) {
            stmt.execute("CREATE DATABASE [" + freshDb + "]");
            try {
                runSqlScript(stmt, "group-7.sql", freshDb);
                stmt.execute("EXEC sys.sp_set_session_context N'stayly_skip_demo_owner', 1");
                runSqlScript(stmt, "seed.sql", freshDb);
                assertEquals(0, rowCount(stmt, "SELECT COUNT(*) FROM dbo.users"));
                assertEquals(1, rowCount(stmt, "SELECT COUNT(*) FROM dbo.roles WHERE role_code = 'LANDLORD'"));

                setSessionValue(con, "stayly_owner_database", freshDb);
                setSessionValue(con, "stayly_owner_email", "owner-bootstrap@example.test");
                setSessionValue(con, "stayly_owner_name", "QA Owner");
                setSessionValue(con, "stayly_owner_bcrypt", context.getBean(PasswordEncoder.class).encode(QA_PASSWORD));
                runSqlScript(stmt, "provision-owner.sql", freshDb);
                runSqlScript(stmt, "seed.sql", freshDb);
                assertEquals(1, rowCount(stmt, "SELECT COUNT(*) FROM dbo.users"));
                assertEquals(1, rowCount(stmt, """
                        SELECT COUNT(*) FROM dbo.users u
                        JOIN dbo.user_roles ur ON ur.user_id = u.user_id
                        JOIN dbo.roles r ON r.role_id = ur.role_id
                        WHERE u.email = 'owner-bootstrap@example.test' AND r.role_code = 'LANDLORD'
                        """));

                // Simulate an account in an otherwise empty QA database whose demo password has been rotated.
                stmt.execute("EXEC sys.sp_set_session_context N'stayly_skip_demo_owner', NULL");
                stmt.execute("UPDATE dbo.users SET email = 'admin@minihouse.local'");
                runSqlScript(stmt, "sample-data-test.sql", freshDb);
                assertEquals(1, rowCount(stmt, "SELECT COUNT(*) FROM dbo.users"));
                assertEquals(0, rowCount(stmt, "SELECT COUNT(*) FROM dbo.properties"));
            } finally {
                stmt.execute("USE [master]");
                stmt.execute("DROP DATABASE [" + freshDb + "]");
            }
        }
    }

    private static void runSqlScript(Statement stmt, String scriptName, String targetDb) throws IOException, SQLException {
        String script = Files.readString(Path.of("db", scriptName)).replace("\uFEFF", "")
                .replace("MiniBoardingHouseDB", targetDb);
        for (String batch : script.split("(?im)^\\s*GO\\s*$")) {
            if (!batch.isBlank()) stmt.execute(batch);
        }
    }

    private static void setSessionValue(Connection con, String key, String value) throws SQLException {
        try (var stmt = con.prepareStatement("EXEC sys.sp_set_session_context @key = ?, @value = ?")) {
            stmt.setNString(1, key);
            stmt.setNString(2, value);
            stmt.execute();
        }
    }

    private static int rowCount(Statement stmt, String sql) throws SQLException {
        try (var rows = stmt.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    @AfterAll
    void removeIntegrationDatabase() throws SQLException {
        try {
            if (context != null) context.close();
            if (created && sqlServer == null) {
                try (var con = connect("master"); var stmt = con.createStatement()) {
                    stmt.execute("DROP DATABASE [" + database + "]");
                }
            }
        } finally {
            if (sqlServer != null) sqlServer.stop();
        }
    }

    @Test
    void sqlDefaultsAndBusinessClockUseVietnamTime() throws IOException {
        assertEquals("Asia/Ho_Chi_Minh", context.getBean(java.time.Clock.class).getZone().getId());
        Integer id = createStaff("LANDLORD");
        Long offset = db.queryForObject("""
                SELECT DATEDIFF(MINUTE, SYSUTCDATETIME(), created_at) FROM users WHERE user_id = ?
                """, Long.class, id);
        assertTrue(offset >= 419 && offset <= 420, "SQL default offset: " + offset);

        db.execute("CREATE TABLE qa_legacy_time (created_at DATETIME2 DEFAULT GETDATE())");
        String migration = Files.readString(Path.of("db/patch_21_business_time_defaults.sql"))
                .replaceFirst("(?im)^\\s*GO\\s*$", "");
        db.execute(migration);
        db.execute(migration);
        db.execute("INSERT INTO qa_legacy_time DEFAULT VALUES");
        Long migratedOffset = db.queryForObject(
                "SELECT DATEDIFF(MINUTE, SYSUTCDATETIME(), created_at) FROM qa_legacy_time", Long.class);
        assertTrue(migratedOffset >= 419 && migratedOffset <= 420, "Migrated SQL default offset: " + migratedOffset);
    }

    @Test
    void roleSpecificRoutesAllowOnlyPermittedAccess() throws Exception {
        String owner = login(createStaff("LANDLORD"));
        String manager = login(createStaff("MANAGER"));
        String cashier = login(createStaff("CASHIER"));
        String meter = login(createStaff("METER_READER"));
        assertEquals(200, get("/api/meter-periods?propertyId=1", meter).statusCode());
        assertEquals(200, get("/api/chi/rooms/floor/1", meter).statusCode());
        assertEquals(200, get("/api/notifications/unread-count", meter).statusCode());
        assertEquals(200, get("/api/payments", cashier).statusCode());
        assertEquals(200, get("/api/invoices/1/partial-payments", cashier).statusCode());
        assertEquals(200, get("/api/invoices/summary/2026-11?propertyId=1", cashier).statusCode());
        assertEquals(403, get("/api/invoices/internal-preview", cashier).statusCode());
        assertEquals(200, get("/api/contracts?size=1", manager).statusCode());
        assertEquals(403, get("/api/payments", meter).statusCode());
        assertEquals(403, get("/api/rooms/1/residents", meter).statusCode());
        assertEquals(403, get("/api/tenants/1/vehicles", meter).statusCode());
        assertEquals(403, get("/api/contracts?size=1", cashier).statusCode());
        assertEquals(403, get("/api/admin/backups/availability", manager).statusCode());
        assertEquals(Boolean.FALSE, JsonPath.read(get("/api/admin/backups/availability", owner).body(), "$.data"));
        assertEquals(404, get("/api/admin/backups", owner).statusCode());
        assertEquals(403, post("/api/auth/register", meter, "{}").statusCode());
        assertEquals(401, get("/api/contracts/signatures/unavailable.png", null).statusCode());
        assertEquals(401, get("/api/auth/avatars/unavailable.png", null).statusCode());
        assertEquals(401, get("/api/bank-accounts/qr/unavailable.png", null).statusCode());
    }

    @Test
    void passwordChangesRevokeOldSessionsAndLogoutRevokesOnlyTheActiveSession() throws Exception {
        int id = createStaff("LANDLORD");
        db.update("UPDATE users SET must_change_password = 1 WHERE user_id = ?", id);
        String old = login(id);
        String anotherOld = login(id);
        assertEquals(200, get("/api/auth/me", old).statusCode());
        assertEquals(403, get("/api/auth/profile", old).statusCode());
        assertEquals(403, get("/api/payments", old).statusCode());

        var change = post("/api/auth/password", old, """
                {"currentPassword":"SqlIntegrationPassword42!","newPassword":"ChangedPassword43!"}
                """);
        assertEquals(200, change.statusCode());
        String renewed = JsonPath.read(change.body(), "$.data.accessToken");
        assertEquals(401, get("/api/auth/me", old).statusCode());
        assertEquals(401, get("/api/auth/me", anotherOld).statusCode());
        assertEquals(200, get("/api/auth/profile", renewed).statusCode());
        String anotherNew = login(id, "ChangedPassword43!");
        assertEquals(200, post("/api/auth/logout", renewed, "{}").statusCode());
        assertEquals(401, get("/api/auth/me", renewed).statusCode());
        assertEquals(200, get("/api/auth/me", anotherNew).statusCode());
    }

    @Test
    void signaturesArePrivateAndInvoiceLinksExpire() throws Exception {
        var tenants = db.queryForList("""
                SELECT TOP 2 t.tenant_id FROM tenants t
                WHERE EXISTS (SELECT 1 FROM rental_contracts c WHERE c.tenant_id = t.tenant_id)
                  AND NOT EXISTS (SELECT 1 FROM users u WHERE u.phone_number = t.phone_number)
                ORDER BY t.tenant_id
                """, Integer.class);
        String firstTenant = login(createTenantAccount(tenants.get(0)));
        String secondTenant = login(createTenantAccount(tenants.get(1)));
        String owner = login(createStaff("LANDLORD"));
        String fileName = UUID.randomUUID() + ".png";
        Files.createDirectories(uploads.resolve("signatures"));
        Files.write(uploads.resolve("signatures").resolve(fileName), new byte[] {1, 2, 3});
        String signature = "/api/contracts/signatures/" + fileName;
        db.update("""
                UPDATE rental_contracts SET digital_signature_url = ? WHERE contract_id =
                    (SELECT MIN(contract_id) FROM rental_contracts WHERE tenant_id = ?)
                """, signature, tenants.get(0));
        assertEquals(200, get(signature, owner).statusCode());
        assertEquals(200, get(signature, firstTenant).statusCode());
        assertEquals(404, get(signature, secondTenant).statusCode());

        Integer invoice = db.queryForObject("SELECT MIN(invoice_id) FROM invoices WHERE payment_status = 'UNPAID'", Integer.class);
        String create = "/api/invoices/" + invoice + "/share-link";
        var result = post(create, owner, "{}");
        assertEquals(200, result.statusCode());
        String link = JsonPath.read(result.body(), "$.shareLink");
        assertEquals(200, get(link, null).statusCode());
        db.update("UPDATE invoices SET share_expires_at = 1 WHERE invoice_id = ?", invoice);
        assertEquals(404, get(link, null).statusCode());
        String replacement = JsonPath.read(post(create, owner, "{}").body(), "$.shareLink");
        assertFalse(link.equals(replacement));
        assertEquals(200, get(replacement, null).statusCode());
    }

    @Test
    void concurrentConfirmationsCreditAnInvoiceOnce() throws Exception {
        int owner = createStaff("LANDLORD");
        int invoice = db.queryForObject("""
                SELECT TOP 1 invoice_id FROM invoices
                WHERE paid_amount = 0 AND payment_status IN ('UNPAID', 'OVERDUE') AND total_amount > 1
                ORDER BY invoice_id
                """, Integer.class);
        int transaction = db.queryForObject("""
                INSERT INTO payment_transactions (invoice_id, payment_method, transaction_code, amount_paid,
                    received_by_user_id, status)
                OUTPUT inserted.transaction_id VALUES (?, 'QR_CODE', ?, 1, ?, 'FAILED')
                """, Integer.class, invoice, "QA-" + UUID.randomUUID(), owner);

        var service = context.getBean(PaymentService.class);
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> confirm = () -> {
                ready.countDown();
                if (!go.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Confirmation start timeout");
                try {
                    return service.confirm(transaction, new ConfirmRequest(BigDecimal.ONE), owner, "127.0.0.1").confirmed();
                } catch (BusinessException e) {
                    assertEquals(HttpStatus.CONFLICT, e.getStatus());
                    return false;
                }
            };
            var first = workers.submit(confirm);
            var second = workers.submit(confirm);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            go.countDown();
            assertTrue(first.get(20, TimeUnit.SECONDS) ^ second.get(20, TimeUnit.SECONDS));
        } finally {
            workers.shutdownNow();
        }
        assertEquals(0, BigDecimal.ONE.compareTo(db.queryForObject(
                "SELECT paid_amount FROM invoices WHERE invoice_id = ?", BigDecimal.class, invoice)));
        assertEquals("SUCCESS", db.queryForObject("SELECT status FROM payment_transactions WHERE transaction_id = ?",
                String.class, transaction));
    }

    private Integer createStaff(String role) {
        String email = UUID.randomUUID() + "@example.test";
        String hash = context.getBean(PasswordEncoder.class).encode(QA_PASSWORD);
        Integer id = db.queryForObject("""
                INSERT INTO users (email, password_hash, full_name, status)
                OUTPUT inserted.user_id VALUES (?, ?, 'Integration Test', 'ACTIVE')
                """, Integer.class, email, hash);
        db.update("INSERT INTO user_roles (user_id, role_id) SELECT ?, role_id FROM roles WHERE role_code = ?", id, role);
        return id;
    }

    private Integer createTenantAccount(Integer tenantId) {
        Integer id = createStaff("TENANT");
        db.update("UPDATE users SET phone_number = (SELECT phone_number FROM tenants WHERE tenant_id = ?) WHERE user_id = ?",
                tenantId, id);
        return id;
    }

    private String login(Integer id) throws Exception {
        return login(id, QA_PASSWORD);
    }

    private String login(Integer id, String password) throws Exception {
        String email = db.queryForObject("SELECT email FROM users WHERE user_id = ?", String.class, id);
        var res = post("/api/auth/login", null, "{\"identifier\":\"" + email + "\",\"password\":\"" + password + "\"}");
        assertEquals(200, res.statusCode(), res.body());
        String token = JsonPath.read(res.body(), "$.data.accessToken");
        assertFalse(token.isBlank());
        return token;
    }

    private HttpResponse<String> get(String path, String token) throws IOException, InterruptedException {
        return client.send(request(path, token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String token, String body) throws IOException, InterruptedException {
        var req = request(path, token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder request(String path, String token) {
        int port = context.getWebServer().getPort();
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).header("Accept", "application/json");
        return token == null ? request : request.header("Authorization", "Bearer " + token);
    }

    private Connection waitForSqlServer() throws SQLException, InterruptedException {
        SQLException last = null;
        for (int attempt = 0; attempt < 15; attempt++) {
            try {
                return connect("master");
            } catch (SQLException e) {
                last = e;
                Thread.sleep(2_000);
            }
        }
        throw last;
    }

    private Connection connect(String database) throws SQLException {
        return DriverManager.getConnection(url(database), username, password);
    }

    private String url(String database) {
        return "jdbc:sqlserver://127.0.0.1:" + port + ";databaseName=" + database
                + ";encrypt=true;trustServerCertificate=true;loginTimeout=5";
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Environment variable required: " + name);
        return value;
    }
}
