package com.dbscodetrust.qa.tests;

import com.dbscodetrust.qa.api.CartApi;
import com.dbscodetrust.qa.config.TestConfig;
import com.dbscodetrust.qa.data.DataFactory;
import com.dbscodetrust.qa.data.TestData;
import com.dbscodetrust.qa.pages.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.concurrent.atomic.AtomicReference;

import com.microsoft.playwright.assertions.PlaywrightAssertions;
import static org.assertj.core.api.Assertions.assertThat;

/** Variações negativas pedidas no enunciado: cartão recusado e endereço fora da área. */
@Tag("e2e")
@Tag("negative")
class NegativeCheckoutTest extends BaseE2ETest {

    /** Leva o fluxo até o step de Delivery (pré-condição comum). */
    private DeliveryPage reachDelivery() {
        CartApi api = new CartApi(context);
        String email = DataFactory.uniqueEmail();
        evidence.fact("E-mail (único)", email);
        var delivery = new AtomicReference<DeliveryPage>();
        evidence.step("Pré-condição: SKU no carrinho via API", () -> {
            api.getStagingCookie();
            api.addToCart(TestConfig.sku(), 1);
        });
        evidence.step("Pré-condição: Cart -> Guest -> Datos de cliente", () -> delivery.set(
                new CartPage(page).open().clickContinue()
                        .continueAsGuest(email)
                        .fill(DataFactory.customer())
                        .continueToDelivery()));
        return delivery.get();
    }

    @ParameterizedTest(name = "Cartão recusado - titular {0} ({1})")
    @CsvSource({
            "OTHE, rejected - general error",
            "FUND, rejected - insufficient amount",
            "SECU, rejected - invalid security code"
    })
    @DisplayName("Pagamento recusado pelo Mercado Pago não cria pedido e exibe erro")
    void declinedCard(String holder, String expected) {
        evidence.fact("Status esperado (sandbox MP)", holder + " -> " + expected);
        DeliveryPage delivery = reachDelivery();
        var payment = new AtomicReference<PaymentPage>();

        evidence.step("Endereço + Delivery Mode + T&C", () -> payment.set(delivery
                .fillAddress(DataFactory.address("lima-miraflores"))
                .chooseDeliveryMode(TestData.DeliveryMode.NORMAL)
                .acceptTerms()
                .continueToPayment()));
        evidence.step("Preencher cartão com titular " + holder, () ->
                payment.get().selectCreditCard().fillCard(DataFactory.cardWithStatus("visa", holder)));
        evidence.step("Realizar pedido", () -> payment.get().clickPlaceOrder());
        evidence.step("Validar recusa: pedido não criado e feedback de erro ao usuário",
                () -> payment.get().assertPaymentRejected(45_000));
    }

    @Test
    @DisplayName("Endereço obrigatório: Dirección vazia bloqueia avanço com mensagem de validação")
    void requiredAddressValidation() {
        DeliveryPage delivery = reachDelivery();
        TestData.Address a = DataFactory.address("lima-miraflores");
        TestData.Address noStreet = new TestData.Address(a.department(), a.province(), a.district(),
                "", a.number(), a.apartment(), a.reference());

        evidence.step("Preencher endereço sem Dirección", () -> delivery
                .fillAddress(noStreet)
                .chooseDeliveryMode(TestData.DeliveryMode.NORMAL)
                .acceptTerms());
        evidence.step("Tentar continuar e validar mensagem", () -> {
            delivery.clickContinueExpectingValidation();
            delivery.assertFieldError("Por favor, ingrese dirección.");
            PlaywrightAssertions.assertThat(page).hasURL(java.util.regex.Pattern.compile(".*step=CHECKOUT_STEP_DELIVERY.*"));
        });
    }

    /**
     * "ZIP outside the delivery area": o Peru não usa ZIP; a área é Departamento/Provincia/Distrito.
     * Recon stg2: não há distrito totalmente sem cobertura, mas a área de entrega expressa/agendada
     * é restrita a Lima/Callao. Fora dela o site deve oferecer SOMENTE o envío regular de provincias.
     */
    @Test
    @DisplayName("Endereço fora da área de entrega Lima/Callao não oferece entrega expressa nem agendada")
    void outOfCoverageAddress() {
        DeliveryPage delivery = reachDelivery();
        TestData.Address addr = DataFactory.address("out-of-coverage");
        evidence.fact("Endereço", addr.department() + " / " + addr.province() + " / " + addr.district());
        evidence.step("Selecionar endereço fora de Lima/Callao", () -> delivery.fillAddress(addr));
        evidence.step("Validar modos de entrega: sem NEXTDAY/SCHEDULE, apenas envío regular provincias", () -> {
            var ids = delivery.deliveryModeIds();
            evidence.fact("Delivery modes oferecidos", String.join(", ", ids));
            assertThat(ids).as("fora de Lima/Callao não deve haver entrega expressa nem agendada")
                    .noneMatch(id -> id.contains("-NEXTDAY-") || id.contains("-SCHEDULE-"));
            // se o distrito não tiver cobertura nenhuma, também é comportamento válido de "fora da área"
            if (!ids.isEmpty()) {
                assertThat(ids).as("apenas o envío regular").allMatch(id -> id.contains("-NORMAL"));
                PlaywrightAssertions.assertThat(page.locator("main")).containsText(TestConfig.ci("env[ií]os a provincias"));
            }
        });
    }
}
