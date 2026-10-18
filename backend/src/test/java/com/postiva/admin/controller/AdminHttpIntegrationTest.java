package com.postiva.admin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.postiva.scheduler.PublishingScheduler;
import com.postiva.admin.security.Totp;
import com.postiva.user.entity.User;
import com.postiva.user.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "POSTIVA_TEST_DB_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=", "server.address=127.0.0.1", "postiva.mail.enabled=false",
        "postiva.openai.api-key=", "postiva.storage.upload-dir=target/admin-http-uploads",
        "app.jwt.secret=only-an-isolated-admin-http-test-signing-secret",
        "postiva.security.mfa-encryption-key=only-an-isolated-mfa-test-encryption-key-123",
        "postiva.admin.bootstrap.enabled=true", "postiva.admin.bootstrap.email=admin-http@example.test",
        "postiva.admin.bootstrap.password=only-for-isolated-http-tests-123",
        "postiva.admin.bootstrap.full-name=HTTP Test Admin",
        "postiva.security.login-attempts-per-15-minutes=100"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminHttpIntegrationTest {
    private static final String SCHEMA = "admin_http_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final String URL = System.getenv("POSTIVA_TEST_DB_URL");
    private static final String USERNAME = System.getenv().getOrDefault("POSTIVA_TEST_DB_USERNAME", "postgres");
    private static final String PASSWORD = System.getenv().getOrDefault("POSTIVA_TEST_DB_PASSWORD", "");
    private static final String FIXTURE_PASSWORD = "only-for-isolated-http-tests-123";
    private static final String ROTATED_PASSWORD = "a-new-isolated-admin-password-456";
    @LocalServerPort private int port;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwords;
    @Autowired private ObjectMapper json;
    @Autowired private Totp totp;
    @MockBean private PublishingScheduler scheduler;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        if (URL == null || !URL.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/postiva_migration_test(\\?.*)?")) {
            throw new IllegalStateException("HTTP tests require the dedicated local postiva_migration_test database");
        }
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.datasource.username", () -> USERNAME);
        registry.add("spring.datasource.password", () -> PASSWORD);
        registry.add("spring.datasource.hikari.schema", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
    }

    @AfterAll
    static void cleanup() throws Exception {
        try (var connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    @Test
    void actualBootstrapLoginAuthorizationStatusAndAuditFlow() throws Exception {
        User target = new User();
        target.setEmail("user-http@example.test");
        target.setFullName("HTTP Test User");
        target.setPasswordHash(passwords.encode(FIXTURE_PASSWORD));
        target = users.save(target);
        String path = "/api/admin/users/" + target.getId();

        assertEquals(401, request("GET", "/api/admin/users", null, null).status());
        assertEquals(401, request("GET", "/api/admin/analytics", null, null).status());
        String userToken = login(target.getEmail());
        assertEquals(403, request("GET", "/api/admin/users", userToken, null).status());
        assertEquals(403, request("GET", "/api/admin/analytics", userToken, null).status());
        String adminToken = login("admin-http@example.test");
        assertEquals(200, request("GET", "/api/auth/me", adminToken, null).status());
        assertTrue(request("GET", "/api/auth/me", adminToken, null).body().path("mfaSetupRequired").asBoolean());
        assertEquals(403, request("GET", "/api/admin/users", adminToken, null).status());
        assertEquals(403, request("GET", "/api/admin/analytics", adminToken, null).status());
        assertEquals(403, request("POST", "/api/admin/security/mfa/setup", userToken,
                Map.of("currentPassword", FIXTURE_PASSWORD)).status());
        Result setup = request("POST", "/api/admin/security/mfa/setup", adminToken,
                Map.of("currentPassword", FIXTURE_PASSWORD));
        assertEquals(200, setup.status());
        String secret = setup.body().path("data").path("secret").asText();
        String initialCode = totp.code(secret, Instant.now().getEpochSecond() / 30);
        Result enabled = request("POST", "/api/admin/security/mfa/confirm", adminToken, Map.of("code", initialCode));
        assertEquals(200, enabled.status());
        List<String> recovery = json.convertValue(enabled.body().path("data").path("recoveryCodes"),
                new com.fasterxml.jackson.core.type.TypeReference<List<String>>() { });
        assertEquals(10, recovery.size());
        assertEquals(401, request("GET", "/api/auth/me", adminToken, null).status());
        assertEquals(428, request("POST", "/api/auth/login", null,
                Map.of("email", "admin-http@example.test", "password", FIXTURE_PASSWORD)).status());
        assertEquals(401, request("POST", "/api/auth/login", null,
                Map.of("email", "admin-http@example.test", "password", FIXTURE_PASSWORD, "mfaCode", initialCode)).status());
        var first = CompletableFuture.supplyAsync(() -> loginAttempt("admin-http@example.test", FIXTURE_PASSWORD, recovery.get(0)));
        var second = CompletableFuture.supplyAsync(() -> loginAttempt("admin-http@example.test", FIXTURE_PASSWORD, recovery.get(0)));
        List<Result> concurrent = List.of(first.join(), second.join());
        assertEquals(List.of(200, 401), concurrent.stream().map(Result::status).sorted().toList());
        adminToken = concurrent.stream().filter(result -> result.status() == 200).findFirst().orElseThrow().body().path("token").asText();
        UUID analyticsVisitor = UUID.randomUUID();
        UUID analyticsSession = UUID.randomUUID();
        assertEquals(204, request("POST", "/api/analytics/events", null, Map.of(
                "type", "PAGE_VIEW", "visitorId", analyticsVisitor.toString(),
                "sessionId", analyticsSession.toString(), "viewId", UUID.randomUUID().toString(),
                "path", "/", "source", "DIRECT")).status());
        Result analytics = request("GET", "/api/admin/analytics?days=7", adminToken, null);
        assertEquals(200, analytics.status());
        assertEquals(1, analytics.body().path("data").path("sessions").path("sessions").asInt());
        assertEquals(204, request("POST", "/api/analytics/events", adminToken, Map.of(
                "type", "EXCLUDE", "visitorId", analyticsVisitor.toString(),
                "sessionId", analyticsSession.toString())).status());
        assertEquals(0, request("GET", "/api/admin/analytics?days=7", adminToken, null)
                .body().path("data").path("sessions").path("sessions").asInt());
        assertEquals(401, request("POST", "/api/auth/login", null,
                Map.of("email", "admin-http@example.test", "password", FIXTURE_PASSWORD, "mfaCode", recovery.get(0))).status());
        assertEquals(403, request("GET", "/api/app-data/overview", adminToken, null).status());
        assertEquals(200, request("GET", "/api/app-data/overview", userToken, null).status());
        Result list = request("GET", "/api/admin/users?q=user-http&status=ACTIVE&page=0&size=1", adminToken, null);
        assertEquals(200, list.status());
        assertEquals(1, list.body().path("data").path("totalElements").asInt());
        assertFalse(list.body().path("data").path("items").get(0).has("passwordHash"));

        assertEquals(422, request("PATCH", path + "/lock", adminToken, Map.of("reason", " ")).status());
        Result locked = request("PATCH", path + "/lock", adminToken, Map.of("reason", "HTTP lock fixture"));
        assertEquals(200, locked.status());
        assertEquals("LOCKED", locked.body().path("data").path("status").asText());
        assertEquals(401, request("GET", "/api/auth/me", userToken, null).status());
        assertEquals(401, request("POST", "/api/auth/login", null,
                Map.of("email", target.getEmail(), "password", FIXTURE_PASSWORD)).status());

        Result history = request("GET", path + "/audit", adminToken, null);
        assertEquals(200, history.status());
        assertEquals(1, history.body().path("data").path("totalElements").asInt());
        assertEquals("HTTP Test Admin", history.body().path("data").path("items").get(0).path("actorName").asText());
        assertEquals("HTTP lock fixture", request("GET", path, adminToken, null)
                .body().path("data").path("lockReason").asText());

        assertEquals(200, request("PATCH", path + "/unlock", adminToken, Map.of("reason", "HTTP unlock fixture")).status());
        login(target.getEmail());
        assertEquals(200, request("PATCH", path + "/unlock", adminToken, Map.of("reason", "Repeated request")).status());
        assertEquals(2, request("GET", path + "/audit", adminToken, null).body().path("data").path("totalElements").asInt());

        String changePath = "/api/admin/security/password";
        assertEquals(403, request("POST", changePath, userToken,
                Map.of("currentPassword", FIXTURE_PASSWORD, "newPassword", ROTATED_PASSWORD)).status());
        assertEquals(422, request("POST", changePath, adminToken,
                Map.of("currentPassword", "wrong-password", "newPassword", ROTATED_PASSWORD)).status());
        assertEquals(422, request("POST", changePath, adminToken,
                Map.of("currentPassword", FIXTURE_PASSWORD, "newPassword", FIXTURE_PASSWORD)).status());
        assertEquals(204, request("POST", changePath, adminToken,
                Map.of("currentPassword", FIXTURE_PASSWORD, "newPassword", ROTATED_PASSWORD)).status());
        assertEquals(401, request("GET", "/api/admin/users", adminToken, null).status());
        assertEquals(401, request("POST", "/api/auth/login", null,
                Map.of("email", "admin-http@example.test", "password", FIXTURE_PASSWORD)).status());
        String rotatedToken = login("admin-http@example.test", ROTATED_PASSWORD, recovery.get(1));
        assertEquals(200, request("GET", "/api/admin/users", rotatedToken, null).status());
        assertEquals(4, users.findByEmailIgnoreCase("admin-http@example.test").orElseThrow().getAuthVersion());
        try (var connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT count(*) FROM " + SCHEMA + ".admin_security_events WHERE action='PASSWORD_CHANGED'")) {
            assertTrue(result.next());
            assertEquals(1, result.getInt(1));
        }

        Result regenerated = request("POST", "/api/admin/security/mfa/recovery-codes", rotatedToken,
                Map.of("currentPassword", ROTATED_PASSWORD, "code", recovery.get(2)));
        assertEquals(200, regenerated.status());
        List<String> refreshed = json.convertValue(regenerated.body().path("data").path("recoveryCodes"),
                new com.fasterxml.jackson.core.type.TypeReference<List<String>>() { });
        assertEquals(401, request("GET", "/api/admin/users", rotatedToken, null).status());
        String refreshedToken = login("admin-http@example.test", ROTATED_PASSWORD, refreshed.get(0));
        assertEquals(401, request("POST", "/api/auth/login", null,
                Map.of("email", "admin-http@example.test", "password", ROTATED_PASSWORD, "mfaCode", recovery.get(3))).status());
        assertEquals(204, request("POST", "/api/admin/security/mfa/disable", refreshedToken,
                Map.of("currentPassword", ROTATED_PASSWORD, "code", refreshed.get(1))).status());
        assertEquals(401, request("GET", "/api/auth/me", refreshedToken, null).status());
        String restricted = login("admin-http@example.test", ROTATED_PASSWORD);
        assertEquals(403, request("GET", "/api/admin/users", restricted, null).status());
        Result newSetup = request("POST", "/api/admin/security/mfa/setup", restricted, Map.of("currentPassword", ROTATED_PASSWORD));
        String newSecret = newSetup.body().path("data").path("secret").asText();
        assertEquals(200, request("POST", "/api/admin/security/mfa/confirm", restricted,
                Map.of("code", totp.code(newSecret, Instant.now().getEpochSecond() / 30))).status());
        for (int i = 0; i < 5; i++) {
            assertEquals(401, request("POST", "/api/auth/login", null,
                    Map.of("email", "admin-http@example.test", "password", ROTATED_PASSWORD, "mfaCode", "invalid-code")).status());
        }
        assertEquals(401, request("POST", "/api/auth/login", null,
                Map.of("email", "admin-http@example.test", "password", ROTATED_PASSWORD, "mfaCode", "123456")).status());
        try (var connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT failed_attempts, blocked_until, secret_encrypted, recovery_hashes::text FROM " + SCHEMA + ".admin_mfa WHERE user_id=(SELECT id FROM " + SCHEMA + ".users WHERE email='admin-http@example.test')")) {
            assertTrue(result.next());
            assertEquals(5, result.getInt(1));
            assertNotNull(result.getTimestamp(2));
            assertFalse(result.getString(3).contains(newSecret));
            assertFalse(result.getString(4).contains(refreshed.get(0)));
        }
    }

    @Test
    void adminPasswordFailuresAreLimitedBeforeMfaEnrollment() throws Exception {
        User admin = new User();
        admin.setEmail("throttle-http@example.test"); admin.setFullName("Throttle Test Admin");
        admin.setRole(User.Role.ADMIN); admin.setPasswordHash(passwords.encode(FIXTURE_PASSWORD));
        users.save(admin);
        for (int attempt = 0; attempt < 5; attempt++) {
            assertEquals(401,
                    loginAttempt(admin.getEmail(), "not-the-password", "").status());
        }
        assertEquals(401, loginAttempt(admin.getEmail(), FIXTURE_PASSWORD, "").status());
    }

    @Test
    void pendingMfaSetupExpiresWithoutEnablingTheAccount() throws Exception {
        User admin = new User();
        admin.setEmail("expiry-http@example.test"); admin.setFullName("Expiry Test Admin");
        admin.setRole(User.Role.ADMIN); admin.setPasswordHash(passwords.encode(FIXTURE_PASSWORD));
        admin = users.save(admin);
        String token = login(admin.getEmail());
        Result setup = request("POST", "/api/admin/security/mfa/setup", token, Map.of("currentPassword", FIXTURE_PASSWORD));
        assertEquals(200, setup.status());
        try (var connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
             var statement = connection.prepareStatement("UPDATE " + SCHEMA + ".admin_mfa SET setup_expires_at=CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE user_id=?")) {
            statement.setObject(1, admin.getId()); statement.executeUpdate();
        }
        String secret = setup.body().path("data").path("secret").asText();
        assertEquals(409, request("POST", "/api/admin/security/mfa/confirm", token,
                Map.of("code", totp.code(secret, Instant.now().getEpochSecond() / 30))).status());
        assertFalse(users.findByEmailIgnoreCase(admin.getEmail()).orElseThrow().isMfaEnabled());
    }

    private Result loginAttempt(String email, String password, String code) {
        try { return request("POST", "/api/auth/login", null, Map.of("email", email, "password", password, "mfaCode", code)); }
        catch (Exception exception) { throw new RuntimeException(exception); }
    }

    private String login(String email) throws Exception {
        return login(email, FIXTURE_PASSWORD);
    }

    private String login(String email, String password) throws Exception {
        return login(email, password, "");
    }

    private String login(String email, String password, String mfaCode) throws Exception {
        Result result = request("POST", "/api/auth/login", null, Map.of("email", email, "password", password, "mfaCode", mfaCode));
        assertEquals(200, result.status());
        String token = result.body().path("token").asText();
        assertFalse(token.isBlank());
        return token;
    }

    private Result request(String method, String path, String token, Object body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(15));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new Result(response.statusCode(), response.body().isBlank() ? null : json.readTree(response.body()));
    }

    private record Result(int status, JsonNode body) { }
}
