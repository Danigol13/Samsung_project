package com.dbscodetrust.qa.unit;

import com.dbscodetrust.qa.data.DataFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Testes offline do próprio framework (rodam no CI sem acesso ao staging). */
@Tag("unit")
class FrameworkUnitTest {

    @Test
    void parsesJsonpFromAddToCart() throws Exception {
        String body = "/**/jQuery111305177703263619047_1595407056965({\"resultCode\":\"0000\",\"resultMessage\":\"SUCCESS\","
                + "\"products\":[{\"productCode\":\"RB45DG6300B1PE\",\"resultCode\":\"0000\",\"resultMessage\":\"SUCCESS\"}],"
                + "\"cartCount\":1,\"productPickupFlag\":\"T\"})";
        Method m = Class.forName("com.dbscodetrust.qa.api.CartApi").getDeclaredMethod("parseJsonp", String.class);
        m.setAccessible(true);
        var json = (com.fasterxml.jackson.databind.JsonNode) m.invoke(null, body);
        assertThat(json.path("resultCode").asText()).isEqualTo("0000");
        assertThat(json.path("cartCount").asInt()).isEqualTo(1);
        assertThat(json.path("products").path(0).path("productCode").asText()).isEqualTo("RB45DG6300B1PE");
    }

    @Test
    void uniqueEmailsAreUniqueAcrossThreads() {
        Set<String> emails = java.util.Collections.synchronizedSet(new HashSet<>());
        IntStream.range(0, 500).parallel().forEach(i -> emails.add(DataFactory.uniqueEmail()));
        assertThat(emails).hasSize(500)
                .allMatch(e -> e.matches("qa\\.guest\\+\\d{14}-t\\d+-[0-9a-f]{8}@mailinator\\.com"));
    }

    @Test
    void testDataLoads() {
        assertThat(DataFactory.card("visa").cardholderName()).isEqualTo("APRO");
        assertThat(DataFactory.cardWithStatus("visa", "OTHE").cardholderName()).isEqualTo("OTHE");
        assertThat(DataFactory.address("lima-miraflores").district()).isEqualTo("MIRAFLORES");
    }

    @Test
    void orderNumberRegexMatchesCommonFormats() {
        Pattern p = Pattern.compile(com.dbscodetrust.qa.config.TestConfig.get("order.number.regex"));
        for (String txt : new String[]{
                "¡Gracias por tu compra! Número de pedido: PE210045678",
                "Tu pedido N° 00012345678 fue realizado",
                "Order #PE1234567 placed successfully"}) {
            var m = p.matcher(txt);
            assertThat(m.find()).as(txt).isTrue();
            assertThat(m.group(1)).matches("[A-Z]{0,4}\\d{6,}");
        }
    }
}
