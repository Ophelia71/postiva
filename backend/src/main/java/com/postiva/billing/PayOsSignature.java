package com.postiva.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** payOS payment-request canonical form; never log payloads, keys or signature input. */
@Component
public class PayOsSignature {
    private final ObjectMapper mapper;
    private final BillingProperties properties;

    public PayOsSignature(ObjectMapper mapper, BillingProperties properties) {
        this.mapper = mapper; this.properties = properties;
    }

    public String signCreate(long orderCode, long amount, String description, String returnUrl, String cancelUrl) {
        return hmac("amount=" + amount + "&cancelUrl=" + cancelUrl + "&description=" + description
                + "&orderCode=" + orderCode + "&returnUrl=" + returnUrl);
    }

    public boolean verify(JsonNode data, String signature) {
        if (data == null || !data.isObject() || signature == null || !signature.matches("[0-9a-fA-F]{64}")) return false;
        byte[] actual = HexFormat.of().parseHex(signature);
        byte[] expected = HexFormat.of().parseHex(hmac(canonical(data)));
        return MessageDigest.isEqual(expected, actual);
    }

    String canonical(JsonNode data) {
        Map<String, String> sorted = new TreeMap<>();
        data.fields().forEachRemaining(entry -> sorted.put(entry.getKey(), stringValue(entry.getValue())));
        List<String> parts = new ArrayList<>();
        sorted.forEach((key, value) -> parts.add(key + "=" + value));
        return String.join("&", parts);
    }

    private String stringValue(JsonNode node) {
        if (node == null || node.isNull() || "undefined".equals(node.asText()) || "null".equals(node.asText())) return "";
        if (node.isTextual() || node.isNumber() || node.isBoolean()) return node.asText();
        if (node.isArray()) {
            var normalized = mapper.createArrayNode();
            node.forEach(item -> normalized.add(sortObject(item)));
            return normalized.toString();
        }
        return sortObject(node).toString();
    }

    private JsonNode sortObject(JsonNode node) {
        if (!node.isObject()) return node;
        var sorted = mapper.createObjectNode();
        Map<String, JsonNode> fields = new TreeMap<>();
        node.fields().forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue()));
        fields.forEach((key, value) -> sorted.set(key, sortObject(value)));
        return sorted;
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getChecksumKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException("Không thể ký dữ liệu thanh toán", exception); }
    }
}
