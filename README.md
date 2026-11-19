# Samsung_project — Guest Checkout E2E (Samsung Shop PE · stg2)

MVP de automação do fluxo **manual de criação de pedido como convidado** no staging da Samsung Peru, com **evidência (screenshot) de cada passo/tela** e **captura do Order Number**.

| Item | Escolha |
|---|---|
| Linguagem | Java 21 (LTS; compatível com 25) |
| UI | Playwright‑Java **1.49.0** (Chromium headless) |
| Runner | JUnit 5 (parametrizado + paralelo) |
| Asserções | Playwright Assertions (UI, auto‑wait) + AssertJ (API/dados) |
| Backend | Captura de rede das chamadas OCC (submitOrder + detalhes do pedido) |
| API | `APIRequestContext` compartilhando cookies com o browser + captura de rede do place order |
| Evidências | `report.html` por teste + screenshots full‑page + `trace.zip` |
| Container | Imagem oficial `mcr.microsoft.com/playwright/java:v1.49.0-noble` |
| CI | GitHub Actions e GitLab CI |

> **Sobre a arquitetura sugerida** (Spring Boot + Angular + PostgreSQL): para o MVP optei por um framework de teste enxuto, 100% containerizado. Os componentes de UI/DB são evolução natural (ver *Roadmap*) e não são necessários para provar o fluxo.

---

## Cenário (BDD)

```gherkin
Feature: Guest checkout

  Scenario Outline: Guest compra um SKU com cartão de teste e captura o Order Number
    Given I have SKU "RB45DG6300B1PE" on the Cart page
    When I click "Continue"
    Then I can advance to the Checkout
    Given I have entered the e-mail address on the intermediate page as a guest user
    Then I reach the Checkout where I fill in personal information
    Given after filling personal information the Address section becomes enabled
    Then I can select the Delivery Mode
    And I am able to complete the payment with the test card "<card>"
    Then the confirmation page displays the Order Number and I capture it

    Examples:
      | card       |
      | visa       |
      | mastercard |
      | amex       |
```

Mapeamento passo a passo → `GuestCheckoutTest` (cada `evidence.step` = 1 screenshot):

| # | Passo do roteiro | Implementação |
|---|---|---|
| 1 | Get Cookies Page | `CartApi.getStagingCookie()` |
| 2 | Add SKU ao carrinho | `CartApi.addToCart()` → valida JSONP `resultCode=0000`, `cartCount` |
| 3 | Cart com SKU listado | `CartPage.assertSkuListed()` |
| 4‑5 | Continue → página intermediária | `CartPage.clickContinue()` → `/pe/guestlogin/checkout` |
| 6 | E‑mail de convidado | `GuestLoginPage.continueAsGuest()` (e‑mail único) |
| 7‑8 | Dados pessoais | `ContactInfoPage.fill()` (nome, celular, DNI) |
| 9‑10 | Endereço habilitado e preenchido | `DeliveryPage.fillAddress()` (Departamento → Provincia → Distrito) |
| 11 | Delivery Mode | `DeliveryPage.chooseDeliveryMode(NORMAL)` |
| 12 | Ir para pagamento | `acceptTerms().continueToPayment()` + valida **Total = Subtotal + Envío** |
| 13 | Cartão de teste | `PaymentPage.fillCard()` (Mercado Pago Secure Fields) |
| 14 | Realizar pedido | `PaymentPage.placeOrder()` inicia a captura de rede (`OrderCapture`) |
| 15 | API place order | `POST .../paymentmodes/pe-mercadoCC/submitOrder` → HTTP 200 |
| 16 | Confirmação + Order Number | `/pe/orderConfirmation` · "¡Muchas gracias por tu compra!" · Order Number == `orderCode` da URL |
| 17 | API pedido no backend | `GET .../users/current/orders/{code}` → **code == UI**, status válido, `guestCustomer=true`, **total API == total UI** |

---

## Mapeamento do staging (recon)

Feito manualmente no stg2 antes da automação:

| Tela | URL / step | Locators usados |
|---|---|---|
| Carrinho | `/pe/cart` | `[data-an-la="proceed to checkout"]`, `a[href*='/p/{SKU}']` |
| Guest | `/pe/guestlogin/checkout` | `input[formcontrolname=guestEmail]`, `[data-an-la=guest]` |
| Datos de cliente | `?step=CHECKOUT_STEP_CONTACT_INFO` | `input[name=firstName\|lastName\|email\|phone\|vatNumber]`, mat-select "Tipo de documento" |
| Dirección | `?step=CHECKOUT_STEP_DELIVERY` | `mat-select[name=regionIso\|town\|district]`, `input[name=line1\|line2]`, `input[name=termsAndCondition]`, `[data-an-la="delivery option:next"]` |
| Delivery mode | idem | `input[name$=delivery_mode_option][id*=-NORMAL-]` (SCHEDULE / NEXTDAY / NORMAL) |
| Pago | `?step=CHECKOUT_STEP_PAYMENT` | `iframe[name=cardNumber\|expirationDate\|securityCode]` (MP Secure Fields), `#input-checkout__cardholderName`, `#input-checkout__installments`, `button[data-an-la^="payment:pay now"]` |
| Confirmação | `/pe/orderConfirmation;orderCode=PE…` | "¡Muchas gracias por tu compra!", Order Number `PE######-########` |

