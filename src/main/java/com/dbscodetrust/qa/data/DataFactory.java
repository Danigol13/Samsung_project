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
    /** Sequencial por JVM: garante unicidade mesmo com muitos e-mails no mesmo segundo/thread. */
    private static final java.util.concurrent.atomic.AtomicLong SEQ = new java.util.concurrent.atomic.AtomicLong();

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
     * ex.: qa.261119105853.12a3f9@gmail.com  (32 caracteres)
     * Regras do validador do stg2 (observadas): rejeita "+" e "-" e e-mails longos (~40+ caracteres)
     * -> só letras, números e ponto, curto. Timestamp + sequencial (único na JVM)
     * + 4 hex (unicidade entre máquinas/execuções simultâneas).
     */
    public static String uniqueEmail() {
        String prefix = customer().emailPrefix();
        String ts = LocalDateTime.now().format(TS);
        String rnd = UUID.randomUUID().toString().replace("-", "").substring(0, 4);
        long seq = SEQ.incrementAndGet();
        int at = prefix.indexOf('@');
        return prefix.substring(0, at) + "." + ts + "." + seq + rnd + prefix.substring(at);
    }
}
