package dev.nybikyt.simpleVoiceRadio.Handlers;

import dev.nybikyt.simpleVoiceRadio.Misc.Item;
import dev.nybikyt.simpleVoiceRadio.Misc.RecipeHolder;
import dev.nybikyt.simpleVoiceRadio.SimpleVoiceRadio;
import dev.nybikyt.simpleVoiceRadio.Utils.Messages;
import dev.nybikyt.simpleVoiceRadio.Utils.PluginConfig;
import dev.nybikyt.simpleVoiceRadio.Utils.Scheduler;
import dev.nybikyt.simpleVoiceRadio.Utils.SkinManager;
import dev.nybikyt.simpleVoiceRadio.Voice.VoiceAddon;
import dev.nybikyt.simpleVoiceRadio.Audio.AudioStreamer;
import net.kyori.adventure.text.Component;
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
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class CommandHandler implements CommandExecutor, TabCompleter {

    private static final Map<String, String> SUB_PERMISSIONS = Map.of(
            "reload", "simple_voice_radio.reload_config",
            "give", "simple_voice_radio.give",
            "view_craft", "simple_voice_radio.can_view_craft",
            "play_audio", "simple_voice_radio.play_audio"
    );

    private final SimpleVoiceRadio plugin;
    private final PluginConfig config;
    private final Messages messages;
    private final Item item;
    private final SkinManager skinManager;
    private final VoiceAddon voiceAddon;

    public CommandHandler(SimpleVoiceRadio plugin, PluginConfig config, Messages messages, Item item, SkinManager skinManager, VoiceAddon voiceAddon) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.item = item;
        this.skinManager = skinManager;
        this.voiceAddon = voiceAddon;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) return false;

        String subcommand = args[0].toLowerCase();
        String permission = SUB_PERMISSIONS.get(subcommand);
        if (permission != null && !sender.hasPermission(permission)) {
            sender.sendMessage(messages.get("no-permission"));
            return true;
        }

        return switch (subcommand) {
            case "reload" -> handleReload(sender);
            case "give" -> handleGive(sender, args);
            case "view_craft" -> handleViewCraft(sender);
            case "play_audio" -> handlePlayAudio(sender, args);
            default -> {
                sender.sendMessage(messages.get("unknown-subcommand"));
                yield true;
            }
        };
    }

    private boolean handleReload(CommandSender sender) {
        config.reload();
        messages.reload();
        skinManager.reloadConfig();
        item.reloadCraft();
        sender.sendMessage(messages.get("reload.done"));
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (args.length < 2) return false;

        List<Player> targets = resolveTargets(sender, args[1]);
        if (targets.isEmpty()) {
            sender.sendMessage(messages.get("give.player-not-found", "player", args[1]));
            return true;
        }
        targets.forEach(target -> Scheduler.runFor(plugin, target, () -> giveRadio(target)));
        sender.sendMessage(messages.get("give.done", "count", String.valueOf(targets.size())));
        return true;
    }

    private void giveRadio(Player target) {
        target.getInventory().addItem(item.getItem()).values()
                .forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
    }

    private boolean handleViewCraft(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("players-only"));
            return true;
        }
        RecipeHolder holder = new RecipeHolder();
        Inventory inventory = Bukkit.createInventory(holder, InventoryType.WORKBENCH, messages.get("view-craft.title"));
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
            default -> sender.sendMessage(messages.get("unknown-subcommand"));
        }
        return true;
    }

    private void handlePlayAudioStop(CommandSender sender) {
        if (!AudioStreamer.isStreaming()) {
            sender.sendMessage(messages.get("play-audio.nothing-playing"));
            return;
        }
        AudioStreamer.stopStreaming();
        sender.sendMessage(messages.get("play-audio.stopped"));
    }

    private boolean handlePlayAudioFile(CommandSender sender, String[] args) {
        if (args.length < 3) return false;
        if (!canStartStream(sender)) return true;

        PlayArguments parsed = parsePlayArguments(args);
        if (parsed == null) return false;

        File audioDir = new File(plugin.getDataFolder(), "audio");
        File audioFile = new File(audioDir, parsed.value());
        if (!isInsideDirectory(audioFile, audioDir)) {
            sender.sendMessage(messages.get("play-audio.invalid-path"));
            return true;
        }
        if (!audioFile.exists()) {
            sender.sendMessage(messages.get("play-audio.file-not-found", "file", parsed.value()));
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
            sender.sendMessage(messages.get("play-audio.invalid-url"));
            return true;
        }

        startStream(sender, parsed, () -> AudioSystem.getAudioInputStream(new URI(url).toURL()), false);
        return true;
    }

    private boolean canStartStream(CommandSender sender) {
        if (voiceAddon == null || voiceAddon.getAudioRouter() == null) {
            sender.sendMessage(messages.get("play-audio.voice-unavailable"));
            return false;
        }
        if (AudioStreamer.isStreaming()) {
            sender.sendMessage(messages.get("play-audio.already-streaming"));
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
            sender.sendMessage(messages.get("play-audio.failed", "error", String.valueOf(e.getMessage())));
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

        if (rest.size() > 1 && rest.getLast().equalsIgnoreCase("loop")) {
            loop = true;
            rest.removeLast();
        }
        if (rest.size() > 1) {
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
        Component message = messages.get("play-audio.started", "source", parsed.value());
        if (parsed.frequency() != null) {
            message = message.append(messages.get("play-audio.started-frequency", "frequency", String.valueOf(parsed.frequency())));
        }
        if (parsed.loop()) {
            message = message.append(messages.get("play-audio.started-loop"));
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
            return filterByPrefix(Stream.of("reload", "give", "view_craft", "play_audio")
                    .filter(subcommand -> sender.hasPermission(SUB_PERMISSIONS.get(subcommand))), args[0]);
        }

        String permission = SUB_PERMISSIONS.get(args[0].toLowerCase());
        if (permission == null || !sender.hasPermission(permission)) return Collections.emptyList();

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
