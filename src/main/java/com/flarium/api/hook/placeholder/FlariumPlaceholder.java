package com.flarium.api.hook.placeholder;

import org.bukkit.OfflinePlayer;

public interface FlariumPlaceholder {
    String getIdentifier();
    String process(OfflinePlayer player, String args);
}