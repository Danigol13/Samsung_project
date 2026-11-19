package com.dbscodetrust.qa.pages;

import com.dbscodetrust.qa.config.TestConfig;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Página de produto (PDP) - /pe/p/{SKU}. Execution Steps 1-2 do desafio.
 *
 * Recon stg2:
 *  - CTA: button[data-an-la="add to cart"][data-modelcode="{SKU}"] (renderizado 2x: sticky oculto + visível)
 *  - Após o clique abre o modal .add-to-cart-container com "Producto agregado"
 *      "Finalizar Compra"     -> [data-an-la="recommended product confirmation pop up:ok"]  (vai para /pe/cart)
 *      "Continuar comprando"  -> [data-an-la="recommended product confirmation pop up:continue shopping"]
 */
public class ProductPage extends BasePage {

    private final String sku;
    private final Locator addToCartBtn;
    // o site abre o modal 2x empilhado (app-add-cart-modal): o de CIMA é o último no DOM
    // e intercepta cliques no de baixo -> sempre .last()
    private final Locator addedModal = page.locator("app-add-cart-modal.show .add-to-cart-container >> visible=true").last();
    private final Locator goToCheckoutBtn = page.locator(
            "app-add-cart-modal.show [data-an-la='recommended product confirmation pop up:ok'] >> visible=true").last();

    public ProductPage(Page page, String sku) {
        super(page);
        this.sku = sku;
        this.addToCartBtn = page.locator("button[data-an-la='add to cart'][data-modelcode='" + sku + "'] >> visible=true").first();
    }

    /** Step 1: abre a página do produto que contém o SKU. */
    public ProductPage open() {
        page.navigate(TestConfig.siteUrl("/p/" + sku),
                new Page.NavigateOptions().setTimeout(TestConfig.getInt("timeout.navigation")));
        assertThat(addToCartBtn).isVisible(new com.microsoft.playwright.assertions.LocatorAssertions.IsVisibleOptions()
                .setTimeout(TestConfig.getInt("timeout.navigation")));
        assertThat(addToCartBtn).hasAttribute("data-modelcode", sku);
        return this;
    }

    public String productName() {
        return addToCartBtn.getAttribute("data-modeldisplay");
    }

    public String price() {
        return addToCartBtn.getAttribute("data-modelprice");
    }

    /** Step 2: "Agregar al carrito" e confirmação "Producto agregado". */
    public ProductPage addToCart() {
        addToCartBtn.click();
        assertThat(addedModal).isVisible(new com.microsoft.playwright.assertions.LocatorAssertions.IsVisibleOptions()
                .setTimeout(TestConfig.getInt("timeout.navigation")));
        assertThat(addedModal).containsText(TestConfig.ci("producto agregado"));
        return this;
    }

    /** "Finalizar Compra" no modal -> página do carrinho. */
    public CartPage goToCart() {
        goToCheckoutBtn.click();
        page.waitForURL(Pattern.compile(".*/" + TestConfig.site() + "/cart.*"),
                new Page.WaitForURLOptions().setTimeout(TestConfig.getInt("timeout.navigation")));
        return new CartPage(page);
    }
}
