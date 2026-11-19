package com.dbscodetrust.qa.tests;

import com.dbscodetrust.qa.config.TestConfig;
import com.dbscodetrust.qa.core.BrowserManager;
import com.dbscodetrust.qa.core.Evidence;
import com.dbscodetrust.qa.core.NoiseBlocker;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Tracing;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Ciclo de vida por teste: BrowserContext novo (cookies/carrinho isolados), tracing,
 * relatório de evidências. JUnit cria uma instância por método -> seguro em paralelo.
 */
@ExtendWith(ResultWatcher.class)
public abstract class BaseE2ETest {

    protected BrowserContext context;
    protected Page page;
    protected Evidence evidence;

    @BeforeEach
    void setUpBrowser(TestInfo info) {
        // staging lento (SSR + lazy load por step): 5s padrão das assertions é pouco
        PlaywrightAssertions.setDefaultAssertionTimeout(TestConfig.getInt("timeout.assertion"));
        Browser browser = BrowserManager.browser();
        Path evidenceRoot = Paths.get(TestConfig.get("evidence.dir"));

        Browser.NewContextOptions opts = new Browser.NewContextOptions()
                .setViewportSize(TestConfig.getInt("viewport.width"), TestConfig.getInt("viewport.height"))
                .setLocale("es-PE")
                .setTimezoneId("America/Lima");
        if (TestConfig.getBool("video.enabled")) opts.setRecordVideoDir(evidenceRoot.resolve("videos"));

        context = browser.newContext(opts);
        context.setDefaultTimeout(TestConfig.getInt("timeout.default"));
        context.setDefaultNavigationTimeout(TestConfig.getInt("timeout.navigation"));
        if (TestConfig.getBool("block.third.party")) NoiseBlocker.apply(context);
        if (TestConfig.getBool("trace.enabled")) {
            context.tracing().start(new Tracing.StartOptions().setScreenshots(true).setSnapshots(true).setSources(true));
        }
        page = context.newPage();

        String name = info.getTestClass().map(Class::getSimpleName).orElse("Test") + "."
                + info.getDisplayName();
        evidence = new Evidence(evidenceRoot, name, page);
    }

    @AfterEach
    void tearDownBrowser(TestInfo info) {
        boolean passed = ResultWatcher.lastResultPassed();
        try {
            if (TestConfig.getBool("trace.enabled")) {
                context.tracing().stop(new Tracing.StopOptions().setPath(evidence.dir().resolve("trace.zip")));
            }
            Path report = evidence.writeReport(passed);
            System.out.println("[evidence] " + report.toAbsolutePath());
        } finally {
            context.close();
        }
    }
}
