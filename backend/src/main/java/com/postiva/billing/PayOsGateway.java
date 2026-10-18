package com.postiva.billing;

import com.fasterxml.jackson.databind.JsonNode;

public interface PayOsGateway {
    Link create(long orderCode, long amountVnd, String description, long expiresAt);
    Status lookup(long orderCode);
    Status cancel(long orderCode);
    VerifiedPayment verifyPayment(JsonNode body);

    record Link(long orderCode, long amountVnd, String linkId, String checkoutUrl, String recipientAccountNumber) { }
    record Status(long orderCode, long amountVnd, long amountPaidVnd, String status, String linkId,
                  String recipientAccountNumber, String transactionReference) { }
    record VerifiedPayment(long orderCode, long amountVnd, String currency, String linkId,
                           String recipientAccountNumber, String reference, boolean success, String signature) { }
}
