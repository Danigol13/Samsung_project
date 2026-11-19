package com.dbscodetrust.qa.core;

import com.dbscodetrust.qa.config.TestConfig;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

/**
 * Playwright NÃO é thread-safe: um Playwright + Browser por thread do JUnit.
 * Cada teste ganha um BrowserContext novo (isolamento de cookies/carrinho).
 */
public final class BrowserManager {

    private static final ThreadLocal<Playwright> PLAYWRIGHT = new ThreadLocal<>();
    private static final ThreadLocal<Browser> BROWSER = new ThreadLocal<>();

    private BrowserManager() {}

    public static Browser browser() {
        if (BROWSER.get() == null || !BROWSER.get().isConnected()) {
            Playwright pw = Playwright.create();
            PLAYWRIGHT.set(pw);
            BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions()
                    .setHeadless(TestConfig.getBool("headless"))
                    .setSlowMo(TestConfig.getInt("slow.mo"));
            BrowserType type = switch (TestConfig.get("browser").toLowerCase()) {
                case "firefox" -> pw.firefox();
                case "webkit"  -> pw.webkit();
                default        -> pw.chromium();
            };
            BROWSER.set(type.launch(opts));
            Runtime.getRuntime().addShutdownHook(new Thread(pw::close));
        }
        return BROWSER.get();
    }
}
