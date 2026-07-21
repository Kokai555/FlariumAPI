package com.flarium.api.ui.item;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.flarium.api.core.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

public class ItemBuilder {

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
        meta.displayName(ColorUtil.format(miniMessage));
        return this;
    }

    public ItemBuilder lore(List<String> miniMessages) {
        if (miniMessages == null || miniMessages.isEmpty()) return this;
        meta.lore(ColorUtil.format(miniMessages));
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
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    public ItemBuilder hideTooltip(boolean hide) {
        meta.setHideTooltip(hide);
        return this;
    }

    public ItemBuilder color(Color color) {
        if (color == null) return this;
        if (meta instanceof LeatherArmorMeta leatherMeta) {
            leatherMeta.setColor(color);
        } else if (meta instanceof PotionMeta potionMeta) {
            potionMeta.setColor(color);
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

        PlayerProfile profile = Bukkit.createProfile(java.util.UUID.randomUUID(), "flarium_head");
        profile.setProperty(new ProfileProperty("textures", base64Texture));
        skullMeta.setPlayerProfile(profile);
        return this;
    }

    public ItemBuilder skullOwner(String playerName) {
        if (playerName == null || playerName.isEmpty() || !(meta instanceof SkullMeta skullMeta)) return this;

        org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayerIfCached(playerName);
        if (offlinePlayer != null) {
            skullMeta.setOwningPlayer(offlinePlayer);
        } else {
            Bukkit.getLogger().warning("Skull owner '" + playerName + "' not cached. Skipping blocking API call.");
        }
        return this;
    }

    public ItemBuilder enchant(Enchantment enchantment, int level) {
        meta.addEnchant(enchantment, level, true);
        return this;
    }

    public ItemBuilder flag(ItemFlag... flags) {
        meta.addItemFlags(flags);
        return this;
    }

    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack fromConfig(ConfigurationSection section) {
        if (section == null) return new ItemStack(Material.AIR);

        String materialName = section.getString("material", "");
        if (materialName.isEmpty()) return new ItemStack(Material.AIR);

        Material material = Material.matchMaterial(materialName.toUpperCase().replace("-", "_"));
        if (material == null) {
            material = Material.STONE;
        }

        ItemBuilder builder = new ItemBuilder(material)
                .amount(section.getInt("amount", 1))
                .name(section.getString("name", ""))
                .lore(section.getStringList("lore"))
                .customModelData(section.getInt("custom-model-data", 0))
                .glow(section.getBoolean("glow", false))
                .hideTooltip(section.getBoolean("hide-tooltip", false))
                .unbreakable(section.getBoolean("unbreakable", false));

        String colorHex = section.getString("color");
        if (colorHex != null && !colorHex.isEmpty()) {
            try {
                String hex = colorHex.replace("#", "");
                Color color = Color.fromRGB(Integer.parseInt(hex, 16));
                builder.color(color);
            } catch (Exception ignored) {
            }
        }

        if (material == Material.PLAYER_HEAD) {
            String texture = section.getString("skull-texture");
            if (texture != null) {
                builder.skullTexture(texture);
            } else {
                String owner = section.getString("skull-owner");
                if (owner != null) builder.skullOwner(owner);
            }
        }

        if (section.contains("enchants")) {
            for (String enchantLine : section.getStringList("enchants")) {
                String[] split = enchantLine.split(":");
                Enchantment enchant = org.bukkit.Registry.ENCHANTMENT.get(org.bukkit.NamespacedKey.minecraft(split[0].toLowerCase()));
                if (enchant != null) {
                    int level = split.length > 1 ? Integer.parseInt(split[1]) : 1;
                    builder.enchant(enchant, level);
                }
            }
        }

        if (section.contains("flags")) {
            for (String flagName : section.getStringList("flags")) {
                try {
                    builder.flag(ItemFlag.valueOf(flagName.toUpperCase()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        return builder.build();
    }
}