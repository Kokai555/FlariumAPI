package com.flarium.api.command;

import org.bukkit.command.CommandSender;

import java.util.List;

public interface ArgumentType<T> {
    String name();
    T parse(String input);
    List<String> tabComplete(CommandSender sender);
}