**Backend (SAP Commerce OCC)** — `https://s2-smb-api-cdn.ecom-stg.samsung.com/tokocommercewebservices/v2/pe`, mapeado via Playwright trace:

| Chamada | Uso no teste |
|---|---|
| `PUT /users/current/carts/current/email` | e‑mail do convidado |
| `POST /users/current/carts/current/checkout/update` · `deliverymodes/update` | dados / entrega |
| `POST https://api.mercadopago.com/v1/card_tokens` | tokenização do cartão (diagnóstico) |
| `POST /users/current/carts/current/paymentmodes/pe-mercadoCC/submitOrder` | **place order** |
| `GET /users/current/orders/{code}_{ts}?fields=FULL` | **validação do pedido** (`code`, `status`, `guestCustomer`, `totalPriceWithTax`) |

**Pontos de atenção encontrados:**
- Storefront é **SAP Spartacus (Angular 19 + Angular Material)**: IDs de input são UUIDs por render → nunca usados. Prioridade: `data-an-la` → `name/formcontrolname` → texto/role.
- **Peru não tem ZIP**: a área de entrega é definida por Departamento/Provincia/Distrito (selects em cascata, carregados por XHR). Os delivery modes só aparecem após o Distrito.
- Pop‑up **"Aún tienes productos en tu carrito"** (Insider) cobre os CTAs → bloqueado em `NoiseBlocker`.
- Endereço de entrega e de faturação usam os mesmos `name` → sempre `.first()` (entrega).
- Cartão: número/validade/CVV são **iframes cross‑origin do Mercado Pago**. O dropdown de parcelas só popula quando o MP reconhece o BIN — usado como *sync point* antes do "Realizar pedido".
- Validação mapeada: `Por favor, ingrese dirección.`
- O validador de e‑mail **rejeita `+` e `-`** → e‑mails únicos usam só letras, números e ponto.
- Botões aparecem **duplicados** (CTA fixo do mobile oculto no desktop) → locators filtram `visible=true`.
- O checkbox de T&C precisa de **clique real na caixa**; marcar o `<input>` nativo não é registrado pelo form.
- O `<title>` do carrinho chega vazio até o bootstrap do Angular → validação por URL + conteúdo.
- Cada iframe do MP tem **inputs ocultos** (ex.: `input#cardNumber.hide` dentro do iframe de validade) → usa‑se o input visível.

---

## Massa de teste

`src/main/resources/testdata/checkout-data.json`

- **Cartões** (sandbox Mercado Pago PE): Visa `4009 1753 3280 6176`, Mastercard `5031 7557 3453 0604`, Amex `3711 803032 57522` — validade `11/30`, CVV `123` (Amex `1234`), documento `123456789`.
- **O titular define o resultado**: `APRO` aprovado · `OTHE` erro geral · `FUND` saldo insuficiente · `SECU` CVV inválido · `CONT` pendente.
- **E‑mail único** por execução/thread: `qa.guest.<yyMMddHHmmss>.t<thread>n<seq><6hex>@gmail.com` (o site rejeita `+` e `-`).

---

## Como executar

### Docker (recomendado — nada instalado localmente)
```bash
docker compose up --build                     # smoke (3 cartões aprovados)
GROUPS_TAG=negative docker compose up --build # cenários negativos
```

### Local
```bash
# 1x: instalar browser
mvn exec:java -Dexec.args="install --with-deps chromium"

mvn test -Dgroups=unit       # testes do framework (offline)
mvn test -Dgroups=smoke      # happy path
mvn test -Dgroups=negative   # negativos
mvn test -Dgroups=e2e        # tudo

# overrides (System property ou env var)
mvn test -Dgroups=smoke -Dheadless=false -Dslow.mo=300 -Dsku=OUTRO_SKU
mvn test -Djunit.jupiter.execution.parallel.config.fixed.parallelism=1   # padrão: 2
```

### Evidências
```
target/evidence/<timestamp>_<Classe.teste>/
├── report.html        ← abre no browser: passos, URL, duração, screenshots, ORDER NUMBER
├── screenshots/01_...png
└── trace.zip          ← npx playwright show-trace trace.zip
```
O Order Number também é impresso no console: `[ORDER] visa | <email> | <número>`.

---

## Cenários negativos (`NegativeCheckoutTest`)

| Cenário | Esperado |
|---|---|
| Cartão recusado (`OTHE`, `FUND`, `SECU`) | Permanece em `CHECKOUT_STEP_PAYMENT` com mensagem de erro, sem pedido |
| Dirección vazia | `Por favor, ingrese dirección.` e não avança |
| Distrito fora da cobertura | Nenhum delivery mode ou mensagem de cobertura |

