package com.flarium.api.command;

import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class EnumArgument<T extends Enum<T>> implements ArgumentType<T> {
    private final Class<T> enumClass;

    public EnumArgument(Class<T> enumClass) {
        this.enumClass = enumClass;
    }

    @Override
    public String name() { return enumClass.getSimpleName().toLowerCase(Locale.ROOT); }

    @Override
    public T parse(String input) {
        try {
            return Enum.valueOf(enumClass, input.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender) {
        return Arrays.stream(enumClass.getEnumConstants())
                .map(Enum::name)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .toList();
    }
}