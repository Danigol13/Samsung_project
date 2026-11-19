package com.dbscodetrust.qa.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Response;

/**
 * Resultado do place order capturado na REDE (fonte de verdade do backend),
 * usado para cruzar com o Order Number exibido na UI.
 */
public record PlacedOrder(int httpStatus, String url, String code, String status, String rawBody) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static PlacedOrder from(Response response) {
        String body;
        try { body = response.text(); } catch (RuntimeException e) { body = ""; }
        String code = null, status = null;
        try {
            JsonNode n = MAPPER.readTree(body);
            // Spartacus/OCC devolve {code, guid, status...}; customizações podem aninhar em "order"/"data"
            JsonNode order = n.has("order") ? n.get("order") : n.has("data") ? n.get("data") : n;
            code = firstText(order, "code", "orderCode", "orderNumber", "orderId");
            status = firstText(order, "status", "statusDisplay", "paymentStatus");
        } catch (Exception ignored) {
            // corpo não-JSON (redirect/HTML): code fica null e a asserção acusa
        }
        return new PlacedOrder(response.status(), response.url(), code, status, body);
    }

    private static String firstText(JsonNode n, String... fields) {
        for (String f : fields) {
            if (n.hasNonNull(f) && !n.get(f).asText().isBlank()) return n.get(f).asText();
        }
        return null;
    }
}
