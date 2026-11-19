package com.dbscodetrust.qa.data;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Fonte única da massa de teste. Garante e-mail único por execução/thread
 * (requisito de execução paralela).
 */
public final class DataFactory {

    private static final TestData.Root ROOT = load();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyMMddHHmmss");

    private DataFactory() {}

    private static TestData.Root load() {
        try (InputStream in = DataFactory.class.getClassLoader()
                .getResourceAsStream("testdata/checkout-data.json")) {
            if (in == null) throw new IllegalStateException("testdata/checkout-data.json não encontrado");
            return new ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .readValue(in, TestData.Root.class);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler massa de teste", e);
        }
    }

    public static TestData.Customer customer() { return ROOT.customer(); }

    public static TestData.Address address(String key) {
        var a = ROOT.addresses().get(key);
        if (a == null) throw new IllegalArgumentException("Endereço não cadastrado na massa: " + key);
        return a;
    }

    public static TestData.Card card(String key) {
        var c = ROOT.cards().get(key);
        if (c == null) throw new IllegalArgumentException("Cartão não cadastrado na massa: " + key);
        return c;
    }

    /** Cartão base com outro titular -> simula outro status no sandbox MP. */
    public static TestData.Card cardWithStatus(String cardKey, String holder) {
        var c = card(cardKey);
        return new TestData.Card(c.brand(), c.number(), c.expiry(), c.cvv(), holder, c.identificationNumber());
    }

    /**
     * ex.: qa.guest.261119105853.t26a3f9@gmail.com
     * O validador do stg2 rejeita "+" e "-" no e-mail (campo fica inválido e o step não avança),
     * por isso só letras, números e ponto. Timestamp + thread + 4 hex garantem unicidade em paralelo.
     */
    public static String uniqueEmail() {
        String prefix = customer().emailPrefix();
        String ts = LocalDateTime.now().format(TS);
        String rnd = UUID.randomUUID().toString().replace("-", "").substring(0, 4);
        long thread = Thread.currentThread().threadId();
        int at = prefix.indexOf('@');
        return prefix.substring(0, at) + "." + ts + ".t" + thread + rnd + prefix.substring(at);
    }
}