---

## Estrutura

```
src/main/java/com/dbscodetrust/qa
├── config/TestConfig.java        # properties + env + -D
├── core/BrowserManager.java      # Playwright/Browser por thread
├── core/NoiseBlocker.java        # bloqueio de pop-ups/analytics
├── core/Evidence.java            # screenshots por passo + report.html
├── data/{TestData,DataFactory}   # massa + e-mail único
├── api/{CartApi,OrderCapture}    # addToCart + captura submitOrder/pedido (OCC)
└── pages/                        # Cart, GuestLogin, ContactInfo, Delivery, Payment, Confirmation
src/test/java/com/dbscodetrust/qa
├── tests/{BaseE2ETest,GuestCheckoutTest,NegativeCheckoutTest,ResultWatcher}
└── unit/FrameworkUnitTest        # JSONP, e-mail único, massa, regex/URLs da confirmação e API
```

---

## Status da execução (stg2 · 19/11/2026)

Execução local: `mvn clean test -Dgroups=e2e` (Chromium headed, 2 threads).

| Cenário | Último run | Observação |
|---|---|---|
| Guest checkout — **Visa** (APRO) | ✅ | `PE261119-78882520` · status `WAITING_FOR_SEND_FINANCIAL` · UI == API (passou nos 3 últimos runs) |
| Guest checkout — **Mastercard** (APRO) | ✅ | `PE261119-78882518` · status `CREATED` · UI == API (passou nos 3 últimos runs) |
| Guest checkout — **Amex** (APRO) | ✅ | `PE261119-78882514` · status `CREATED` · UI == API (estabilizado com a recuperação dos Secure Fields do MP) |
| Cartão recusado — **FUND** | ✅ | Pedido não criado + mensagem de erro exibida |
| Cartão recusado — **OTHE** | ✅ | Pedido não criado + mensagem de erro exibida |
| Cartão recusado — **SECU** | ⚠️ possível defeito | Pedido não criado ✅, mas **sem feedback ao usuário** (botão em loading) — ver item 1 |
| Dirección vazia | ⚠️ intermitente | Passou em 5 de 6 runs; no último, timeout de 60 s do stg2 antes de chegar ao step de endereço (item 2) |
| Distrito fora da cobertura | ⏭️ skipped | Massa sem distrito realmente sem cobertura no stg2 (item 4) |

Pedidos de teste criados nas execuções: `PE261119-78882474`, `-78882479`, `-78882481`, `-78882492`, `-78882493`, `-78882504`, `-78882506`, `-78882514`, `-78882518`, `-78882520`.

### Limitações conhecidas / achados

1. **Possível defeito — recusa de pagamento sem feedback (intermitente).**
   Com titulares de recusa do sandbox Mercado Pago (`FUND`, `SECU`, `OTHE`), o pedido corretamente **não** é criado, mas em parte das execuções o botão "Realizar pedido" fica **em loading indefinidamente, sem mensagem de erro**. O mesmo titular ora exibe a mensagem, ora não (ex.: `SECU` passou em um run e falhou no seguinte; `FUND` o inverso).
   O teste **não mascara** isso: falha com `POSSÍVEL DEFEITO: pagamento recusado sem feedback ao usuário` e anexa screenshot + `trace.zip`. Recomenda‑se abrir bug para o time do checkout/integração MP.
2. **Instabilidade/lentidão do stg2.** Steps do checkout carregam por lazy‑load e chegam a levar 20–60 s sob execução paralela. Timeouts foram ajustados (`config.properties`) e o paralelismo padrão é 2 (`junit-platform.properties`).
3. **Mercado Pago sandbox / SDK.** Em parte das execuções o SDK do MP injeta os iframes dos Secure Fields mas não os monta (ficam invisíveis), impedindo o preenchimento do cartão. Mitigação implementada: o teste recarrega o step de pagamento e reabre o painel (até 2x), como faria um usuário; persistindo, falha com mensagem explícita de instabilidade do MP. Também ocasionalmente a tokenização não dispara o `submitOrder` — nesse caso o teste informa se o `POST /v1/card_tokens` ocorreu e com qual status.
4. **Cobertura de entrega.** Não foi identificado distrito sem cobertura no stg2 (o testado tem entrega). O cenário é ignorado com instrução de ajuste até a massa ser definida com o time.
5. **Pedidos reais no staging.** Cada execução do fluxo aprovado cria pedidos de teste no stg2 (cartões sandbox).

## Roadmap
- Persistir execuções (Order Number, duração, status) em **PostgreSQL** e expor um painel (Spring Boot + Angular) — arquitetura sugerida no desafio.
- Validação pós‑pedido no backoffice (status de pagamento/fulfillment) e cancelamento automático dos pedidos de teste.
- Testes só de API (`@Tag("api")`) para addToCart/checkout sem browser.
- Relatório Allure e execução cross‑browser (Firefox/WebKit via `-Dbrowser=`).
