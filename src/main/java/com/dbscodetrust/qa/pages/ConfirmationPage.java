package com.dbscodetrust.qa.pages;

import com.dbscodetrust.qa.config.TestConfig;
import com.microsoft.playwright.Page;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** Página de confirmação: mensagem de sucesso + Order Number (objetivo final do teste). */
public class ConfirmationPage extends BasePage {

    public ConfirmationPage(Page page) { super(page); }

    public ConfirmationPage waitLoaded() {
        page.waitForURL(Pattern.compile(TestConfig.get("confirmation.url.regex")),
                new Page.WaitForURLOptions().setTimeout(TestConfig.getInt("timeout.place.order")));
        assertThat(page.locator("main")).containsText(
                Pattern.compile(TestConfig.get("confirmation.success.regex")));
        return this;
    }

    /** Captura o Order Number exibido na tela. */
    public String orderNumber() {
        String text = page.locator("main").innerText();
        Matcher m = Pattern.compile(TestConfig.get("order.number.regex")).matcher(text);
        if (!m.find()) {
            throw new AssertionError("Order Number não encontrado na confirmação. Texto:\n"
                    + text.substring(0, Math.min(1500, text.length())));
        }
        return m.group(1);
    }
}
