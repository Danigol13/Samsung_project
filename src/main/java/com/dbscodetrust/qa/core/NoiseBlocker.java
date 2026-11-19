package com.dbscodetrust.qa.core;

import com.microsoft.playwright.BrowserContext;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Remove ruído que causa flakiness, mapeado no recon do stg2:
 *  - Pop-up "Aún tienes productos en tu carrito" (Insider: .ins-custom-cart-reminder-*) cobre os CTAs.
 *  - Pixels de analytics (FB, TikTok, Clarity, Bing, Decibel, Beusable) só atrasam o load.
 * Mercado Pago (secure-fields / api.mercadopago) e Adobe Launch NÃO são bloqueados.
 */
public final class NoiseBlocker {

    private static final List<Pattern> BLOCKED = List.of(
            Pattern.compile(".*useinsider\\.com.*"),
            Pattern.compile(".*connect\\.facebook\\.net.*"),
            Pattern.compile(".*analytics\\.tiktok\\.com.*"),
            Pattern.compile(".*clarity\\.ms.*"),
            Pattern.compile(".*bat\\.bing\\.net.*"),
            Pattern.compile(".*decibelinsight\\.net.*"),
            Pattern.compile(".*beusable\\.net.*")
    );

    /** Rede de segurança caso o pop-up venha de outra origem. */
    private static final String HIDE_POPUPS_SCRIPT = """
            (() => {
              const css = '[class*="ins-custom-cart-reminder"], [id^="ins-"], .ins-preview-wrapper { display:none !important; }';
              const apply = () => {
                if (document.head && !document.getElementById('qa-noise-blocker')) {
                  const s = document.createElement('style'); s.id = 'qa-noise-blocker'; s.textContent = css;
                  document.head.appendChild(s);
                }
              };
              document.addEventListener('DOMContentLoaded', apply); apply();
            })();
            """;

    private NoiseBlocker() {}

    public static void apply(BrowserContext context) {
        for (Pattern p : BLOCKED) {
            context.route(p, route -> route.abort());
        }
        context.addInitScript(HIDE_POPUPS_SCRIPT);
    }
}
