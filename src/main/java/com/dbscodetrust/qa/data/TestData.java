package com.dbscodetrust.qa.data;

import java.util.Map;

/** Modelos de massa de dados (lidos de testdata/checkout-data.json). */
public final class TestData {

    private TestData() {}

    public record Customer(String firstName, String lastName, String emailPrefix, String phone,
                           String documentType, String documentNumber) {}

    /** Peru não usa ZIP: a "área de entrega" é definida por Departamento/Provincia/Distrito. */
    public record Address(String department, String province, String district,
                          String street, String number, String apartment, String reference) {}

    /** cardholderName controla o status no sandbox Mercado Pago (APRO, OTHE, FUND, SECU...). */
    public record Card(String brand, String number, String expiry, String cvv,
                       String cardholderName, String identificationNumber) {}

    /** Delivery modes mapeados no recon: SCHEDULE, NEXTDAY, NORMAL. */
    public enum DeliveryMode {
        SCHEDULE("Agenda tu envío"),
        NEXTDAY("Recíbelo mañana"),
        NORMAL("Envío regular");

        private final String label;
        DeliveryMode(String label) { this.label = label; }
        public String label() { return label; }
    }

    public record Root(Customer customer,
                       Map<String, Address> addresses,
                       Map<String, Card> cards,
                       Map<String, String> paymentStatusByHolder) {}
}
