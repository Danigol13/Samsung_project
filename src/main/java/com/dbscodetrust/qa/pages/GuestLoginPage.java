package com.dbscodetrust.qa.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** /pe/guestlogin/checkout - identificação do convidado. */
public class GuestLoginPage extends BasePage {

    private final Locator guestHeading = page.getByText("Continuar como invitado",
            new Page.GetByTextOptions().setExact(true));
    private final Locator emailInput = page.locator("input[formcontrolname='guestEmail']");
    private final Locator guestButton = byAnalytics("guest");

    public GuestLoginPage(Page page) { super(page); }

    /** Passo 5. */
    public GuestLoginPage assertLoaded() {
        assertThat(guestHeading).isVisible();
        assertThat(emailInput).isVisible();
        return this;
    }

    /** Passo 6: preenche e-mail e confirma como convidado. */
    public ContactInfoPage continueAsGuest(String email) {
        emailInput.fill(email);
        guestButton.click();
        waitForStep("CHECKOUT_STEP_CONTACT_INFO");
        return new ContactInfoPage(page);
    }

    public Locator emailInput() { return emailInput; }
}
