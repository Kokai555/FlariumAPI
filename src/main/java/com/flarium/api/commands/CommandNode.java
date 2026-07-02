package com.flarium.api.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class CommandNode {

    private final String name;
    private final String permission;
    private final String[] aliases;
    private final Map<String, CommandNode> children = new HashMap<>();
    private final List<ArgumentDefinition> arguments = new ArrayList<>();

    protected CommandNode(String name, String permission, String... aliases) {
        this.name = name.toLowerCase();
        this.permission = permission;
        this.aliases = aliases;
    }

    public void addChild(CommandNode node) {
        children.put(node.getName(), node);
        for (String alias : node.getAliases()) {
            children.put(alias.toLowerCase(), node);
        }
    }

    protected void addArgument(String name, ArgumentType<?> arg) {
        arguments.add(new ArgumentDefinition(name, arg));
    }

    public CommandNode getChild(String name) {
        return children.get(name.toLowerCase());
    }

    public List<ArgumentDefinition> getArguments() {
        return arguments;
    }

    public abstract void execute(CommandContext context);

    public List<String> tabComplete(CommandContext context) {
        if (context.getArgsLength() == 0) return List.of();

        int argIndex = context.getArgsLength() - 1;

        if (argIndex == 0) {
            List<String> options = new ArrayList<>();
            for (Map.Entry<String, CommandNode> entry : children.entrySet()) {
                CommandNode child = entry.getValue();
                if (child.getPermission() == null || context.getSender().hasPermission(child.getPermission())) {
                    options.add(entry.getKey());
                }
            }
            if (!arguments.isEmpty()) {
                options.addAll(arguments.get(0).type().tabComplete(context.getSender()));
            }
            return options;
        }

        if (!children.isEmpty()) {
            CommandNode child = getChild(context.getString(0));
            if (child != null) {
                return child.tabComplete(context.shift());
            }
        }

        if (argIndex < arguments.size()) {
            return arguments.get(argIndex).type().tabComplete(context.getSender());
        }

        return List.of();
    }

    public String getName() { return name; }
    public String getPermission() { return permission; }
    public String[] getAliases() { return aliases; }

    public record ArgumentDefinition(String name, ArgumentType<?> type) {}
}