package com.flarium.api.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class MessageService<T extends Enum<T> & MessageKey> {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)}");
    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
            LegacyComponentSerializer.builder().character('&').hexCharacter('#').hexColors().build();

    private final JavaPlugin plugin;
    private final MiniMessage miniMessage;
    private final Class<T> enumClass;
    private volatile EnumMap<T, String> messageCache;
    private volatile TagResolver prefixResolver;

    public MessageService(JavaPlugin plugin, Class<T> enumClass, PluginConfig config) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        this.enumClass = enumClass;
        this.messageCache = new EnumMap<>(enumClass);
        setup(config).join();
    }

    public CompletableFuture<Void> reload(PluginConfig config) {
        return setup(config);
    }

    private CompletableFuture<Void> setup(PluginConfig config) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        FileConfiguration messages = YamlConfiguration.loadConfiguration(file);

        return CompletableFuture.runAsync(() -> {
            EnumMap<T, String> newCache = new EnumMap<>(enumClass);

            for (T key : enumClass.getEnumConstants()) {
                if (!messages.contains(key.getPath())) {
                    plugin.getLogger().severe("Missing message in messages.yml: " + key.getPath());
                    continue;
                }

                List<String> linesToParse = new ArrayList<>();
                if (messages.isList(key.getPath())) {
                    linesToParse.addAll(messages.getStringList(key.getPath()));
                } else {
                    linesToParse.add(messages.getString(key.getPath(), ""));
                }

                List<String> parsedLines = new ArrayList<>();
                for (String line : linesToParse) {
                    Component legacyComponent = LEGACY_SERIALIZER.deserialize(line);
                    parsedLines.add(miniMessage.serialize(legacyComponent));
                }

                String joinedMessage = String.join("<newline>", parsedLines);

                String finalMessage = PLACEHOLDER_PATTERN.matcher(joinedMessage)
                        .replaceAll(match -> "<" + match.group(1) + ">");

                newCache.put(key, finalMessage);
            }

            synchronized (this) {
                this.messageCache = newCache;
                this.prefixResolver = Placeholder.component("prefix", miniMessage.deserialize(config.prefix()));
            }
        }, Thread::startVirtualThread);
    }

    private void loadMessages(EnumMap<T, String> cache) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        FileConfiguration messages = YamlConfiguration.loadConfiguration(file);

        for (T key : enumClass.getEnumConstants()) {
            if (!messages.contains(key.getPath())) {
                plugin.getLogger().severe("Missing message in messages.yml: " + key.getPath());
                continue;
            }

            List<String> linesToParse = new ArrayList<>();
            if (messages.isList(key.getPath())) {
                linesToParse.addAll(messages.getStringList(key.getPath()));
            } else {
                linesToParse.add(messages.getString(key.getPath(), ""));
            }

            List<String> parsedLines = new ArrayList<>();
            for (String line : linesToParse) {
                Component legacyComponent = LEGACY_SERIALIZER.deserialize(line);
                parsedLines.add(miniMessage.serialize(legacyComponent));
            }

            String joinedMessage = String.join("<newline>", parsedLines);

            String finalMessage = PLACEHOLDER_PATTERN.matcher(joinedMessage)
                    .replaceAll(match -> "<" + match.group(1) + ">");

            cache.put(key, finalMessage);
        }
    }

    public void send(CommandSender sender, T key, TagResolver... resolvers) {
        String cachedMessage = messageCache.get(key);
        if (cachedMessage == null || cachedMessage.isEmpty()) {
            return;
        }

        TagResolver resolver = TagResolver.builder()
                .resolvers(prefixResolver)
                .resolvers(resolvers)
                .build();

        Component component = miniMessage.deserialize(cachedMessage, resolver);
        sender.sendMessage(component);
    }
}