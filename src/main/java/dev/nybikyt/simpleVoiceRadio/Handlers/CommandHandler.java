package dev.nybikyt.simpleVoiceRadio.Handlers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import dev.nybikyt.simpleVoiceRadio.Bridges.JavaZoom;
import dev.nybikyt.simpleVoiceRadio.Misc.Item;
import dev.nybikyt.simpleVoiceRadio.Misc.RecipeHolder;
import dev.nybikyt.simpleVoiceRadio.SimpleVoiceRadio;
import dev.nybikyt.simpleVoiceRadio.Utils.SkinManager;
import dev.nybikyt.simpleVoiceRadio.VoiceAddon;
import org.jetbrains.annotations.NotNull;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CommandHandler implements CommandExecutor, TabCompleter {

    private final SimpleVoiceRadio plugin;
    private final Item item;
    private final SkinManager skinManager;
    private final VoiceAddon voiceAddon;

    private final TextColor errorColor = TextColor.fromHexString("#d64933");
    private final TextColor successColor = TextColor.fromHexString("#f57542");
    private final TextColor miscColor = TextColor.fromHexString("#778da9");
    private final TextComponent playBackError = Component.text("Playback failed: ", errorColor);
    private final TextComponent startStreaming = Component.text("Streaming audio: ", successColor);

    public CommandHandler(SimpleVoiceRadio plugin, Item item, SkinManager skinManager, VoiceAddon voiceAddon) {
        this.plugin = plugin;
        this.item = item;
        this.skinManager = skinManager;
        this.voiceAddon = voiceAddon;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) return false;

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadConfig();
                skinManager.reloadConfig();
                item.reloadCraft();
                sender.sendMessage(Component.text("Config has been reloaded!", successColor));
            }

            case "give" -> {
                if (args.length < 2) return false;
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found: " + args[1], errorColor));
                    return true;
                }
                target.getInventory().addItem(item.getItem());
                sender.sendMessage(Component.text("Radio has been given!", successColor));
            }

            case "view_craft" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Players only.", errorColor));
                    return true;
                }
                RecipeHolder holder = new RecipeHolder();
                Inventory inventory = Bukkit.createInventory(holder, InventoryType.WORKBENCH, Component.text("Radio craft recipe"));
                holder.setInventory(inventory);
                inventory.setContents(item.getRecipeIngredients());
                player.openInventory(inventory);
            }

            case "play_audio" -> {
                if (args.length < 2) return false;
                switch (args[1].toLowerCase()) {
                    case "stop" -> {
                        if (!JavaZoom.isStreaming()) {
                            sender.sendMessage(Component.text("Nothing is playing.", errorColor));
                            return true;
                        }
                        JavaZoom.stopStreaming();
                        sender.sendMessage(Component.text("Playback stopped.", successColor));
                    }

                    case "file" -> {
                        if (args.length < 3) return false;
                        if (JavaZoom.isStreaming()) {
                            sender.sendMessage(Component.text("Already streaming.", errorColor));
                            return true;
                        }
                        String filename = String.join("", Arrays.copyOfRange(args, 2, args.length));
                        File audioFile = new File(plugin.getDataFolder(), "audio/" + filename);
                        if (!audioFile.exists()) {
                            sender.sendMessage(Component.text("File not found: ", errorColor).append(Component.text(filename, miscColor)));
                            return true;
                        }
                        try {
                            AudioInputStream stream = AudioSystem.getAudioInputStream(audioFile);
                            voiceAddon.getUtils().playFile(stream);
                            sender.sendMessage(startStreaming.append(Component.text(filename, miscColor)));
                        } catch (Exception e) {
                            sender.sendMessage(playBackError.append(Component.text(e.getMessage(), miscColor)));
                            SimpleVoiceRadio.LOGGER.warn(playBackError.content() + e);
                        }
                    }

                    case "url" -> {
                        if (args.length < 3) return false;
                        if (JavaZoom.isStreaming()) {
                            sender.sendMessage(Component.text("Already streaming.", errorColor));
                            return true;
                        }
                        String url = String.join("", Arrays.copyOfRange(args, 2, args.length));
                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            sender.sendMessage(Component.text("Invalid URL: must start with http:// or https://", errorColor));
                            return true;
                        }
                        try {
                            AudioInputStream stream = AudioSystem.getAudioInputStream(new URI(url).toURL());
                            voiceAddon.getUtils().playFile(stream);
                            sender.sendMessage(startStreaming.append(Component.text(url, miscColor)));
                        } catch (Exception e) {
                            sender.sendMessage(playBackError.append(Component.text(e.getMessage(), miscColor)));
                            SimpleVoiceRadio.LOGGER.warn(playBackError.content() + e);
                        }
                    }

                    default -> sender.sendMessage(Component.text("Unknown subcommand.", errorColor));
                }
            }

            default -> sender.sendMessage(Component.text("Unknown subcommand.", errorColor));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 1) {
            return Stream.of("reload", "give", "view_craft", "play_audio")
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("play_audio")) {
            return Stream.of("file", "url", "stop")
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("play_audio") && args[1].equalsIgnoreCase("file")) {
            File audioDir = new File(plugin.getDataFolder(), "audio");
            File[] files = audioDir.listFiles();
            if (files == null) return Collections.emptyList();
            return Arrays.stream(files)
                    .filter(File::isFile)
                    .map(File::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}