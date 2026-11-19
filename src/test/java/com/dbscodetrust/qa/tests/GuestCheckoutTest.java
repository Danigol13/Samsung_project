package com.dbscodetrust.qa.tests;

import com.dbscodetrust.qa.api.CartApi;
import com.dbscodetrust.qa.api.OrderCapture;
import com.dbscodetrust.qa.config.TestConfig;
import com.dbscodetrust.qa.data.DataFactory;
import com.dbscodetrust.qa.data.TestData;
import com.dbscodetrust.qa.data.TestData.DeliveryMode;
import com.dbscodetrust.qa.pages.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature: Guest checkout - Samsung Shop PE (stg2)
 *
 *  Given I have SKU "X" on the Cart page
 *  When I click "Continue"                              Then I can advance to the Checkout
 *  Given I entered the e-mail on the intermediate page as a guest
 *  Then I reach the Checkout where I fill in personal information
 *  Given after filling personal information the Address section becomes enabled
 *  Then I can select the Delivery Mode
 *  And I am able to complete the payment with the test cards
 *  Then the Confirmation Page displays the Order Number, which I capture
 */
@Tag("e2e")
class GuestCheckoutTest extends BaseE2ETest {

    @Tag("smoke")
    @ParameterizedTest(name = "Guest checkout aprovado - cartão {0}")
    @ValueSource(strings = {"visa", "mastercard", "amex"})
    @DisplayName("Guest compra SKU com cartão de teste Mercado Pago (APRO) e captura o Order Number")
    void guestCheckoutApproved(String cardKey) {
        String sku = TestConfig.sku();
        String email = DataFactory.uniqueEmail();
        TestData.Customer customer = DataFactory.customer();
        TestData.Address address = DataFactory.address("lima-miraflores");
        TestData.Card card = DataFactory.card(cardKey);
        DeliveryMode mode = DeliveryMode.NORMAL;

        evidence.fact("SKU", sku);
        evidence.fact("Browser", TestConfig.get("browser"));
        evidence.fact("E-mail (único)", email);
        evidence.fact("Cartão", card.brand() + " **** " + card.number().substring(card.number().length() - 4)
                + " / titular " + card.cardholderName());

        CartApi cartApi = new CartApi(context);
        var cart = new CartPage(page);
        var guest = new AtomicReference<GuestLoginPage>();
        var contact = new AtomicReference<ContactInfoPage>();
        var delivery = new AtomicReference<DeliveryPage>();
        var payment = new AtomicReference<PaymentPage>();
        var placed = new AtomicReference<OrderCapture>();
        var uiTotal = new AtomicReference<BigDecimal>();
        var confirmation = new ConfirmationPage(page);

        // ---- Pré-condição do staging: Get Cookies Page ----
        evidence.step("00 - Get Cookies Page (acesso ao stg2)", () -> {
            cartApi.getStagingCookie();
            page.navigate(TestConfig.baseUrl() + "/getcookie.html"); // evidência visual do passo
        });

        // ---- Passos 1-2 (Execution Steps): PDP + "Add to Cart" pela UI ----
        var product = new ProductPage(page, sku);
        evidence.step("01 - Abrir página do produto (SKU " + sku + ")", () -> {
            product.open();
            evidence.fact("Produto", product.productName() + " | S/ " + product.price());
        });
        evidence.step("02 - Clicar 'Agregar al carrito' e confirmar 'Producto agregado'", product::addToCart);

        // ---- Passo 3: carrinho ----
        evidence.step("03 - Abrir Cart e validar SKU listado", () ->
                product.goToCart().assertLoaded().assertSkuListed(sku));

        // ---- Passos 4-5: Continue -> página intermediária ----
        evidence.step("04 - Clicar Continuar", () -> guest.set(cart.clickContinue()));
        evidence.step("05 - Página intermediária de convidado", () -> guest.get().assertLoaded());

        // ---- Passos 6-8: e-mail + dados pessoais ----
        evidence.step("06 - Informar e-mail e seguir como convidado",
                () -> contact.set(guest.get().continueAsGuest(email)));
        evidence.step("07 - Checkout: Datos de cliente exibido", () -> contact.get().assertLoaded(email));
        evidence.step("08 - Preencher dados pessoais", () -> contact.get().fill(customer));

        // ---- Passos 9-11: endereço + delivery mode ----
        evidence.step("09 - Seção de endereço habilitada",
                () -> delivery.set(contact.get().continueToDelivery().assertAddressSectionEnabled()));
        evidence.step("10 - Preencher endereço (Departamento/Provincia/Distrito/Dirección)",
                () -> delivery.get().fillAddress(address));
        evidence.step("11 - Escolher Delivery Mode: " + mode.label(), () -> {
            assertThat(delivery.get().deliveryModesCount()).as("delivery modes disponíveis").isPositive();
            delivery.get().chooseDeliveryMode(mode);
        });
        BigDecimal shipping = delivery.get().deliveryModePrice(mode);

        // ---- Passo 12: ir para pagamento ----
        evidence.step("12 - Aceitar T&C e seguir para pagamento",
                () -> payment.set(delivery.get().acceptTerms().continueToPayment()));
        evidence.step("12b - Resumo: Total = Subtotal + Envío", () -> {
            BigDecimal subtotal = payment.get().summaryValue("Subtotal");
            BigDecimal total = payment.get().summaryValue("Total");
            uiTotal.set(total);
            evidence.fact("Subtotal / Envío / Total", subtotal + " / " + shipping + " / " + total);
            assertThat(total).isEqualByComparingTo(subtotal.add(shipping));
        });

        // ---- Passos 13-14: cartão + place order ----
        evidence.step("13 - Preencher cartão de teste (Mercado Pago Secure Fields)",
                () -> payment.get().selectCreditCard().fillCard(card));
        evidence.step("14 - Realizar pedido (captura API de place order)",
                () -> placed.set(payment.get().placeOrder()));

        // ---- Validação final: UI + API ----
        int apiTimeout = TestConfig.getInt("timeout.place.order");
        evidence.step("15 - API: submitOrder (place order) respondeu 200", () -> {
            var submit = placed.get().awaitSubmit(apiTimeout);
            evidence.fact("API submitOrder", submit.httpStatus() + " " + submit.url());
            assertThat(submit.httpStatus()).as("HTTP submitOrder").isEqualTo(200);
        });
        var uiOrder = new AtomicReference<String>();
        evidence.step("16 - Confirmação: mensagem de sucesso e Order Number", () -> {
            confirmation.waitLoaded();
            uiOrder.set(confirmation.orderNumber());
            evidence.fact("ORDER NUMBER", uiOrder.get());
            assertThat(confirmation.orderCodeFromUrl()).as("Order Number tela == orderCode da URL").isEqualTo(uiOrder.get());
        });
        evidence.step("17 - API: pedido criado no backend (GET orders/{code})", () -> {
            var order = placed.get().awaitDetails(apiTimeout);
            evidence.fact("API pedido", order.code() + " | status=" + order.status()
                    + " | guest=" + order.guestCustomer() + " | total=" + order.total() + " " + order.currency());
            assertThat(order.httpStatus()).as("HTTP GET orders").isEqualTo(200);
            assertThat(order.code()).as("Order Number UI == API").isEqualTo(uiOrder.get());
            assertThat(order.status()).as("status do pedido").isNotBlank()
                    .doesNotMatch(TestConfig.regex("order.status.failure.regex"));
            assertThat(order.guestCustomer()).as("pedido de convidado").isTrue();
            assertThat(order.total()).as("total API == total UI").isEqualByComparingTo(uiTotal.get());
            System.out.printf("[ORDER] %s | %s | %s | %s%n", cardKey, email, uiOrder.get(), order.status());
        });
    }
}
