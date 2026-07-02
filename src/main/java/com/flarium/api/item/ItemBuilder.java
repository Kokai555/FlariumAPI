package com.flarium.api.item;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ItemBuilder {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder(ItemStack itemStack) {
        this.item = itemStack.clone();
        this.meta = this.item.getItemMeta();
    }

    public ItemBuilder name(String miniMessage) {
        if (miniMessage == null || miniMessage.isEmpty()) return this;
        meta.displayName(MINI_MESSAGE.deserialize(miniMessage));
        return this;
    }

    public ItemBuilder lore(List<String> miniMessages) {
        if (miniMessages == null || miniMessages.isEmpty()) return this;
        List<Component> lore = new ArrayList<>();
        for (String line : miniMessages) {
            lore.add(MINI_MESSAGE.deserialize(line));
        }
        meta.lore(lore);
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, amount));
        return this;
    }

    public ItemBuilder customModelData(int data) {
        if (data != 0) {
            meta.setCustomModelData(data);
        }
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        if (glow) {
            meta.setEnchantmentGlintOverride(true);
        }
        return this;
    }

    public ItemBuilder unbreakable(boolean unbreakable) {
        meta.setUnbreakable(unbreakable);
        if (unbreakable) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        }
        return this;
    }

    public ItemBuilder addPersistentData(Plugin plugin, String key, String value) {
        if (key == null || value == null) return this;
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, key),
                PersistentDataType.STRING,
                value
        );
        return this;
    }

    public ItemBuilder skullTexture(String base64Texture) {
        if (base64Texture == null || base64Texture.isEmpty() || !(meta instanceof SkullMeta skullMeta)) return this;

        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), "flarium_head");
        profile.setProperty(new ProfileProperty("textures", base64Texture));
        skullMeta.setPlayerProfile(profile);
        return this;
    }

    public ItemBuilder skullOwner(String playerName) {
        if (playerName == null || playerName.isEmpty() || !(meta instanceof SkullMeta skullMeta)) return this;

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
        skullMeta.setOwningPlayer(offlinePlayer);
        return this;
    }

    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack fromConfig(ConfigurationSection section) {
        if (section == null) return new ItemStack(Material.AIR);

        String materialName = section.getString("material", "STONE").toUpperCase().replace("-", "_");
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            material = Material.STONE;
        }

        ItemBuilder builder = new ItemBuilder(material)
                .amount(section.getInt("amount", 1))
                .name(section.getString("name", ""))
                .lore(section.getStringList("lore"))
                .customModelData(section.getInt("custom-model-data", 0))
                .glow(section.getBoolean("glow", false))
                .unbreakable(section.getBoolean("unbreakable", false));

        if (material == Material.PLAYER_HEAD) {
            String texture = section.getString("skull-texture");
            if (texture != null) {
                builder.skullTexture(texture);
            } else {
                String owner = section.getString("skull-owner");
                if (owner != null) builder.skullOwner(owner);
            }
        }

        return builder.build();
    }
}