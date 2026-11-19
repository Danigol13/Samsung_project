package com.dbscodetrust.qa.pages;

import com.dbscodetrust.qa.config.TestConfig;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** /pe/cart */
public class CartPage extends BasePage {

    private final Locator continueButton = byAnalytics("proceed to checkout");

    public CartPage(Page page) { super(page); }

    public CartPage open() {
        page.navigate(TestConfig.siteUrl("/cart"),
                new Page.NavigateOptions().setTimeout(TestConfig.getInt("timeout.navigation")));
        assertThat(page).hasTitle(Pattern.compile("(?i)shopping basket|carrito"));
        return this;
    }

    /** Passo 3: SKU listado (o código aparece abaixo do nome do produto). */
    public CartPage assertSkuListed(String sku) {
        assertThat(page.locator("main").getByText(sku, new Locator.GetByTextOptions().setExact(true)).first())
                .isVisible();
        assertThat(page.locator("main a[href*='/p/" + sku + "']").first()).isVisible();
        return this;
    }

    public String productName(String sku) {
        return page.locator("main a[href*='/p/" + sku + "']").first().innerText().trim();
    }

    /** Passo 4: "Continuar" -> página intermediária de convidado. */
    public GuestLoginPage clickContinue() {
        continueButton.first().click();
        page.waitForURL(Pattern.compile(".*/guestlogin/checkout.*"),
                new Page.WaitForURLOptions().setTimeout(TestConfig.getInt("timeout.navigation")));
        return new GuestLoginPage(page);
    }
}
