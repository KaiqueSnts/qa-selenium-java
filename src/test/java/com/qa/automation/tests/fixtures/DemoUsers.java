package com.qa.automation.tests.fixtures;

public final class DemoUsers {
    public static final Credentials STANDARD = new Credentials("standard_user", "secret_sauce");
    public static final Credentials LOCKED_OUT = new Credentials("locked_out_user", "secret_sauce");

    private DemoUsers() { }

    public record Credentials(String username, String password) { }
}
