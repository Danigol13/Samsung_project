package com.dbscodetrust.qa.api;

import com.dbscodetrust.qa.config.TestConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.BrowserContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Camada API do fluxo. Usa context.request(): compartilha o cookie jar com o BrowserContext,
 * então o carrinho criado aqui é o mesmo que a UI abre em /pe/cart.
 *
 * Recon (stg2):
 *  GET /getcookie.html  -> seta cookie de acesso ao staging
 *  GET /pe/ng/p4v1/addToCart?products[0].productCode=SKU&products[0].quantity=1&callback=cb
 *      -> JSONP: cb({"resultCode":"0000","resultMessage":"SUCCESS","products":[...],"cartCount":1})
 */
public final class CartApi {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final APIRequestContext request;

    public CartApi(BrowserContext context) {
        this.request = context.request();
    }

    /** Passo 1 do roteiro: Get Cookies Page. */
    public void getStagingCookie() {
        APIResponse r = request.get(TestConfig.baseUrl() + "/getcookie.html");
        assertThat(r.status()).as("GET /getcookie.html").isBetween(200, 399);
    }

    /** Passo 2 do roteiro: adiciona SKU ao carrinho e valida o contrato da resposta. */
    public AddToCartResult addToCart(String sku, int qty) {
        String cb = "jQuery_qa_" + System.currentTimeMillis();
        String url = TestConfig.siteUrl("/ng/p4v1/addToCart")
                + "?products[0].productCode=" + sku
                + "&products[0].quantity=" + qty
                + "&callback=" + cb
                + "&_=" + System.currentTimeMillis();
        APIResponse r = request.get(url);
        assertThat(r.status()).as("HTTP status addToCart").isEqualTo(200);

        JsonNode json = parseJsonp(r.text());
        var result = new AddToCartResult(
                json.path("resultCode").asText(),
                json.path("resultMessage").asText(),
                json.path("cartCount").asInt(-1),
                json.path("products").path(0).path("productCode").asText(),
                json.path("products").path(0).path("resultCode").asText());

        assertThat(result.resultCode()).as("addToCart resultCode (body: %s)", r.text()).isEqualTo("0000");
        assertThat(result.productCode()).isEqualTo(sku);
        assertThat(result.productResultCode()).isEqualTo("0000");
        assertThat(result.cartCount()).isGreaterThanOrEqualTo(qty);
        return result;
    }

    static JsonNode parseJsonp(String body) {
        int start = body.indexOf('(');
        int end = body.lastIndexOf(')');
        String json = (start >= 0 && end > start) ? body.substring(start + 1, end) : body;
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Resposta addToCart não é JSON/JSONP válido: " + body, e);
        }
    }

    public record AddToCartResult(String resultCode, String resultMessage, int cartCount,
                                  String productCode, String productResultCode) {}
}
