# QA Automation Portfolio — Java + Selenium

Projeto de automação de testes migrado de Playwright e TypeScript para Java 21, Selenium WebDriver, JUnit 5 e Maven. A suíte cobre testes web no [SauceDemo](https://www.saucedemo.com/), testes REST na API [Restful Booker](https://restful-booker.herokuapp.com/) e um fluxo E2E que prepara dados pela API e conclui uma compra pelo navegador.

O projeto demonstra Page Object Model (POM), testes independentes, dados de teste isolados, limpeza de dados da API e execução contínua com GitHub Actions.

## Escopo de testes

| Camada | Aplicação | Cobertura |
|---|---|---|
| Web UI | SauceDemo | Login, logout, carrinho, checkout e campos obrigatórios |
| REST API | Restful Booker | Autenticação, CRUD de reservas, status HTTP e tipos de resposta |
| E2E | SauceDemo + Restful Booker | Dados de cliente criados pela API e checkout no navegador |

### Web UI

- Login válido, senha incorreta, usuário incorreto, usuário bloqueado e username vazio.
- Logout e retorno à tela de login.
- Adição e remoção de produtos no catálogo e no carrinho.
- Validação do contador e dos produtos no carrinho.
- Checkout concluído e validação de nome, sobrenome e código postal obrigatórios.

### REST API

- Autenticação com credenciais válidas e inválidas.
- Criação, consulta, filtragem, atualização e remoção de reservas.
- Respostas 403 para operações sem token e 404 para reserva inexistente/removida.
- Validação dos tipos dos campos da resposta.
- Dados criados por teste são removidos ao final por uma fixture reutilizável.

### E2E

Uma reserva de cliente é criada e consultada pela API. Seus dados são usados no checkout de um produto SauceDemo. A reserva é removida ao final do teste.

## Tecnologias

- Java 21
- Selenium WebDriver 4.27.0 e Selenium Manager
- JUnit 5.11.4
- Apache Maven
- Jackson 2.22.0 para serialização e leitura JSON da API
- Google Chrome
- GitHub Actions

## Estrutura do projeto

```text
src/
├── main/java/com/qa/automation/
│   ├── clients/BookingClient.java
│   ├── config/
│   │   ├── DriverFactory.java
│   │   └── EnvironmentConfig.java
│   ├── models/
│   │   ├── Booking.java
│   │   ├── BookingDates.java
│   │   ├── CheckoutInfo.java
│   │   └── CreatedBooking.java
│   ├── pages/
│   │   ├── BasePage.java
│   │   ├── CartPage.java
│   │   ├── CheckoutPage.java
│   │   ├── InventoryPage.java
│   │   └── LoginPage.java
└── test/java/com/qa/automation/tests/
    ├── api/BookingApiTest.java
    ├── fixtures/
    │   ├── BookingDataFactory.java
    │   ├── BookingFixture.java
    │   ├── DemoUsers.java
    │   └── TestProducts.java
    ├── web/
    │   ├── ApiSeededCheckoutTest.java
    │   ├── AuthSetupTest.java
    │   ├── CartFlowTest.java
    │   └── CheckoutFlowTest.java
    ├── BaseTest.java
    ├── FailureScreenshotExtension.java
    ├── GoogleTest.java
    ├── InventoryTest.java
    └── LoginTest.java
```

`GoogleTest.java` foi preservado do projeto Java preexistente e testa o Google; não faz parte dos cenários SauceDemo/API e pode ser avaliado separadamente no futuro.

## Pré-requisitos

- JDK 21
- Apache Maven
- Google Chrome
- Git

O Selenium Manager resolve automaticamente uma versão compatível do ChromeDriver. Os testes de navegador iniciam Chrome em modo headless quando `HEADLESS=true`.

## Executar

Clone o repositório e entre na pasta do projeto:

```bash
git clone https://github.com/KaiqueSnts/qa-selenium-java
cd qa-selenium-java
```

Execute toda a suíte:

```bash
mvn clean test
```

Execute apenas os testes smoke ou regression:

```bash
mvn -Dgroups=smoke test
mvn -Dgroups=regression test
```

Filtre por camada:

```bash
mvn -Dgroups=api test
mvn -Dgroups=web test
```

Para executar os smoke tests por camada:

```bash
mvn -Dgroups="api & smoke" test
mvn -Dgroups="web & smoke" test
```

Para executar a regressão por camada:

```bash
mvn -Dgroups="api & regression" test
mvn -Dgroups="web & regression" test
```

O Surefire grava os resultados JUnit em XML em `target/surefire-reports/`. A extensão de screenshots salva capturas de falhas de testes web em `target/screenshots/`. Na CI, os XML e as capturas são publicados como artefatos do workflow.

Depois de executar os testes localmente, gere o relatório HTML com:

```bash
mvn surefire-report:report-only
```

O relatório é criado em `target/site/surefire-report.html`. No GitHub Actions, o workflow gera esse HTML a partir dos XML de Surefire e publica no GitHub Pages em execuções que não sejam pull requests.

## Resultados da validação local

- Suíte completa: **33 testes executados, 33 aprovados, 0 falhas, 0 erros e 0 ignorados**.
- Smoke API: **3 testes aprovados**.
- Smoke Web: **5 testes aprovados**.

## Configuração de ambiente

As variáveis são opcionais; os valores padrão apontam para as aplicações públicas de demonstração.

| Variável | Padrão |
|---|---|
| `WEB_URL` | `https://www.saucedemo.com` |
| `API_URL` | `https://restful-booker.herokuapp.com` |
| `API_USERNAME` | `admin` |
| `API_PASSWORD` | `password123` |
| `HEADLESS` | `false` |

As credenciais padrão são as públicas da API de demonstração. Não armazene credenciais privadas ou tokens no repositório.

## Estratégia e CI

- `BaseTest` cria e encerra uma sessão Chrome por teste web; cada cenário faz sua própria autenticação.
- Page Objects centralizam seletores e interações; os testes contêm as validações JUnit.
- `WebDriverWait` é usado para sincronização; não há esperas fixas.
- Testes podem ser selecionados com as tags JUnit `smoke`, `regression`, `api` e `web`.
- GitHub Actions executa smoke em pull requests e a suíte completa em pushes para `main` e execuções agendadas.
- Relatórios XML do Surefire e screenshots de falhas web são publicados como artefatos; o workflow consolida os relatórios em HTML e publica no GitHub Pages fora de pull requests.

O armazenamento de sessão do Playwright foi substituído por login por teste para manter independência. Playwright trace e gravação de vídeo não possuem equivalente direto nesta configuração Selenium; a suíte captura screenshots em falhas e mantém os relatórios XML do Surefire.

## Documentação

- [Plano de teste](docs/test-plan.md)
- [Casos de teste](docs/test-cases.md)

## Limitações conhecidas

- SauceDemo e Restful Booker são serviços públicos; indisponibilidade, latência, limites ou mudanças podem causar falhas externas ao código.
- A API Restful Booker compartilha dados entre usuários; os testes usam nomes únicos e removem reservas criadas.
- A suíte atual executa no Google Chrome desktop.

## Autor

**Kaique Dos Santos Magalhães**, QA Automation Engineer

- LinkedIn: https://www.linkedin.com/in/kaique-dos-santos-magalh%C3%A3es-5a0152436/
- GitHub: https://github.com/KaiqueSnts
- E-mail: kaique.santos.qa@outlook.com
