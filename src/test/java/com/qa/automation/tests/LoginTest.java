package com.qa.automation.tests;

import com.qa.automation.pages.InventoryPage;
import com.qa.automation.pages.LoginPage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginTest extends BaseTest {
    private static final String CREDENTIALS_ERROR =
            "Epic sadface: Username and password do not match any user in this service";

    @Test
    void deveRealizarLoginComSucesso() {
        InventoryPage inventoryPage = new LoginPage(driver)
                .open()
                .login("standard_user", "secret_sauce");

        assertTrue(inventoryPage.isLoaded(),
                "O usuário deveria ser direcionado para a página de produtos");
    }

    @Test
    void deveExibirErroComSenhaIncorreta() {
        LoginPage loginPage = new LoginPage(driver).open();
        loginPage.login("standard_user", "senha_incorreta");

        assertEquals(CREDENTIALS_ERROR, loginPage.getErrorMessage());
    }

    @Test
    void deveExibirErroComUsuarioIncorreto() {
        LoginPage loginPage = new LoginPage(driver).open();
        loginPage.login("usuario_incorreto", "secret_sauce");

        assertEquals(CREDENTIALS_ERROR, loginPage.getErrorMessage());
    }

    @Test
    void deveExibirErroQuandoUsernameEstiverVazio() {
        LoginPage loginPage = new LoginPage(driver).open();
        loginPage.login("", "");

        assertEquals("Epic sadface: Username is required",
                loginPage.getErrorMessage());
    }

    @Test
    void deveExibirErroParaUsuarioBloqueado() {
        LoginPage loginPage = new LoginPage(driver).open();
        loginPage.login("locked_out_user", "secret_sauce");

        assertEquals("Epic sadface: Sorry, this user has been locked out.",
                loginPage.getErrorMessage());
    }
}
