# Plano de Teste

## 1. Objetivo

Validar os fluxos principais da loja SauceDemo e do ciclo de reservas Restful Booker, mantendo feedback rápido em pull requests e regressão completa em pushes para `main` e execuções agendadas.

## 2. Escopo

**Incluído**

- Web: autenticação, logout, carrinho, checkout e validação de campos obrigatórios.
- API: autenticação, criação, consulta, filtragem, atualização e remoção de reservas; respostas 403/404 e contrato de tipos.
- Integração: dados de cliente criados e consultados pela API são usados em um checkout web.

**Fora do escopo**

- Performance, acessibilidade e regressão visual.
- Outros usuários especiais do SauceDemo.
- Firefox, WebKit e dispositivos móveis.

## 3. Tipos e ferramentas

| Tipo | Ferramentas | Execução |
|---|---|---|
| Web UI | Selenium WebDriver, JUnit 5 | Smoke em pull request; regressão no push e agendamento |
| API | Java HttpClient, Jackson, JUnit 5 | Smoke em pull request; regressão no push e agendamento |
| E2E | Selenium WebDriver + cliente REST | Regressão |
| Manual | Casos em `test-cases.md` | Quando houver mudança relevante |

## 4. Ambientes

- Web: https://www.saucedemo.com
- API: https://restful-booker.herokuapp.com
- Local: Java 21, Maven e Google Chrome.
- CI: GitHub Actions, Temurin 21 e Chrome headless com Selenium Manager.

## 5. Compilação e execução

**Compilação:** execute `mvn -DskipTests package`. O Maven compila o código e empacota o projeto sem executar os testes.

**Execução:** execute `mvn clean test` para rodar a suíte completa. O resultado da execução é diferente do resultado da compilação.

**Pré-requisitos para execução:** aplicações públicas acessíveis, Java 21, Maven e Google Chrome disponíveis.

## 6. Critérios de saída

- Para aprovação de pull request, os 3 smoke tests de API e os 5 smoke tests Web devem passar.
- A validação local completa deve executar 33 testes, com 33 aprovados, 0 falhas, 0 erros e 0 ignorados.
- Pushes para `main` e execuções agendadas executam a suíte completa; a execução deve terminar sem falhas.
- Reservas criadas durante os testes devem ser removidas pela fixture ou pelo cleanup do próprio cenário.

## 7. Riscos e mitigação

| Risco | Impacto | Mitigação |
|---|---|---|
| Instabilidade dos serviços públicos | Falsos negativos | Identificar falhas de disponibilidade e consultar screenshots/relatórios JUnit |
| Dados compartilhados na API | Interferência entre testes | Nomes únicos e cleanup de reservas |
| Mudança de seletores SauceDemo | Falhas web | Page Objects centralizam os seletores |
| Incompatibilidade entre Chrome, Selenium e ChromeDriver | Falha ao iniciar o navegador | Selenium Manager resolve automaticamente um ChromeDriver compatível com o Chrome instalado; revisar a combinação de versões ao atualizar o navegador ou Selenium |
| Diferença de comportamento API | Falhas de contrato | Casos registram status e formatos esperados |

## 8. Métricas

Taxa de aprovação, duração da suíte, falhas intermitentes e defeitos por severidade.
