package com.postiva.billing;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

@Component
public class PayOsClient implements PayOsGateway {
    private final BillingProperties properties;
    private final PayOsSignature signature;
    private final WebClient client;

    public PayOsClient(BillingProperties properties, PayOsSignature signature, WebClient.Builder builder) {
        this.properties = properties; this.signature = signature;
        this.client = builder.clone().baseUrl("https://api-merchant.payos.vn").build();
    }

    @Override
    public Link create(long orderCode, long amountVnd, String description, long expiresAt) {
        requireReady();
        Map<String, Object> request = Map.of("orderCode", orderCode, "amount", amountVnd, "description", description,
                "returnUrl", properties.getReturnUrl(), "cancelUrl", properties.getCancelUrl(), "expiredAt", expiresAt,
                "signature", signature.signCreate(orderCode, amountVnd, description, properties.getReturnUrl(), properties.getCancelUrl()));
        JsonNode data = signed(requestBody(client.post().uri("/v2/payment-requests").bodyValue(request)));
        if (data.path("orderCode").asLong(-1) != orderCode || data.path("amount").asLong(-1) != amountVnd
                || !"VND".equals(data.path("currency").asText()) || !"PENDING".equals(data.path("status").asText())) badProvider();
        String id = requiredText(data, "paymentLinkId");
        String checkout = requiredText(data, "checkoutUrl");
        URI uri = URI.create(checkout);
        if (!"https".equals(uri.getScheme()) || !"pay.payos.vn".equalsIgnoreCase(uri.getHost())
                || !uri.getPath().equals("/web/" + id)) badProvider();
        return new Link(orderCode, amountVnd, id, checkout, requiredText(data, "accountNumber"));
    }

    @Override
    public Status lookup(long orderCode) {
        requireReady();
        JsonNode data = signed(requestBody(client.get().uri("/v2/payment-requests/{id}", orderCode)));
        return status(orderCode, data);
    }

    @Override
    public Status cancel(long orderCode) {
        requireReady();
        JsonNode data = signed(requestBody(client.post().uri("/v2/payment-requests/{id}/cancel", orderCode)
                .bodyValue(Map.of("cancellationReason", "Customer requested cancellation"))));
        return status(orderCode, data);
    }

    @Override
    public VerifiedPayment verifyPayment(JsonNode body) {
        requireReady();
        JsonNode data = body == null ? null : body.path("data");
        String signed = body == null ? "" : body.path("signature").asText("");
        if (!signature.verify(data, signed)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chữ ký webhook không hợp lệ");
        return new VerifiedPayment(data.path("orderCode").asLong(-1), data.path("amount").asLong(-1),
                data.path("currency").asText(""), data.path("paymentLinkId").asText(""),
                data.path("accountNumber").asText(""), data.path("reference").asText(""),
                body.path("success").asBoolean(false) && "00".equals(body.path("code").asText())
                        && "00".equals(data.path("code").asText()), signed);
    }

    private Status status(long orderCode, JsonNode data) {
        if (data.path("orderCode").asLong(-1) != orderCode
                || !data.path("id").asText("").matches("[A-Za-z0-9_-]{10,100}")
                || data.path("amount").asLong(-1) < 0
                || !java.util.List.of("PENDING", "PAID", "CANCELLED", "EXPIRED").contains(data.path("status").asText(""))) badProvider();
        JsonNode transactions = data.path("transactions");
        String reference = "";
        if (transactions.isArray() && transactions.size() == 1) reference = transactions.get(0).path("reference").asText("");
        else if (transactions.isObject() && transactions.size() == 1) reference = transactions.elements().next().path("reference").asText("");
        return new Status(orderCode, data.path("amount").asLong(-1), data.path("amountPaid").asLong(-1),
                data.path("status").asText(""), data.path("id").asText(""),
                data.path("accountNumber").asText(""), reference);
    }

    private JsonNode signed(JsonNode root) {
        if (root == null || !"00".equals(root.path("code").asText())
                || !signature.verify(root.path("data"), root.path("signature").asText(""))) badProvider();
        return root.path("data");
    }

    private JsonNode requestBody(WebClient.RequestHeadersSpec<?> request) {
        try {
            JsonNode body = request.header("x-client-id", properties.getClientId())
                    .header("x-api-key", properties.getApiKey())
                    .retrieve().bodyToMono(JsonNode.class).block(Duration.ofSeconds(12));
            if (body == null) badProvider();
            return body;
        } catch (ResponseStatusException exception) { throw exception; }
        catch (Exception exception) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Không thể xác minh với cổng thanh toán"); }
    }

    private void requireReady() {
        if (!properties.providerConfigured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Cổng thanh toán chưa được cấu hình");
    }

    private String requiredText(JsonNode data, String key) {
        String value = data.path(key).asText("");
        if (value.isBlank()) badProvider();
        return value;
    }

    private void badProvider() { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Dữ liệu cổng thanh toán không hợp lệ"); }
}
