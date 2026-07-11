package com.flarium.api.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class PlayerArgument implements ArgumentType<Player> {
    @Override
    public String name() { return "player"; }

    @Override
    public Player parse(String input) {
        return Bukkit.getPlayerExact(input);
    }

    @Override
    public List<String> tabComplete(CommandSender sender) {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
        }
        return names;
    }
}