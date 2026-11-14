package com.qa.automation.tests;

import com.qa.automation.pages.InventoryPage;
import com.qa.automation.pages.LoginPage;
import com.qa.automation.tests.fixtures.DemoUsers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginTest extends BaseTest {
    private static final String CREDENTIALS_ERROR =
            "Epic sadface: Username and password do not match any user in this service";

    @Test
    @Tag("smoke")
    @Tag("regression")
    void deveRealizarLoginComSucesso() {
        InventoryPage inventoryPage = new LoginPage(driver)
                .open(config.webUrl())
                .login(DemoUsers.STANDARD.username(), DemoUsers.STANDARD.password());

        assertTrue(inventoryPage.isLoaded(),
                "O usuário deveria ser direcionado para a página de produtos");
    }

    @Test
    @Tag("regression")
    void deveExibirErroComSenhaIncorreta() {
        LoginPage loginPage = new LoginPage(driver).open(config.webUrl());
        loginPage.login(DemoUsers.STANDARD.username(), "senha_incorreta");

        assertEquals(CREDENTIALS_ERROR, loginPage.getErrorMessage());
    }

    @Test
    @Tag("regression")
    void deveExibirErroComUsuarioIncorreto() {
        LoginPage loginPage = new LoginPage(driver).open(config.webUrl());
        loginPage.login("usuario_incorreto", "secret_sauce");

        assertEquals(CREDENTIALS_ERROR, loginPage.getErrorMessage());
    }

    @Test
    @Tag("regression")
    void deveExibirErroQuandoUsernameEstiverVazio() {
        LoginPage loginPage = new LoginPage(driver).open(config.webUrl());
        loginPage.login("", "");

        assertEquals("Epic sadface: Username is required",
                loginPage.getErrorMessage());
    }

    @Test
    @Tag("regression")
    void deveExibirErroParaUsuarioBloqueado() {
        LoginPage loginPage = new LoginPage(driver).open(config.webUrl());
        loginPage.login(DemoUsers.LOCKED_OUT.username(), DemoUsers.LOCKED_OUT.password());

        assertEquals("Epic sadface: Sorry, this user has been locked out.",
                loginPage.getErrorMessage());
    }

    @Test
    @Tag("regression")
    void deveFazerLogoutERetornarParaLogin() {
        InventoryPage inventoryPage = new LoginPage(driver)
                .open(config.webUrl())
                .login(DemoUsers.STANDARD.username(), DemoUsers.STANDARD.password());
        inventoryPage.logout();

        assertTrue(new LoginPage(driver).isLoginButtonVisible());
    }
}
