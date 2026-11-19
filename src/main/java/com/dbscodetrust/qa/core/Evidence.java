package com.dbscodetrust.qa.core;

import com.microsoft.playwright.Page;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Documenta cada passo com screenshot full-page e gera um relatório HTML por teste
 * (requisito do problema: "document each step and screen with screenshots").
 *
 * Uso: evidence.step("06 - Preencher e-mail de convidado", () -> guestPage.continueAsGuest(email));
 */
public final class Evidence {

    public enum Status { PASSED, FAILED }

    public record StepRecord(int index, String name, Status status, String screenshot,
                             String url, long durationMs, String error) {}

    private final Path dir;
    private final String testName;
    private final Page page;
    private final List<StepRecord> steps = new ArrayList<>();
    private final Map<String, String> facts = new LinkedHashMap<>();
    private final Instant startedAt = Instant.now();

    public Evidence(Path baseDir, String testName, Page page) {
        this.testName = testName;
        this.page = page;
        String folder = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + "_" + testName.replaceAll("[^a-zA-Z0-9._-]", "_");
        this.dir = baseDir.resolve(folder);
        try {
            Files.createDirectories(dir.resolve("screenshots"));
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível criar pasta de evidências " + dir, e);
        }
    }

    public Path dir() { return dir; }

    /** Dados relevantes do teste (e-mail usado, order number, total...). Vão para o topo do relatório. */
    public void fact(String key, String value) { facts.put(key, value); }

    public void step(String name, Runnable action) {
        int idx = steps.size() + 1;
        Instant t0 = Instant.now();
        try {
            action.run();
            steps.add(new StepRecord(idx, name, Status.PASSED, shot(idx, name), safeUrl(),
                    Duration.between(t0, Instant.now()).toMillis(), null));
        } catch (RuntimeException | AssertionError e) {
            steps.add(new StepRecord(idx, name, Status.FAILED, shot(idx, name + "_FAILED"), safeUrl(),
                    Duration.between(t0, Instant.now()).toMillis(), e.getMessage()));
            throw e;
        }
    }

    private String shot(int idx, String name) {
        String file = "screenshots/%02d_%s.png".formatted(idx, name.replaceAll("[^a-zA-Z0-9]+", "_"));
        try {
            page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve(file)).setFullPage(true));
            return file;
        } catch (RuntimeException e) {
            return null; // página fechada/crash: não mascarar o erro original
        }
    }

    private String safeUrl() {
        try { return page.url(); } catch (RuntimeException e) { return "-"; }
    }

    /** Gera report.html. Chamado no @AfterEach, passe ou falhe. */
    public Path writeReport(boolean testPassed) {
        StringBuilder html = new StringBuilder("""
                <!DOCTYPE html><html lang="pt-BR"><head><meta charset="utf-8">
                <title>Evidências - %s</title>
                <style>
                 body{font-family:system-ui,Arial,sans-serif;margin:24px;color:#1d1d1f;background:#fafafa}
                 h1{font-size:20px} .meta{background:#fff;border:1px solid #ddd;border-radius:8px;padding:12px 16px;margin-bottom:20px}
                 .meta td{padding:2px 12px 2px 0} .pass{color:#0a7d32;font-weight:600} .fail{color:#c62828;font-weight:600}
                 .step{background:#fff;border:1px solid #ddd;border-radius:8px;padding:12px 16px;margin:12px 0}
                 .step h2{font-size:15px;margin:0 0 6px} .step small{color:#666} .err{color:#c62828;white-space:pre-wrap}
                 .step img{max-width:100%%;border:1px solid #eee;margin-top:8px;max-height:700px;object-fit:contain;object-position:top}
                </style></head><body>
                """.formatted(esc(testName)));
        html.append("<h1>").append(esc(testName)).append(" — <span class='")
            .append(testPassed ? "pass'>PASSED" : "fail'>FAILED").append("</span></h1>");
        html.append("<div class='meta'><table>")
            .append(row("Início", startedAt.toString()))
            .append(row("Duração", Duration.between(startedAt, Instant.now()).toSeconds() + " s"));
        facts.forEach((k, v) -> html.append(row(k, v)));
        html.append("</table></div>");
        for (StepRecord s : steps) {
            html.append("<div class='step'><h2>").append("%02d. ".formatted(s.index())).append(esc(s.name()))
                .append(" <span class='").append(s.status() == Status.PASSED ? "pass'>✔" : "fail'>✘").append("</span></h2>")
                .append("<small>").append(esc(s.url())).append(" · ").append(s.durationMs()).append(" ms</small>");
            if (s.error() != null) html.append("<div class='err'>").append(esc(s.error())).append("</div>");
            if (s.screenshot() != null)
                html.append("<a href='").append(s.screenshot()).append("' target='_blank'><img loading='lazy' src='")
                    .append(s.screenshot()).append("'></a>");
            html.append("</div>");
        }
        html.append("</body></html>");
        Path report = dir.resolve("report.html");
        try {
            Files.writeString(report, html.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return report;
    }

    private static String row(String k, String v) {
        return "<tr><td><b>" + esc(k) + "</b></td><td>" + esc(v) + "</td></tr>";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;");
    }
}
