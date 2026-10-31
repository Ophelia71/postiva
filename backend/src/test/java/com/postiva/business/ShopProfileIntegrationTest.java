package com.postiva.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.postiva.ai.dto.AiPostResult;
import com.postiva.ai.dto.ProviderTokenUsage;
import com.postiva.ai.dto.ShopContext;
import com.postiva.ai.service.OpenAiService;
import com.postiva.auth.security.JwtService;
import com.postiva.mail.EmailService;
import com.postiva.scheduler.PublishingScheduler;
import com.postiva.template.entity.IndustryTemplate;
import com.postiva.user.entity.User;
import com.postiva.user.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Opt-in, real PostgreSQL tests of the shop profile and of its use in AI drafts. */
@EnabledIfEnvironmentVariable(named = "POSTIVA_TEST_DB_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=", "server.address=127.0.0.1", "postiva.mail.enabled=false",
        "postiva.openai.api-key=isolated-test-no-provider-calls", "postiva.storage.upload-dir=target/shop-profile-uploads",
        "app.jwt.secret=only-for-shop-profile-isolated-tests-signing-key",
        "postiva.admin.bootstrap.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ShopProfileIntegrationTest {
    private static final String SCHEMA = "shop_profile_" + UUID.randomUUID().toString().replace("-", "");
    private static final String URL = System.getenv("POSTIVA_TEST_DB_URL");
    private static final String DB_USER = System.getenv().getOrDefault("POSTIVA_TEST_DB_USERNAME", "postgres");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("POSTIVA_TEST_DB_PASSWORD", "");
    private static final String SIGNATURE = "📍 12 Lê Lợi, Q1 · ☎ 0901 234 567";

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtService jwt;
    @MockBean EmailService email;
    @MockBean OpenAiService ai;
    @MockBean PublishingScheduler scheduler;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        if (URL == null || !URL.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/postiva_migration_test(\\?.*)?"))
            throw new IllegalStateException("Dedicated local test database required");
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.datasource.username", () -> DB_USER);
        registry.add("spring.datasource.password", () -> DB_PASSWORD);
        registry.add("spring.datasource.hikari.schema", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
    }

    @AfterAll static void cleanup() throws Exception {
        try (var connection = DriverManager.getConnection(URL, DB_USER, DB_PASSWORD); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    @BeforeEach void fixtures() {
        reset(ai);
        jdbc.update("DELETE FROM ai_daily_budgets");
        when(ai.generatePost(any(), any(), any(), any())).thenAnswer(invocation -> {
            Consumer<ProviderTokenUsage> observer = invocation.getArgument(3);
            observer.accept(new ProviderTokenUsage(500, 0, 100, "gpt-4o-mini", "fixture-post"));
            return new AiPostResult("Tiêu đề", Map.of("FACEBOOK", Map.of("title", "Tiêu đề", "content", "Nội dung AI")),
                    List.of("#caphe"), "Nhắn shop", "Ảnh ly cà phê");
        });
    }

    @Test void profileIsValidatedSavedOnceAndPrivateToItsOwner() {
        User owner = user();
        String token = jwt.generateToken(owner);
        assertTrue(call("GET", "/api/shop-profile", token, null, null).body().path("data").isNull());

        Map<String, Object> missingProducts = profile();
        missingProducts.remove("mainProducts");
        // Bean validation errors are 422 across the API (GlobalExceptionHandler).
        assertEquals(422, call("PUT", "/api/shop-profile", token, null, missingProducts).status());
        Map<String, Object> unknownIndustry = profile();
        unknownIndustry.put("industryCode", "not-a-template");
        assertEquals(400, call("PUT", "/api/shop-profile", token, null, unknownIndustry).status());
        Map<String, Object> otherWithoutName = profile();
        otherWithoutName.put("industryCode", "other");
        assertEquals(400, call("PUT", "/api/shop-profile", token, null, otherWithoutName).status());

        Result saved = call("PUT", "/api/shop-profile", token, null, profile());
        assertEquals(200, saved.status(), saved.body().toString());
        assertTrue(saved.body().path("data").path("complete").asBoolean());

        Map<String, Object> changed = profile();
        changed.put("industryCode", "other");
        changed.put("industryName", "Tiệm bánh");
        changed.put("phone", "");
        JsonNode updated = call("PUT", "/api/shop-profile", token, null, changed).body().path("data");
        assertEquals("Tiệm bánh", updated.path("industryName").asText());
        // Blank optional fields are cleared, not stored as empty strings.
        assertTrue(updated.path("phone").isMissingNode() || updated.path("phone").isNull());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM business_profiles WHERE user_id = ?", Integer.class, owner.getId()));

        assertTrue(call("GET", "/api/shop-profile", jwt.generateToken(user()), null, null).body().path("data").isNull());
    }

    @Test void draftsUseTheProfileIndustrySignatureAndBrandHashtags() {
        User owner = user();
        String token = jwt.generateToken(owner);
        assertEquals(200, call("PUT", "/api/shop-profile", token, null, profile()).status());

        Map<String, Object> request = new HashMap<>();
        request.put("productName", "Cà phê muối");
        request.put("productDescription", "Pha phin");
        request.put("targetAudience", "Dân văn phòng");
        request.put("highlights", "Giao 30 phút");
        request.put("goal", "Tăng đơn");
        request.put("tone", "Thân thiện");
        request.put("platforms", List.of("FACEBOOK"));
        Result generated = call("POST", "/api/posts/generate", token, UUID.randomUUID(), request);
        assertEquals(200, generated.status(), generated.body().toString());

        ArgumentCaptor<IndustryTemplate> template = ArgumentCaptor.forClass(IndustryTemplate.class);
        ArgumentCaptor<ShopContext> shop = ArgumentCaptor.forClass(ShopContext.class);
        verify(ai).generatePost(any(), template.capture(), shop.capture(), any());
        // No industry in the request, so the shop's own industry template is used.
        assertNotNull(template.getValue());
        assertEquals("cafe", template.getValue().getIndustryCode());
        assertEquals("Tiệm Muối", shop.getValue().businessName());
        assertEquals("0901 234 567", shop.getValue().phone());
        assertTrue(shop.getValue().signatureAppended());

        JsonNode data = generated.body().path("data");
        assertEquals("Nội dung AI\n\n" + SIGNATURE, data.path("platformContents").path("FACEBOOK").path("content").asText());
        assertEquals("#TiemMuoi", data.path("hashtags").get(0).asText());
        assertEquals("#caphe", data.path("hashtags").get(1).asText());
    }

    private Map<String, Object> profile() {
        Map<String, Object> body = new HashMap<>();
        body.put("businessName", "Tiệm Muối");
        body.put("industryCode", "cafe");
        body.put("mainProducts", "Cà phê muối, bánh quy bơ");
        body.put("phone", "0901 234 567");
        body.put("address", "12 Lê Lợi, Q1");
        body.put("signature", SIGNATURE);
        body.put("brandHashtags", "#TiemMuoi");
        return body;
    }

    private User user() {
        User user = new User();
        user.setFullName("Shop profile fixture");
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPasswordHash(passwords.encode("isolated-test-password-123"));
        return users.saveAndFlush(user);
    }

    private Result call(String method, String path, String token, UUID key, Object body) {
        try {
            var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json");
            if (token != null) builder.header("Authorization", "Bearer " + token);
            if (key != null) builder.header("Idempotency-Key", key.toString());
            builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Result(response.statusCode(), json.readTree(response.body().isBlank() ? "{}" : response.body()));
        } catch (Exception e) {
            throw new AssertionError("HTTP fixture failed", e);
        }
    }

    private record Result(int status, JsonNode body) { }
}
