package com.dbscodetrust.qa.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Configuração centralizada. Precedência: System property > env var > config.properties.
 */
public final class TestConfig {

    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = TestConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (in == null) throw new IllegalStateException("config.properties não encontrado no classpath");
            // UTF-8 explícito: Properties.load(InputStream) assume ISO-8859-1 e quebraria acentos das regex
            PROPS.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao carregar config.properties", e);
        }
    }

    private TestConfig() {}

    public static String get(String key) {
        String sys = System.getProperty(key);
        if (sys != null && !sys.isBlank()) return sys;
        String env = System.getenv(key.toUpperCase().replace('.', '_'));
        if (env != null && !env.isBlank()) return env;
        String val = PROPS.getProperty(key);
        if (val == null) throw new IllegalArgumentException("Chave de configuração ausente: " + key);
        return val.trim();
    }

    public static int getInt(String key)        { return Integer.parseInt(get(key)); }
    public static boolean getBool(String key)   { return Boolean.parseBoolean(get(key)); }

    public static String baseUrl()  { return get("base.url"); }
    public static String site()     { return get("site"); }
    public static String sku()      { return get("sku"); }

    /**
     * Regex segura para o Playwright: o Pattern é convertido para RegExp do JavaScript,
     * que NÃO aceita o modificador inline "(?i)". Remove o "(?i)" e usa a flag CASE_INSENSITIVE
     * (que o Playwright traduz para /.../i).
     */
    public static Pattern ci(String regex) {
        String clean = regex.startsWith("(?i)") ? regex.substring(4) : regex;
        return Pattern.compile(clean, Pattern.CASE_INSENSITIVE);
    }

    /** Regex lida do config.properties, já no formato aceito pelo Playwright. */
    public static Pattern regex(String key) {
        return ci(get(key));
    }

    public static String siteUrl(String path) {
        return baseUrl() + "/" + site() + (path.startsWith("/") ? path : "/" + path);
    }
}
