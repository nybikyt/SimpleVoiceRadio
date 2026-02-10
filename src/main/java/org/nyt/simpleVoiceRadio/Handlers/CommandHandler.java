package org.nyt.simpleVoiceRadio.Handlers;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.math.BlockPosition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.nyt.simpleVoiceRadio.Misc.Item;
import org.nyt.simpleVoiceRadio.Misc.RecipeHolder;
import org.nyt.simpleVoiceRadio.SimpleVoiceRadio;
import org.nyt.simpleVoiceRadio.Utils.SkinManager;
import java.util.List;


public class CommandHandler {
    private final SimpleVoiceRadio plugin;
    private final Item item;
    private final SkinManager skinManager;
    private final EventHandler eventHandler;

    public CommandHandler(SimpleVoiceRadio plugin, Item item, SkinManager skinManager, EventHandler eventHandler) {
        this.plugin = plugin;
        this.item = item;
        this.skinManager = skinManager;
        this.eventHandler = eventHandler;
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
                                    Component.text("Config has been reloaded!", TextColor.color(245, 203, 78))
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

                                    ctx.getSource().getSender().sendMessage(Component.text("Radio has been given!", TextColor.color(245, 203, 78)));
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


// Removed until better times

//                .then(Commands.literal("set_block")
//                        .requires(source -> source.getSender().hasPermission("simple_voice_radio.can_set_block"))
//                        .then(Commands.argument("position", ArgumentTypes.blockPosition())
//                                .then(Commands.argument("world", ArgumentTypes.world())
//
//                                        .executes(ctx -> {
//                                            World world = ctx.getArgument("world", World.class);
//                                            BlockPositionResolver resolver = ctx.getArgument("position", BlockPositionResolver.class);
//                                            BlockPosition blockPosition = resolver.resolve(ctx.getSource());
//
//                                            Location location = new Location(world, blockPosition.blockX(), blockPosition.blockY(), blockPosition.blockZ());
//
//                                            return Command.SINGLE_SUCCESS;
//                                        }))))

                .build();
    }
}