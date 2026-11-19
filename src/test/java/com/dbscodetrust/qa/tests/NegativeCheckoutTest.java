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
        evidence.step("Validar recusa: permanece no pagamento com mensagem de erro",
                () -> payment.get().assertPaymentRejected());
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

    @Test
    @DisplayName("Endereço fora da área de entrega não oferece Delivery Mode")
    void outOfCoverageAddress() {
        DeliveryPage delivery = reachDelivery();
        evidence.step("Selecionar distrito sem cobertura", () ->
                delivery.fillAddress(DataFactory.address("out-of-coverage")));
        evidence.step("Validar ausência de Delivery Mode (ou mensagem de cobertura)", () -> {
            page.waitForTimeout(3_000); // modos chegam via XHR após o distrito
            boolean noModes = delivery.deliveryModesCount() == 0;
            boolean coverageMsg = page.getByText(
                    java.util.regex.Pattern.compile("(?i)(no (hay|tenemos) cobertura|no realizamos env[ií]os|fuera de (la )?zona)"))
                    .count() > 0;
            assertThat(noModes || coverageMsg)
                    .as("esperado: nenhum delivery mode OU mensagem de cobertura").isTrue();
        });
    }
}
