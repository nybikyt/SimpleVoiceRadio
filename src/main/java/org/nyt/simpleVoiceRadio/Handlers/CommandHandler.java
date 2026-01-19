package org.nyt.simpleVoiceRadio.Handlers;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.nyt.simpleVoiceRadio.Misc.Item;
import org.nyt.simpleVoiceRadio.Misc.RecipeHolder;
import org.nyt.simpleVoiceRadio.SimpleVoiceRadio;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class CommandHandler implements BasicCommand {
    private final SimpleVoiceRadio plugin;
    private final Item item;
    private final static Map<String, String> arguments = Map.ofEntries(
            Map.entry("reload", "simple_voice_radio.reload_config"),
            Map.entry("give", "simple_voice_radio.give"),
            Map.entry("view_craft", "simple_voice_radio.can_view_craft")
    );
    private final Component usage = Component.text("Usage: /simple_voice_radio " + arguments.keySet().stream().toList(), TextColor.color(214, 54, 67));
    private final Component noPermission = Component.text("You don't have permission to use this command!", TextColor.color(214, 54, 67));
    private final Component playerOnly = Component.text("Only players can use this command!", TextColor.color(214, 54, 67));

    public CommandHandler(SimpleVoiceRadio plugin, Item item) {
        this.plugin = plugin;
        this.item = item;
    }

    @Override
    public @Nullable String permission() {
        return "simple_voice_radio.command";
    }

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {

        CommandSender sender = stack.getSender();

        if (args.length == 0) {
            sender.sendMessage(usage);
            return;
        }

        String arg = args[0].toLowerCase();

        if (arguments.containsKey(arg)) {
            if (sender.hasPermission(arguments.get(arg))) {
                switch (arg) {
                    case "reload" -> {
                        try {
                            plugin.reloadConfig();
                            item.reloadCraft();
                            sender.sendMessage(Component.text("Config has been reloaded!", TextColor.color(245, 203, 78)));
                        } catch (Exception e) {
                            sender.sendMessage(Component.text("Failed to reload config!", TextColor.color(214, 54, 67)));
                            SimpleVoiceRadio.LOGGER.error("Failed to reload config", e);
                        }
                    }
                    case "give" -> {
                        if (sender instanceof Player player) {
                            ItemStack radioItem = this.item.getItem();
                            player.getInventory().addItem(radioItem);
                            player.sendMessage(Component.text("Radio has been given!", TextColor.color(245, 203, 78)));
                        } else sender.sendMessage(playerOnly);
                    }
                    case "view_craft" -> {
                        if (sender instanceof Player player) {
                            RecipeHolder holder = new RecipeHolder();
                            Inventory inventory = Bukkit.createInventory(
                                    holder,
                                    InventoryType.WORKBENCH,
                                    Component.text("Radio craft recipe")
                            );
                            holder.setInventory(inventory);
                            inventory.setContents(item.getRecipeIngredients());
                            player.openInventory(inventory);
                        } else sender.sendMessage(playerOnly);
                    }
                }
            } else sender.sendMessage(noPermission);
        }
        else sender.sendMessage(usage);
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack stack, @NotNull String @NotNull [] args) {
        if (args.length <= 1) {
            return arguments.entrySet().stream()
                    .filter(e -> stack.getSender().hasPermission(e.getValue()))
                    .map(Map.Entry::getKey)
                    .toList();
        }
        return List.of();
    }
}