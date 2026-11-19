package com.dbscodetrust.qa.pages;

import com.dbscodetrust.qa.config.TestConfig;
import com.microsoft.playwright.Page;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** Página de confirmação: mensagem de sucesso + Order Number (objetivo final do teste). */
public class ConfirmationPage extends BasePage {

    public ConfirmationPage(Page page) { super(page); }

    /**
     * Espera a confirmação OU o retorno de erro de pagamento. Observado no stg2: o submitOrder pode
     * responder HTTP 200 e mesmo assim redirecionar para ...step=CHECKOUT_STEP_PAYMENT&paymenterror=601
     * (falha de pagamento mesmo com cartão de teste aprovado/APRO) - falhamos na hora com mensagem clara.
     */
    public ConfirmationPage waitLoaded() {
        Pattern ok = TestConfig.regex("confirmation.url.regex");
        Pattern paymentError = Pattern.compile(".*[?&]paymenterror=([^&]+).*");
        long deadline = System.currentTimeMillis() + TestConfig.getInt("timeout.place.order");
        while (!ok.matcher(page.url()).matches()) {
            Matcher err = paymentError.matcher(page.url());
            if (err.matches()) {
                throw new AssertionError("Pagamento NÃO concluído: o checkout retornou paymenterror=" + err.group(1)
                        + " (submitOrder HTTP 200, mas sem pedido). Com cartão de teste APRO isso indica "
                        + "instabilidade do sandbox Mercado Pago/stg2. URL: " + page.url());
            }
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Confirmação não carregou em " + TestConfig.getInt("timeout.place.order")
                        + " ms. URL atual: " + page.url());
            }
            page.waitForTimeout(500);
        }
        assertThat(page.locator("main")).containsText(TestConfig.regex("confirmation.success.regex"));
        return this;
    }

    /**
     * Captura o Order Number exibido na tela. O número carrega depois da mensagem de sucesso,
     * então esperamos ele aparecer antes de ler.
     */
    public String orderNumber() {
        Pattern p = TestConfig.regex("order.number.regex");
        assertThat(page.locator("main")).containsText(p);
        String text = page.locator("main").innerText();
        Matcher m = p.matcher(text);
        if (!m.find()) {
            throw new AssertionError("Order Number não encontrado na confirmação. Texto:\n"
                    + text.substring(0, Math.min(1500, text.length())));
        }
        return m.group(1);
    }

    /** O orderCode também vem na URL: /pe/orderConfirmation;orderCode=PE261119-78882479_261119144444218 */
    public String orderCodeFromUrl() {
        Matcher m = TestConfig.regex("order.number.regex").matcher(page.url());
        return m.find() ? m.group(1) : null;
    }
}
