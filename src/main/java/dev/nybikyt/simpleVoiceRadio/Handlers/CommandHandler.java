package dev.nybikyt.simpleVoiceRadio.Handlers;

import dev.nybikyt.simpleVoiceRadio.Misc.Item;
import dev.nybikyt.simpleVoiceRadio.Misc.RecipeHolder;
import dev.nybikyt.simpleVoiceRadio.SimpleVoiceRadio;
import dev.nybikyt.simpleVoiceRadio.Utils.PluginConfig;
import dev.nybikyt.simpleVoiceRadio.Utils.SkinManager;
import dev.nybikyt.simpleVoiceRadio.SimpleVoiceAddon;
import dev.nybikyt.simpleVoiceRadio.Audio.AudioStreamer;
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
import org.jetbrains.annotations.NotNull;

import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class CommandHandler implements CommandExecutor, TabCompleter {

    private static final TextColor ERROR_COLOR = TextColor.fromHexString("#d64933");
    private static final TextColor SUCCESS_COLOR = TextColor.fromHexString("#f57542");
    private static final TextColor MISC_COLOR = TextColor.fromHexString("#778da9");
    private static final TextComponent PLAYBACK_ERROR = Component.text("Playback failed: ", ERROR_COLOR);
    private static final TextComponent START_STREAMING = Component.text("Streaming audio: ", SUCCESS_COLOR);

    private final SimpleVoiceRadio plugin;
    private final PluginConfig config;
    private final Item item;
    private final SkinManager skinManager;
    private final SimpleVoiceAddon voiceAddon;

    public CommandHandler(SimpleVoiceRadio plugin, PluginConfig config, Item item, SkinManager skinManager, SimpleVoiceAddon voiceAddon) {
        this.plugin = plugin;
        this.config = config;
        this.item = item;
        this.skinManager = skinManager;
        this.voiceAddon = voiceAddon;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) return false;

        return switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "give" -> handleGive(sender, args);
            case "view_craft" -> handleViewCraft(sender);
            case "play_audio" -> handlePlayAudio(sender, args);
            default -> {
                sender.sendMessage(Component.text("Unknown subcommand.", ERROR_COLOR));
                yield true;
            }
        };
    }

    private boolean handleReload(CommandSender sender) {
        config.reload();
        skinManager.reloadConfig();
        item.reloadCraft();
        sender.sendMessage(Component.text("Config has been reloaded!", SUCCESS_COLOR));
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (args.length < 2) return false;

        List<Player> targets = resolveTargets(sender, args[1]);
        if (targets.isEmpty()) {
            sender.sendMessage(Component.text("Player not found: " + args[1], ERROR_COLOR));
            return true;
        }
        targets.forEach(target -> target.getInventory().addItem(item.getItem()));
        sender.sendMessage(Component.text("Radio has been given to " + targets.size() + " player(s)!", SUCCESS_COLOR));
        return true;
    }

    private boolean handleViewCraft(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", ERROR_COLOR));
            return true;
        }
        RecipeHolder holder = new RecipeHolder();
        Inventory inventory = Bukkit.createInventory(holder, InventoryType.WORKBENCH, Component.text("Radio craft recipe"));
        holder.setInventory(inventory);
        inventory.setContents(item.getRecipeIngredients());
        player.openInventory(inventory);
        return true;
    }

    private boolean handlePlayAudio(CommandSender sender, String[] args) {
        if (args.length < 2) return false;

        switch (args[1].toLowerCase()) {
            case "stop" -> handlePlayAudioStop(sender);
            case "file" -> {
                return handlePlayAudioFile(sender, args);
            }
            case "url" -> {
                return handlePlayAudioUrl(sender, args);
            }
            default -> sender.sendMessage(Component.text("Unknown subcommand.", ERROR_COLOR));
        }
        return true;
    }

    private void handlePlayAudioStop(CommandSender sender) {
        if (!AudioStreamer.isStreaming()) {
            sender.sendMessage(Component.text("Nothing is playing.", ERROR_COLOR));
            return;
        }
        AudioStreamer.stopStreaming();
        sender.sendMessage(Component.text("Playback stopped.", SUCCESS_COLOR));
    }

    private boolean handlePlayAudioFile(CommandSender sender, String[] args) {
        if (args.length < 3) return false;
        if (!canStartStream(sender)) return true;

        PlayArguments parsed = parsePlayArguments(args);
        if (parsed == null) return false;

        File audioDir = new File(plugin.getDataFolder(), "audio");
        File audioFile = new File(audioDir, parsed.value());
        if (!isInsideDirectory(audioFile, audioDir)) {
            sender.sendMessage(Component.text("Invalid file path.", ERROR_COLOR));
            return true;
        }
        if (!audioFile.exists()) {
            sender.sendMessage(Component.text("File not found: ", ERROR_COLOR).append(Component.text(parsed.value(), MISC_COLOR)));
            return true;
        }

        startStream(sender, parsed, () -> AudioSystem.getAudioInputStream(audioFile), true);
        return true;
    }

    private boolean handlePlayAudioUrl(CommandSender sender, String[] args) {
        if (args.length < 3) return false;
        if (!canStartStream(sender)) return true;

        PlayArguments parsed = parsePlayArguments(args);
        if (parsed == null) return false;

        String url = parsed.value();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            sender.sendMessage(Component.text("Invalid URL: must start with http:// or https://", ERROR_COLOR));
            return true;
        }

        startStream(sender, parsed, () -> AudioSystem.getAudioInputStream(new URI(url).toURL()), false);
        return true;
    }

    private boolean canStartStream(CommandSender sender) {
        if (voiceAddon == null || voiceAddon.getAudioRouter() == null) {
            sender.sendMessage(Component.text("Voice chat is not available.", ERROR_COLOR));
            return false;
        }
        if (AudioStreamer.isStreaming()) {
            sender.sendMessage(Component.text("Already streaming.", ERROR_COLOR));
            return false;
        }
        return true;
    }

    private void startStream(CommandSender sender, PlayArguments parsed, AudioStreamer.StreamSource source, boolean validate) {
        try {
            if (validate) AudioStreamer.probe(source);
            voiceAddon.getAudioRouter().playAudio(source, parsed.frequency(), parsed.loop());
            sender.sendMessage(describeStream(parsed));
        } catch (Exception e) {
            sender.sendMessage(PLAYBACK_ERROR.append(Component.text(String.valueOf(e.getMessage()), MISC_COLOR)));
            SimpleVoiceRadio.LOGGER.warn("Playback failed: {}", e.toString());
        }
    }

    private boolean isInsideDirectory(File file, File directory) {
        try {
            return file.getCanonicalPath().startsWith(directory.getCanonicalPath() + File.separator);
        } catch (Exception e) {
            return false;
        }
    }

    private record PlayArguments(String value, Integer frequency, boolean loop) {
    }

    private PlayArguments parsePlayArguments(String[] args) {
        List<String> rest = new ArrayList<>(Arrays.asList(args).subList(2, args.length));
        boolean loop = false;
        Integer frequency = null;

        if (!rest.isEmpty() && rest.getLast().equalsIgnoreCase("loop")) {
            loop = true;
            rest.removeLast();
        }
        if (!rest.isEmpty()) {
            String last = rest.getLast();
            if (last.equalsIgnoreCase("all")) {
                rest.removeLast();
            } else {
                try {
                    frequency = Integer.parseInt(last);
                    rest.removeLast();
                } catch (NumberFormatException ignored) {
                }
            }
        }

        String value = String.join(" ", rest).trim();
        return value.isEmpty() ? null : new PlayArguments(value, frequency, loop);
    }

    private Component describeStream(PlayArguments parsed) {
        Component message = START_STREAMING.append(Component.text(parsed.value(), MISC_COLOR));
        if (parsed.frequency() != null) {
            message = message.append(Component.text(" [freq " + parsed.frequency() + "]", MISC_COLOR));
        }
        if (parsed.loop()) {
            message = message.append(Component.text(" [loop]", MISC_COLOR));
        }
        return message;
    }

    private List<Player> resolveTargets(CommandSender sender, String selector) {
        if (selector.startsWith("@")) {
            try {
                return Bukkit.selectEntities(sender, selector).stream()
                        .filter(Player.class::isInstance)
                        .map(Player.class::cast)
                        .toList();
            } catch (IllegalArgumentException e) {
                return List.of();
            }
        }
        Player target = Bukkit.getPlayer(selector);
        return target == null ? List.of() : List.of(target);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 1) {
            return filterByPrefix(Stream.of("reload", "give", "view_craft", "play_audio"), args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return filterByPrefix(Stream.concat(
                    Stream.of("@a", "@p", "@s", "@r"),
                    Bukkit.getOnlinePlayers().stream().map(Player::getName)
            ), args[1]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("play_audio")) {
            return filterByPrefix(Stream.of("file", "url", "stop"), args[1]);
        }

        if (args[0].equalsIgnoreCase("play_audio") && (args[1].equalsIgnoreCase("file") || args[1].equalsIgnoreCase("url"))) {
            if (args.length == 3 && args[1].equalsIgnoreCase("file")) {
                return completeAudioFiles(args[2]);
            }
            if (args.length >= 4) {
                return filterByPrefix(Stream.concat(
                        Stream.of("all", "loop"),
                        IntStream.rangeClosed(1, config.maxFrequency()).mapToObj(String::valueOf)
                ), args[args.length - 1]);
            }
        }

        return Collections.emptyList();
    }

    private List<String> completeAudioFiles(String prefix) {
        File audioDir = new File(plugin.getDataFolder(), "audio");
        File[] files = audioDir.listFiles();
        if (files == null) return Collections.emptyList();
        return filterByPrefix(Arrays.stream(files).filter(File::isFile).map(File::getName), prefix);
    }

    private List<String> filterByPrefix(Stream<String> options, String prefix) {
        String lowered = prefix.toLowerCase();
        return options.filter(option -> option.toLowerCase().startsWith(lowered)).collect(Collectors.toList());
    }
}
