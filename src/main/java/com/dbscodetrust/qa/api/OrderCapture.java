package com.dbscodetrust.qa.api;

import com.dbscodetrust.qa.config.TestConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Escuta a rede do checkout e guarda as chamadas do backend (SAP Commerce OCC) relevantes ao pedido.
 * Mapeado no trace do stg2:
 *  POST .../users/current/carts/current/paymentmodes/pe-mercadoCC/submitOrder  -> place order
 *  GET  .../users/current/orders/{code}_{timestamp}?fields=FULL                  -> detalhes do pedido
 *       {"code":"PE261119-78882474","status":"CREATED","guestCustomer":true,
 *        "totalPriceWithTax":{"value":5399.68,"currencyIso":"PEN"}, ...}
 *
 * Os handlers só guardam o objeto Response; o corpo é lido depois, na thread do teste.
 */
public final class OrderCapture {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Page page;
    private final Pattern submitPattern = TestConfig.regex("place.order.api.regex");
    private final Pattern detailsPattern = TestConfig.regex("order.details.api.regex");
    private final AtomicReference<Response> submit = new AtomicReference<>();
    private final AtomicReference<Response> details = new AtomicReference<>();
    private final Pattern mpTokenPattern = TestConfig.regex("mp.card.token.api.regex");
    private final AtomicReference<Response> mpToken = new AtomicReference<>();

    private OrderCapture(Page page) {
        this.page = page;
        page.onResponse(r -> {
            String url = r.url();
            String method = r.request().method();
            if ("POST".equals(method) && submitPattern.matcher(url).matches()) submit.set(r);
            if ("GET".equals(method) && detailsPattern.matcher(url).matches()) details.set(r);
            if ("POST".equals(method) && mpTokenPattern.matcher(url).matches()) mpToken.set(r);
        });
    }

    /** Registrar ANTES de clicar em "Realizar pedido". */
    public static OrderCapture start(Page page) {
        return new OrderCapture(page);
    }

    /** Espera a resposta do submitOrder (place order). */
    public SubmitResult awaitSubmit(int timeoutMs) {
        Response r;
        try {
            r = await(submit, timeoutMs, "submitOrder");
        } catch (AssertionError e) {
            // diagnóstico: o submitOrder só é chamado depois que o Mercado Pago tokeniza o cartão
            Response t = mpToken.get();
            String mp = t == null ? "Mercado Pago NÃO tokenizou o cartão (POST /v1/card_tokens não ocorreu)"
                    : "Mercado Pago /v1/card_tokens respondeu HTTP " + t.status();
            throw new AssertionError(e.getMessage() + " | " + mp, e);
        }
        String body;
        try { body = r.text(); } catch (RuntimeException e) { body = ""; }
        return new SubmitResult(r.status(), r.url(), body);
    }

    /** Espera o GET de detalhes do pedido disparado pela página de confirmação. */
    public OrderDetails awaitDetails(int timeoutMs) {
        Response r = await(details, timeoutMs, "GET orders/{code}");
        try {
            JsonNode j = MAPPER.readTree(r.text());
            JsonNode total = j.path("totalPriceWithTax");
            return new OrderDetails(r.status(), r.url(),
                    j.path("code").asText(null),
                    j.path("status").asText(null),
                    j.path("guestCustomer").asBoolean(false),
                    total.has("value") ? total.get("value").decimalValue() : null,
                    total.path("currencyIso").asText(null));
        } catch (Exception e) {
            throw new AssertionError("Resposta de detalhes do pedido não é JSON válido: " + r.url(), e);
        }
    }

    /** true se o submitOrder já respondeu (sem esperar). */
    public boolean submitReceived() {
        return submit.get() != null;
    }

    private Response await(AtomicReference<Response> ref, int timeoutMs, String what) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (ref.get() == null) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Chamada de API '" + what + "' não observada em " + timeoutMs + " ms");
            }
            page.waitForTimeout(250); // mantém o event loop do Playwright processando eventos
        }
        return ref.get();
    }

    public record SubmitResult(int httpStatus, String url, String body) {}

    public record OrderDetails(int httpStatus, String url, String code, String status,
                               boolean guestCustomer, BigDecimal total, String currency) {}
}
