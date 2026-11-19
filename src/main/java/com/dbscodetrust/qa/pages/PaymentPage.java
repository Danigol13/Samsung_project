package com.dbscodetrust.qa.pages;

import com.dbscodetrust.qa.api.PlacedOrder;
import com.dbscodetrust.qa.config.TestConfig;
import com.dbscodetrust.qa.data.TestData;
import com.microsoft.playwright.FrameLocator;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.math.BigDecimal;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * step=CHECKOUT_STEP_PAYMENT - "3. Pago" > "Tarjeta de Crédito / Débito" (Mercado Pago).
 *
 * Recon: número, validade e CVV são iframes cross-origin do MP Secure Fields
 *   iframe[name=cardNumber] / iframe[name=expirationDate] / iframe[name=securityCode]
 * Titular, documento, parcelas e emissor ficam no DOM principal:
 *   #input-checkout__cardholderName / #input-checkout__identificationNumber
 *   #input-checkout__installments   / #input-checkout__issuer
 * O dropdown de parcelas só popula depois que o MP reconhece o BIN -> sinal de que o
 * Secure Field recebeu o número. Sem isso o "Realizar pedido" fica em loading eterno.
 */
public class PaymentPage extends BasePage {

    private final Locator cardPanelHeader = page.locator("mat-expansion-panel-header")
            .filter(new Locator.FilterOptions().setHasText("Tarjeta de Crédito / Débito"));
    private final Locator cardNumberFrame = page.locator("iframe[name='cardNumber']");
    private final FrameLocator cardNumber = page.frameLocator("iframe[name='cardNumber']");
    private final FrameLocator expiration = page.frameLocator("iframe[name='expirationDate']");
    private final FrameLocator cvv        = page.frameLocator("iframe[name='securityCode']");
    private final Locator holder       = page.locator("#input-checkout__cardholderName");
    private final Locator idNumber     = page.locator("#input-checkout__identificationNumber");
    private final Locator installments = page.locator("#input-checkout__installments");
    private final Locator issuer       = page.locator("#input-checkout__issuer");
    private final Locator placeOrderBtn = page.locator("button[data-an-la^='payment:pay now']");

    public PaymentPage(Page page) { super(page); }

    public PaymentPage selectCreditCard() {
        assertThat(cardPanelHeader).isVisible();
        if (!cardNumberFrame.isVisible()) {          // painel pode vir expandido sem renderizar o form
            cardPanelHeader.click();
        }
        if (!cardNumberFrame.isVisible()) {          // toggle fechou: abre de novo
            cardPanelHeader.click();
        }
        assertThat(cardNumberFrame).isVisible();
        return this;
    }

    /** Passo 13. */
    public PaymentPage fillCard(TestData.Card c) {
        typeInSecureField(cardNumber, c.number());
        holder.fill(c.cardholderName());
        typeInSecureField(expiration, c.expiry().replace("/", ""));
        typeInSecureField(cvv, c.cvv());
        if (idNumber.isVisible()) idNumber.fill(c.identificationNumber());
        if (issuer.isVisible() && issuer.locator("option").count() > 1) issuer.selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(1));
        selectFirstInstallment();
        return this;
    }

    /** Secure Fields mascaram/formatam: digitar tecla a tecla é mais confiável que fill(). */
    private void typeInSecureField(FrameLocator frame, String value) {
        Locator input = frame.locator("input").first();
        input.click();
        input.pressSequentially(value, new Locator.PressSequentiallyOptions().setDelay(40));
    }

    private void selectFirstInstallment() {
        // espera o MP popular as parcelas (prova de que o BIN foi aceito)
        page.waitForFunction(
                "sel => sel && Array.from(sel.options).some(o => o.value && o.value !== '')",
                installments.elementHandle(),
                new Page.WaitForFunctionOptions().setTimeout(20_000));
        String first = (String) installments.evaluate(
                "sel => Array.from(sel.options).find(o => o.value && o.value !== '').value");
        installments.selectOption(first);
    }

    /** Total do resumo = subtotal + envío (recon: 5,308.82 + 90.86 = 5,399.68). */
    public BigDecimal summaryValue(String label) {
        Locator row = page.locator("xpath=//*[normalize-space(text())='" + label + "']/following::*[contains(.,'S/')][1]").first();
        return money(row.innerText());
    }

    /**
     * Passo 14: "Realizar pedido". Captura a chamada de place order na rede
     * (complemento de API: valida que o backend criou o pedido).
     */
    public PlacedOrder placeOrder() {
        Pattern api = Pattern.compile(TestConfig.get("place.order.api.regex"));
        Response resp = page.waitForResponse(
                r -> "POST".equals(r.request().method()) && api.matcher(r.url()).matches(),
                new Page.WaitForResponseOptions().setTimeout(TestConfig.getInt("timeout.place.order")),
                placeOrderBtn::click);
        return PlacedOrder.from(resp);
    }

    /** Cenários negativos: clica e NÃO espera sucesso. */
    public void clickPlaceOrder() {
        placeOrderBtn.click();
    }

    /** Recusa do MP: permanece no step de pagamento e exibe mensagem de erro. */
    public void assertPaymentRejected() {
        page.waitForTimeout(3_000); // dá tempo do callback do MP
        assertThat(page).hasURL(Pattern.compile(".*step=CHECKOUT_STEP_PAYMENT.*"));
        Locator error = page.locator("cx-global-message, mat-error, [role='alert'], [class*='error-message'], [class*='alert-danger']")
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("\\S")));
        assertThat(error.first()).isVisible(new com.microsoft.playwright.assertions.LocatorAssertions.IsVisibleOptions()
                .setTimeout(TestConfig.getInt("timeout.place.order")));
    }

    public Locator placeOrderButton() { return placeOrderBtn; }
}
