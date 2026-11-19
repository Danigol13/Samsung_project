package com.dbscodetrust.qa.pages;

import com.dbscodetrust.qa.config.TestConfig;
import com.dbscodetrust.qa.data.TestData;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.math.BigDecimal;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * step=CHECKOUT_STEP_DELIVERY - "2. Dirección de entrega".
 * Recon: a seção tem endereço de ENTREGA e de FATURAÇÃO com os mesmos names (line1, district...).
 * Usamos sempre o primeiro (entrega). Delivery modes só aparecem após escolher o Distrito.
 */
public class DeliveryPage extends BasePage {

    private final Locator department = page.locator("mat-select[name='regionIso']").first();
    private final Locator province   = page.locator("mat-select[name='town']").first();
    private final Locator district   = page.locator("mat-select[name='district']").first();
    private final Locator street     = page.locator("input[name='line1']").first();
    private final Locator number     = page.locator("input[name='line2']").first();
    private final Locator apartment  = page.locator("input[name='apartment']").first();
    private final Locator reference  = page.locator("input[name='landmark']").first();
    private final Locator deliveryOptions = page.locator("input[name$='delivery_mode_option']");
    private final Locator terms      = page.locator("input[name='termsAndCondition']");
    private final Locator continueBtn = byAnalytics("delivery option:next");

    public DeliveryPage(Page page) { super(page); }

    /** Passo 9: seção de endereço habilitada após os dados pessoais. */
    public DeliveryPage assertAddressSectionEnabled() {
        // seção carrega por lazy-load; sob execução paralela o stg2 pode levar > 20 s
        assertThat(department).isVisible(new com.microsoft.playwright.assertions.LocatorAssertions.IsVisibleOptions()
                .setTimeout(TestConfig.getInt("timeout.navigation")));
        assertThat(department).hasAttribute("aria-disabled", "false");
        assertThat(street).isEditable();
        return this;
    }

    /** Passo 10. (Peru não tem ZIP: Departamento/Provincia/Distrito cumprem esse papel.) */
    public DeliveryPage fillAddress(TestData.Address a) {
        selectMatOption(department, a.department());
        selectMatOption(province, a.province());
        selectMatOption(district, a.district());
        street.fill(a.street());
        number.fill(a.number());
        if (a.apartment() != null && !a.apartment().isBlank()) apartment.fill(a.apartment());
        if (a.reference() != null && !a.reference().isBlank()) reference.fill(a.reference());
        street.blur();
        return this;
    }

    /** Passo 11. IDs reais: group0pe-NORMAL-50to100KG (sufixo varia com o peso do produto). */
    public DeliveryPage chooseDeliveryMode(TestData.DeliveryMode mode) {
        Locator radio = deliveryOptions.and(page.locator("[id*='-" + mode.name() + "-']")).first();
        assertThat(radio).isAttached();
        page.getByText(mode.label(), new Page.GetByTextOptions().setExact(true)).first().click();
        assertThat(radio).isChecked();
        return this;
    }

    public int deliveryModesCount() {
        return deliveryOptions.count();
    }

    public BigDecimal deliveryModePrice(TestData.DeliveryMode mode) {
        // sobe do radio até o 1º ancestral que contenha o preço (ou "Gratis")
        Locator card = page.locator("xpath=//input[contains(@id,'-" + mode.name() + "-')]"
                + "/ancestor::*[contains(normalize-space(.),'S/') or contains(.,'Gratis')][1]").first();
        String txt = card.count() > 0 ? card.innerText() : "";
        var m = java.util.regex.Pattern.compile("S/\\s*([0-9.,]+)").matcher(txt);
        return m.find() ? money(m.group(1)) : BigDecimal.ZERO; // "Gratis"
    }

    /**
     * Clique REAL na caixa do mat-checkbox (como o usuário). Marcar o <input> nativo com force
     * deixa o checkbox visualmente marcado, mas o form do Spartacus não registra -> step não avança.
     */
    public DeliveryPage acceptTerms() {
        if (!terms.isChecked()) {
            // clica na CAIXA (.mdc-checkbox), não no texto: o label contém links de T&C/Privacidade
            page.locator("mat-checkbox:has(input[name='termsAndCondition']) .mdc-checkbox").first().click();
        }
        assertThat(terms).isChecked();
        return this;
    }

    /**
     * Passo 12. Se o step não avançar, coleta as mensagens de validação visíveis e tenta mais uma vez;
     * persistindo, falha mostrando os erros (em vez de só "timeout").
     */
    public PaymentPage continueToPayment() {
        street.blur();
        for (int attempt = 1; attempt <= 2; attempt++) {
            continueBtn.scrollIntoViewIfNeeded();
            continueBtn.click();
            try {
                page.waitForURL(java.util.regex.Pattern.compile(".*step=CHECKOUT_STEP_PAYMENT.*"),
                        new Page.WaitForURLOptions().setTimeout(20_000));
                return new PaymentPage(page);
            } catch (com.microsoft.playwright.TimeoutError e) {
                if (attempt == 2) {
                    throw new AssertionError("Não avançou para o pagamento. Validações visíveis: " + visibleErrors(), e);
                }
            }
        }
        throw new IllegalStateException("inalcançável");
    }

    /** Mensagens de erro/validação visíveis no step (mat-error, alertas, campos inválidos). */
    public java.util.List<String> visibleErrors() {
        java.util.List<String> errors = new java.util.ArrayList<>(page.locator(
                "mat-error >> visible=true, [role='alert'] >> visible=true, cx-global-message >> visible=true").allInnerTexts());
        page.locator("mat-form-field.mat-form-field-invalid >> visible=true").all()
                .forEach(f -> errors.add("inválido: " + f.locator("mat-label, label").first().innerText().trim()));
        return errors;
    }

    public void clickContinueExpectingValidation() {
        continueBtn.click();
    }

    /** Mensagem mapeada no recon: "Por favor, ingrese dirección." */
    public void assertFieldError(String message) {
        assertThat(page.getByText(message).first()).isVisible();
    }

    public Locator street() { return street; }
}
