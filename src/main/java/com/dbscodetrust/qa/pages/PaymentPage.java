package com.dbscodetrust.qa.pages;

import com.dbscodetrust.qa.api.OrderCapture;
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
    private final Locator placeOrderBtn = page.locator("button[data-an-la^='payment:pay now'] >> visible=true").first();

    public PaymentPage(Page page) { super(page); }

    /**
     * Abre "Tarjeta de Crédito / Débito" e garante que os Secure Fields do Mercado Pago estão prontos.
     * Observado no stg2: às vezes o SDK do MP injeta o iframe mas não o monta (fica invisível).
     * Recuperação igual à de um usuário: recarregar o step de pagamento e reabrir o painel (até 2x).
     */
    public PaymentPage selectCreditCard() {
        for (int reload = 0; reload <= 2; reload++) {
            if (reload > 0) {
                page.reload();
                waitForStep("CHECKOUT_STEP_PAYMENT");
            }
            assertThat(cardPanelHeader).isVisible(new com.microsoft.playwright.assertions.LocatorAssertions.IsVisibleOptions()
                    .setTimeout(TestConfig.getInt("timeout.navigation")));
            if (openCardPanel() && secureFieldsReady(20_000)) {
                return this;
            }
        }
        throw new AssertionError("Secure Fields do Mercado Pago não ficaram prontos (iframe presente mas invisível) "
                + "mesmo após recarregar o pagamento 2x - instabilidade do SDK/sandbox MP");
    }

    /** Toggle do mat-expansion-panel: só clica se estiver recolhido (aria-expanded=false). */
    private boolean openCardPanel() {
        for (int attempt = 1; attempt <= 2; attempt++) {
            if (!"true".equals(cardPanelHeader.getAttribute("aria-expanded"))) {
                cardPanelHeader.click();
            }
            try {
                cardNumberFrame.waitFor(new Locator.WaitForOptions().setTimeout(10_000));
                return true;
            } catch (com.microsoft.playwright.TimeoutError e) {
                if ("true".equals(cardPanelHeader.getAttribute("aria-expanded"))) cardPanelHeader.click(); // recolhe
            }
        }
        return false;
    }

    /** Os 3 iframes visíveis e com o input interno visível = SDK do MP montado. */
    private boolean secureFieldsReady(int timeoutMs) {
        try {
            for (FrameLocator f : new FrameLocator[]{cardNumber, expiration, cvv}) {
                visibleInput(f).waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
            }
            return true;
        } catch (com.microsoft.playwright.TimeoutError e) {
            return false;
        }
    }

    private static Locator visibleInput(FrameLocator frame) {
        return frame.locator("input:not([type='hidden']):not(.hide) >> visible=true").first();
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

    /**
     * Secure Fields mascaram/formatam: digitar tecla a tecla é mais confiável que fill().
     * Cada iframe do MP tem inputs OCULTOS (ex.: input#cardNumber.hide dentro do iframe de validade),
     * então pegamos só o input visível.
     */
    private void typeInSecureField(FrameLocator frame, String value) {
        Locator input = visibleInput(frame);
        // focus() em vez de click(): o painel do MP anima ao abrir e o click esperava "estabilidade" (flaky)
        input.waitFor(new Locator.WaitForOptions().setTimeout(TestConfig.getInt("timeout.navigation")));
        input.focus();
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
     * Passo 14: "Realizar pedido". Começa a escutar a rede ANTES do clique e devolve o capturador
     * com o submitOrder e o GET de detalhes do pedido (validação de API).
     */
    public OrderCapture placeOrder() {
        OrderCapture capture = OrderCapture.start(page);
        placeOrderBtn.click();
        return capture;
    }

    /** Cenários negativos: clica e devolve o capturador de rede. */
    public OrderCapture clickPlaceOrder() {
        return placeOrder();
    }

    /**
     * Recusa do Mercado Pago. Critérios:
     *  1) NÃO navega para /orderConfirmation (pedido não criado);
     *  2) o usuário recebe feedback: mensagem de erro visível e botão liberado (sem loading infinito).
     * Observado no stg2: titular SECU mostra erro; FUND/OTHE deixam o botão em loading sem mensagem.
     */
    public void assertPaymentRejected(int waitMs) {
        Pattern confirmation = TestConfig.regex("confirmation.url.regex");
        long deadline = System.currentTimeMillis() + waitMs;
        Locator error = page.locator("cx-global-message, mat-error, [role='alert'], [class*='error-message'], [class*='alert-danger'], .mp-error, [class*='payment-error']")
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("\\S")));
        boolean feedback = false;
        while (System.currentTimeMillis() < deadline) {
            if (confirmation.matcher(page.url()).matches()) {
                throw new AssertionError("Pagamento deveria ser RECUSADO, mas o pedido foi criado: " + page.url());
            }
            if (error.count() > 0 && error.first().isVisible()) { feedback = true; break; }
            page.waitForTimeout(500);
        }
        assertThat(page).hasURL(Pattern.compile(".*step=CHECKOUT_STEP_PAYMENT.*"));
        if (!feedback) {
            boolean loading = !placeOrderBtn.isEnabled() || placeOrderBtn.innerText().isBlank();
            throw new AssertionError("POSSÍVEL DEFEITO: pagamento recusado sem feedback ao usuário após "
                    + (waitMs / 1000) + " s (nenhuma mensagem de erro; botão 'Realizar pedido' "
                    + (loading ? "travado em loading" : "liberado") + "). Pedido não foi criado.");
        }
    }

    public Locator placeOrderButton() { return placeOrderBtn; }
}
