package com.flarium.api.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CommandDispatcher implements org.bukkit.command.CommandExecutor, org.bukkit.command.TabCompleter {

    private final JavaPlugin plugin;
    private final CommandNode root;

    public CommandDispatcher(JavaPlugin plugin, CommandNode root) {
        this.plugin = plugin;
        this.root = root;
    }

    public void register(String commandName) {
        PluginCommand pluginCommand = plugin.getCommand(commandName);
        if (pluginCommand == null) {
            plugin.getLogger().severe("Command missing in plugin.yml: " + commandName);
            return;
        }

        pluginCommand.setExecutor(this);
        pluginCommand.setTabCompleter(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        CommandNode current = root;
        CommandContext context = new CommandContext(sender, args, 0);

        while (context.getArgsLength() > 0) {
            CommandNode child = current.getChild(context.getString(0));
            if (child == null) break;

            if (child.getPermission() != null && !sender.hasPermission(child.getPermission())) {
                return true;
            }

            current = child;
            context = context.shift();
        }

        if (!current.getArguments().isEmpty()) {
            int requiredArgs = current.getArguments().size();

            // QoL Validáció: Ellenőrizzük, hogy elég argumentumot adott-e meg
            if (context.getArgsLength() < requiredArgs) {
                StringBuilder missing = new StringBuilder();
                for (int i = context.getArgsLength(); i < requiredArgs; i++) {
                    missing.append("<").append(current.getArguments().get(i).name()).append("> ");
                }
                sender.sendMessage("Missing arguments: " + missing.toString().trim());
                return true;
            }

            int argIdx = 0;
            for (CommandNode.ArgumentDefinition def : current.getArguments()) {
                String input = context.getString(argIdx);
                if (input != null) {
                    Object parsed = def.type().parse(input);
                    context.setArgument(def.name(), parsed);
                }
                argIdx++;
            }
        }

        current.execute(context);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        CommandContext context = new CommandContext(sender, args, 0);

        // Immutable List crash javítása: Új, módosítható ArrayList-be csomagoljuk
        List<String> options = new ArrayList<>(root.tabComplete(context));

        if (args.length > 0) {
            String currentArg = args[args.length - 1].toLowerCase();
            options.removeIf(opt -> !opt.toLowerCase().startsWith(currentArg));
        }

        return options;
    }
}