package com.flarium.api.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class CommandContext {
    private final CommandSender sender;
    private final String[] args;
    private final int indexOffset;
    private final Map<String, Object> parsedArguments;

    public CommandContext(CommandSender sender, String[] args, int indexOffset) {
        this.sender = sender;
        this.args = args;
        this.indexOffset = indexOffset;
        this.parsedArguments = new HashMap<>();
    }

    private CommandContext(CommandSender sender, String[] args, int indexOffset, Map<String, Object> parsedArguments) {
        this.sender = sender;
        this.args = args;
        this.indexOffset = indexOffset;
        this.parsedArguments = parsedArguments;
    }

    public CommandContext shift() {
        return new CommandContext(sender, args, indexOffset + 1, parsedArguments);
    }

    public CommandSender getSender() { return sender; }

    public Player getPlayer() {
        return sender instanceof Player p ? p : null;
    }

    public int getArgsLength() {
        return args.length - indexOffset;
    }

    public String getString(int index) {
        int actualIndex = indexOffset + index;
        if (actualIndex >= args.length) return null;
        return args[actualIndex];
    }

    public <T> T getArgument(String name) {
        return (T) parsedArguments.get(name.toLowerCase());
    }

    public void setArgument(String name, Object value) {
        parsedArguments.put(name.toLowerCase(), value);
    }
}