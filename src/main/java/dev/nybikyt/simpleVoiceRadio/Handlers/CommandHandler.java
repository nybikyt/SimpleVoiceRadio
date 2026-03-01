package dev.nybikyt.simpleVoiceRadio.Handlers;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import dev.nybikyt.simpleVoiceRadio.Bridges.JavaZoom;
import dev.nybikyt.simpleVoiceRadio.Misc.Item;
import dev.nybikyt.simpleVoiceRadio.Misc.RecipeHolder;
import dev.nybikyt.simpleVoiceRadio.SimpleVoiceRadio;
import dev.nybikyt.simpleVoiceRadio.Utils.SkinManager;
import dev.nybikyt.simpleVoiceRadio.VoiceAddon;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.List;


public class CommandHandler {
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

    public LiteralCommandNode<CommandSourceStack> createCommand() {
        return Commands.literal("simple_voice_radio")
                .requires(source -> source.getSender().hasPermission("simple_voice_radio.command"))

                .then(Commands.literal("reload")
                        .requires(source -> source.getSender().hasPermission("simple_voice_radio.reload_config"))
                        .executes(ctx -> {
                            plugin.reloadConfig();
                            skinManager.reloadConfig();
                            item.reloadCraft();

                            ctx.getSource().getSender().sendMessage(
                                    Component.text("Config has been reloaded!", successColor)
                            );

                            return Command.SINGLE_SUCCESS;
                        }))

                .then(Commands.literal("give")
                        .requires(source -> source.getSender().hasPermission("simple_voice_radio.give"))
                        .then(Commands.argument("target", ArgumentTypes.players())
                                .executes(ctx -> {
                                    PlayerSelectorArgumentResolver resolver = ctx.getArgument("target", PlayerSelectorArgumentResolver.class);
                                    List<Player> players = resolver.resolve(ctx.getSource());

                                    players.forEach(target -> target.getInventory().addItem(item.getItem()));

                                    ctx.getSource().getSender().sendMessage(Component.text("Radio has been given!", successColor));
                                    return Command.SINGLE_SUCCESS;
                                })))

                .then(Commands.literal("view_craft")
                        .requires(source -> source.getSender().hasPermission("simple_voice_radio.can_view_craft"))
                        .executes(ctx -> {
                            if (!(ctx.getSource().getExecutor() instanceof Player player)) return 0;

                            RecipeHolder holder = new RecipeHolder();
                            Inventory inventory = Bukkit.createInventory(holder, InventoryType.WORKBENCH, Component.text("Radio craft recipe"));
                            holder.setInventory(inventory);
                            inventory.setContents(item.getRecipeIngredients());

                            player.openInventory(inventory);
                            return Command.SINGLE_SUCCESS;
                        }))

                .then(Commands.literal("play_audio")
                        .requires(source -> source.getSender().hasPermission("simple_voice_radio.play_audio"))

                        .then(Commands.literal("file")
                                .requires(source -> !JavaZoom.isStreaming())
                                .then(Commands.argument("filename", StringArgumentType.greedyString())
                                        .suggests((ctx, builder) -> {
                                            File audioDirectory = new File(plugin.getDataFolder(), "audio");
                                            File[] files = audioDirectory.listFiles();

                                            if (files != null) {
                                                Arrays.stream(files)
                                                        .filter(File::isFile)
                                                        .map(File::getName)
                                                        .forEach(builder::suggest);
                                            }

                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            CommandSender sender = ctx.getSource().getSender();
                                            String filename = StringArgumentType.getString(ctx, "filename").replace(" ", "");

                                            File audioFile = new File(plugin.getDataFolder(), "audio/" + filename);
                                            if (!audioFile.exists()) {
                                                sender.sendMessage(
                                                        Component.text("File not found in " + plugin.getDataFolder().toPath().relativize(audioFile.toPath()) + ": ", errorColor)
                                                                .append(Component.text(filename, miscColor))
                                                );

                                                return 0;
                                            }

                                            try {
                                                AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(audioFile);
                                                voiceAddon.getUtils().playFile(audioInputStream);
                                                sender.sendMessage(startStreaming.append(Component.text(filename, miscColor)));
                                            } catch (Exception e) {
                                                sender.sendMessage(playBackError.append(Component.text(e.getMessage(), miscColor)));
                                                SimpleVoiceRadio.LOGGER.warn(playBackError.content() + e);
                                            }

                                            return Command.SINGLE_SUCCESS;
                                        })))

                        .then(Commands.literal("url")
                                .requires(source -> !JavaZoom.isStreaming())
                                .then(Commands.argument("url", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            CommandSender sender = ctx.getSource().getSender();
                                            String url = StringArgumentType.getString(ctx, "url").replace(" ", "");

                                            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                                sender.sendMessage(Component.text("Invalid URL: must start with http:// or https://", errorColor));
                                                return 0;
                                            }

                                            try {
                                                AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(new URI(url).toURL());
                                                voiceAddon.getUtils().playFile(audioInputStream);
                                                sender.sendMessage(startStreaming.append(Component.text(url, miscColor)));
                                            } catch (Exception e) {
                                                sender.sendMessage(playBackError.append(Component.text(e.getMessage(), miscColor)));
                                                SimpleVoiceRadio.LOGGER.warn(playBackError.content() + e);
                                            }

                                            return Command.SINGLE_SUCCESS;
                                        })))

                        .then(Commands.literal("stop")
                                .executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getSender();

                                    if (!JavaZoom.isStreaming()) {
                                        sender.sendMessage(Component.text("Nothing is playing.", errorColor));
                                        return 0;
                                    }

                                    JavaZoom.stopStreaming();
                                    sender.sendMessage(Component.text("Playback stopped.", successColor));
                                    return Command.SINGLE_SUCCESS;
                                })))

                .build();
    }
}