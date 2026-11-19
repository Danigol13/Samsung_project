package com.dbscodetrust.qa.pages;

import com.dbscodetrust.qa.data.TestData;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** /pe/checkout/one?step=CHECKOUT_STEP_CONTACT_INFO - "1. Datos de cliente". */
public class ContactInfoPage extends BasePage {

    private final Locator firstName  = page.locator("main input[name='firstName']");
    private final Locator lastName   = page.locator("main input[name='lastName']");
    private final Locator email      = page.locator("main input[name='email']");
    private final Locator phone      = page.locator("main input[name='phone']");
    private final Locator docNumber  = page.locator("main input[name='vatNumber']");
    // mat-select do tipo de documento não tem name: ancorado no label do form-field
    private final Locator docType    = page.locator("main mat-form-field")
            .filter(new Locator.FilterOptions().setHasText("Tipo de documento")).locator("mat-select");
    private final Locator continueBtn = byAnalytics("customer details:Continue to delivery options");

    public ContactInfoPage(Page page) { super(page); }

    /** Passo 7. O e-mail informado no guest login vem pré-preenchido. */
    public ContactInfoPage assertLoaded(String expectedEmail) {
        assertThat(firstName).isVisible();
        assertThat(email).hasValue(expectedEmail);
        return this;
    }

    /** Passo 8. */
    public ContactInfoPage fill(TestData.Customer c) {
        firstName.fill(c.firstName());
        lastName.fill(c.lastName());
        phone.fill(c.phone());
        selectMatOption(docType.first(), c.documentType());
        docNumber.fill(c.documentNumber());
        return this;
    }

    public DeliveryPage continueToDelivery() {
        continueBtn.click();
        waitForStep("CHECKOUT_STEP_DELIVERY");
        return new DeliveryPage(page);
    }

    /** Para cenários negativos: clica sem esperar troca de step. */
    public void clickContinueExpectingValidation() {
        continueBtn.click();
    }
}
