package com.dbscodetrust.qa.pages;

import com.dbscodetrust.qa.config.TestConfig;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.math.BigDecimal;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Helpers comuns. Estratégia de locators (recon stg2 - SAP Spartacus / Angular 19 + Angular Material):
 *  1) data-an-la (atributo de analytics, estável entre releases)
 *  2) name / formcontrolname
 *  3) texto/role visível
 *  IDs de input são UUIDs gerados a cada render -> NUNCA usar.
 */
public abstract class BasePage {

    protected final Page page;

    protected BasePage(Page page) {
        this.page = page;
    }

    /**
     * Elemento VISÍVEL com o data-an-la informado. O site renderiza CTAs duplicados
     * (ex.: botão fixo do mobile oculto no desktop), então sempre filtramos pelos visíveis.
     */
    protected Locator byAnalytics(String label) {
        return page.locator("[data-an-la=\"" + label + "\"] >> visible=true").first();
    }

    protected void waitForStep(String step) {
        page.waitForURL(Pattern.compile(".*step=" + step + ".*"),
                new Page.WaitForURLOptions().setTimeout(TestConfig.getInt("timeout.navigation")));
    }

    /**
     * Seleciona opção em mat-select. Os selects de endereço são em CASCATA
     * (Departamento -> Provincia -> Distrito) e as opções chegam via XHR, então
     * esperamos a opção específica existir antes de clicar.
     */
    protected void selectMatOption(Locator matSelect, String optionText) {
        assertThat(matSelect).isVisible();
        assertThat(matSelect).hasAttribute("aria-disabled", "false");
        Locator option = page.getByRole(AriaRole.OPTION,
                new Page.GetByRoleOptions().setName(optionText).setExact(true));
        // opções podem demorar (XHR); reabre o painel se abriu vazio
        for (int attempt = 1; attempt <= 3; attempt++) {
            matSelect.click();
            try {
                option.waitFor(new Locator.WaitForOptions().setTimeout(5_000));
                break;
            } catch (RuntimeException e) {
                page.keyboard().press("Escape");
                if (attempt == 3) throw new AssertionError("Opção '" + optionText + "' não carregou no mat-select", e);
            }
        }
        option.click();
        page.locator(".cdk-overlay-pane mat-option").first()
            .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.DETACHED));
        assertThat(matSelect).containsText(optionText);
    }

    /** "S/ 5,399.68" -> 5399.68 */
    protected static BigDecimal money(String text) {
        String digits = text.replaceAll("[^0-9.,]", "").replace(",", "");
        return new BigDecimal(digits);
    }
}
