package com.flarium.api.config;

public enum ApiLang implements MessageKey {
    GENERAL_RELOAD("general.reload"),
    ERROR_NO_PERMISSION("error.no-permission"),
    HELP_COMMANDS_LIST("help.commands-list");

    private final String path;

    ApiLang(String path) {
        this.path = path;
    }

    @Override
    public String getPath() {
        return path;
    }
}