# QA Selenium Java

Projeto de automação de testes web desenvolvido em Java com Selenium WebDriver, JUnit 5 e Maven, utilizando o [SauceDemo](https://www.saucedemo.com/) como aplicação de demonstração.

O objetivo é aplicar boas práticas de automação de testes, organização de código e manutenção de uma suíte automatizada utilizando o padrão Page Object Model (POM).

## Tecnologias utilizadas

- Java 21
- Selenium WebDriver 4.27.0
- JUnit 5.11.4
- Apache Maven
- Google Chrome

## Cenários automatizados

### Login
- Login com credenciais válidas.
- Login com senha incorreta.
- Login com usuário incorreto.
- Validação de campos obrigatórios.
- Tentativa de login com usuário bloqueado.

### Inventário e carrinho
- Carregamento da página de produtos.
- Adição de produto ao carrinho.
- Atualização do contador de produtos.
- Validação dos produtos no carrinho.
- Remoção de produto e validação do carrinho vazio.

## Arquitetura

O projeto utiliza o padrão **Page Object Model (POM)** para separar os elementos e as ações das páginas da aplicação da lógica dos testes.

```text
src/
├── main/
│   └── java/
│       └── com/qa/automation/
│           ├── config/
│           │   └── DriverFactory.java
│           └── pages/
│               ├── BasePage.java
│               ├── LoginPage.java
│               ├── InventoryPage.java
│               └── CartPage.java
└── test/
    └── java/
        └── com/qa/automation/tests/
            ├── BaseTest.java
            ├── GoogleTest.java
            ├── LoginTest.java
            └── InventoryTest.java
```

`GoogleTest.java` está presente na suíte e testa a abertura do Google; esse teste não está relacionado ao SauceDemo. A permanência dele na suíte pode ser avaliada em uma próxima etapa.

## Pré-requisitos

- JDK 21 ou superior.
- Apache Maven instalado.
- Google Chrome instalado.

O Selenium Manager gerencia automaticamente o ChromeDriver compatível quando necessário.

## Executar os testes

Clone o repositório:

```bash
git clone https://github.com/KaiqueSnts/qa-selenium-java.git
```


Acesse a pasta do projeto:

```bash
cd qa-selenium-java
```

Execute toda a suíte:

```bash
mvn clean test
```

## Resultados

Última execução validada:

- 11 testes executados.
- 0 falhas.
- 0 erros.
- 0 testes ignorados.

**Status: BUILD SUCCESS**

## Estrutura e boas práticas

- Page Object Model (POM).
- Esperas explícitas com WebDriverWait.
- Reutilização de métodos comuns através de BasePage.
- Inicialização e encerramento independente do navegador por teste.
- Separação entre configuração, páginas e testes.
