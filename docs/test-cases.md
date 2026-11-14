# Casos de Teste

## Web UI

| ID | Cenário | Resultado esperado |
|---|---|---|
| WEB-01 | Login com `standard_user` e senha válida | A página Products é exibida |
| WEB-02 | Login com senha incorreta | Mensagem de credenciais inválidas |
| WEB-03 | Login com usuário incorreto | Mensagem de credenciais inválidas |
| WEB-04 | Login com `locked_out_user` | Mensagem de usuário bloqueado |
| WEB-05 | Login com username vazio | Mensagem de username obrigatório |
| WEB-06 | Logout de usuário autenticado | Tela de login volta a ser exibida |
| WEB-07 | Adicionar item ao carrinho | Contador mostra 1 e produto aparece no carrinho |
| WEB-08 | Adicionar dois produtos | Contador mostra 2 |
| WEB-09 | Remover item no catálogo | Contador do carrinho volta a zero |
| WEB-10 | Remover item dentro do carrinho | Lista do carrinho fica vazia |
| WEB-11 | Checkout preenchido corretamente | Confirmação “Thank you for your order!” |
| WEB-12 | Checkout sem primeiro nome | Erro “First Name is required” |
| WEB-13 | Checkout sem sobrenome | Erro “Last Name is required” |
| WEB-14 | Checkout sem código postal | Erro “Postal Code is required” |

## REST API

| ID | Cenário | Resultado esperado |
|---|---|---|
| API-01 | Autenticar com credenciais válidas | HTTP 200 e token |
| API-02 | Autenticar com senha incorreta | Corpo informa `Bad credentials` e não contém token |
| API-03 | Criar reserva | HTTP 200 e corpo corresponde aos dados enviados |
| API-04 | Consultar reserva por ID | HTTP 200 e dados correspondentes |
| API-05 | Filtrar reservas por nome | A lista contém o ID criado |
| API-06 | Atualizar com token | HTTP 200 e campos atualizados |
| API-07 | Excluir reserva | HTTP 201; consulta posterior retorna 404 |
| API-08 | Atualizar sem token | HTTP 403 |
| API-09 | Excluir sem token | HTTP 403 |
| API-10 | Consultar ID inexistente | HTTP 404 |
| API-11 | Validar tipos do contrato | Campos têm os tipos e formatos esperados |

## Integração E2E

| ID | Cenário | Resultado esperado |
|---|---|---|
| E2E-01 | Criar dados de cliente pela API e concluir uma compra no SauceDemo | Os dados consultados da reserva são usados no checkout e a página exibe “Thank you for your order!” |

O teste cria uma reserva, consulta o cliente, autentica no SauceDemo, adiciona Sauce Labs Bike Light ao carrinho e usa nome e sobrenome da reserva no checkout. A reserva criada para esse fluxo é removida pela `BookingFixture` ao sair do bloco `try-with-resources`, inclusive se uma validação do teste falhar.
