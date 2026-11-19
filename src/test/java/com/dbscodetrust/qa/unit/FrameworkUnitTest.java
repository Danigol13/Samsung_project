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
                .allMatch(e -> e.matches("qa\\.guest\\.\\d{12}\\.t\\d+n\\d+[0-9a-f]{6}@gmail\\.com"))
                .allMatch(e -> !e.contains("+") && !e.contains("-"));
    }

    @Test
    void testDataLoads() {
        assertThat(DataFactory.card("visa").cardholderName()).isEqualTo("APRO");
        assertThat(DataFactory.cardWithStatus("visa", "OTHE").cardholderName()).isEqualTo("OTHE");
        assertThat(DataFactory.address("lima-miraflores").district()).isEqualTo("MIRAFLORES");
    }

    @Test
    void orderNumberRegexMatchesStagingConfirmation() {
        Pattern p = com.dbscodetrust.qa.config.TestConfig.regex("order.number.regex");
        // texto real da confirmação (o número vem em elemento separado do rótulo)
        var m = p.matcher("¡Muchas gracias por tu compra!\n\n\n PE261119-78882481 \n Más Información");
        assertThat(m.find()).isTrue();
        assertThat(m.group(1)).isEqualTo("PE261119-78882481");
        var url = p.matcher("https://stg2.shop.samsung.com/pe/orderConfirmation;orderCode=PE261119-78882479_261119144444218?viewEnd=true");
        assertThat(url.find()).isTrue();
        assertThat(url.group(1)).isEqualTo("PE261119-78882479");
        var failure = com.dbscodetrust.qa.config.TestConfig.regex("order.status.failure.regex");
        assertThat(failure.matcher("WAITING_FOR_SEND_FINANCIAL").matches()).isFalse();
        assertThat(failure.matcher("CREATED").matches()).isFalse();
        assertThat(failure.matcher("CANCELLED").matches()).isTrue();
    }

    @Test
    void apiPatternsMatchObservedEndpoints() {
        var api = "https://s2-smb-api-cdn.ecom-stg.samsung.com/tokocommercewebservices/v2/pe";
        assertThat(com.dbscodetrust.qa.config.TestConfig.regex("place.order.api.regex")
                .matcher(api + "/users/current/carts/current/paymentmodes/pe-mercadoCC/submitOrder?lang=es_PE&curr=PEN").matches()).isTrue();
        assertThat(com.dbscodetrust.qa.config.TestConfig.regex("order.details.api.regex")
                .matcher(api + "/users/current/orders/PE261119-78882474_261119143035031?fields=FULL&lang=es_PE&curr=PEN").matches()).isTrue();
        assertThat(com.dbscodetrust.qa.config.TestConfig.regex("confirmation.url.regex")
                .matcher("https://stg2.shop.samsung.com/pe/orderConfirmation").matches()).isTrue();
    }
}
