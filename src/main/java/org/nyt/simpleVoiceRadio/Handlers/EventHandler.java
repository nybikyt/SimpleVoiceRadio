package org.nyt.simpleVoiceRadio.Handlers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.nyt.simpleVoiceRadio.Misc.Item;
import org.nyt.simpleVoiceRadio.Misc.RecipeHolder;
import org.nyt.simpleVoiceRadio.SimpleVoiceRadio;
import org.nyt.simpleVoiceRadio.Utils.DataManager;
import org.nyt.simpleVoiceRadio.Utils.DisplayEntityManager;
import org.nyt.simpleVoiceRadio.VoiceAddon;
import java.util.List;

public class EventHandler implements Listener {
    private final SimpleVoiceRadio plugin;
    private final Material material = Material.JUKEBOX;
    private final DataManager dataManager;
    private final DisplayEntityManager displayEntityManager;
    private final VoiceAddon addon;
    private final Item item;

    public EventHandler(SimpleVoiceRadio plugin, DataManager dataManager, DisplayEntityManager displayEntityManager, VoiceAddon addon, Item item) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.displayEntityManager = displayEntityManager;
        this.addon = addon;
        this.item = item;
    }

    private void updateRadioData(DataManager.RadioData blockData, int power) {
        blockData.setFrequency(power);
        if (power > 0) {
            blockData.getFrequencyDisplay().text(Component.text(String.valueOf(blockData.getFrequency()), NamedTextColor.DARK_RED));
        } else {
            blockData.getFrequencyDisplay().text(Component.empty());
            if (plugin.getConfig().getBoolean("radio-block.redstone_frequency", false)) {
                blockData.setState("output");
            }
        }
        displayEntityManager.setStateSkin(blockData.getTextures().get(3), blockData.getState());
    }

    private void breakRadio(Block block, DataManager.RadioData blockData, Boolean shouldModify, Boolean shouldDropItem) {
        blockData.getTextures().forEach(Entity::remove);
        blockData.getFrequencyDisplay().remove();
        Jukebox jukebox = (Jukebox) block.getState();

        dataManager.removeBlock(block.getLocation());
        if (addon != null) {
            addon.deleteChannel(block.getLocation());
            addon.updateOutputChannels();
        }

        jukebox.setRecord(null);
        jukebox.update();

        if ( shouldModify ) block.setType(Material.AIR);
        if ( shouldDropItem ) block.getWorld().dropItemNaturally(block.getLocation(), item.getItem());
    }

    @org.bukkit.event.EventHandler
    public void onRecipeInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof RecipeHolder) {
            event.setCancelled(true);
        }
    }

    @org.bukkit.event.EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!event.getItemInHand().getPersistentDataContainer().has(NamespacedKey.fromString("radio"), PersistentDataType.BOOLEAN)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            float yaw = Math.round(event.getPlayer().getYaw() / 90f) * 90f;
            Location center = event.getBlock().getLocation().toCenterLocation();
            center.getBlock().setBlockData(Bukkit.createBlockData(material), true);
            center.setPitch(0f);
            center.setYaw(yaw);

            Location offset = center.clone().add(0,1,0);
            List<ItemDisplay> itemDisplays = displayEntityManager.createItemDisplays(offset);
            int frequency = plugin.getConfig().getBoolean("radio-block.redstone_frequency", false) ? event.getBlock().getBlockPower() : 1;

            TextDisplay textDisplay = displayEntityManager.createTextDisplay(offset, frequency);

            dataManager.setBlock(event.getBlock().getLocation(), frequency, "output", itemDisplays, textDisplay);
            updateRadioData(dataManager.getBlock(event.getBlock().getLocation()), frequency);

            if (addon != null) addon.createChannel(event.getBlock().getLocation());
        }, 1L);
    }

    @org.bukkit.event.EventHandler
    public void onCraftPrepare(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (event.getView().getPlayer() instanceof Player player && result != null) {
            if ( result.getPersistentDataContainer().has(NamespacedKey.fromString("radio")) && !player.hasPermission("simple_voice_radio.can_craft") ) {
                event.getInventory().setResult(null);
            }
        }
    }

    @org.bukkit.event.EventHandler
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        DataManager.RadioData blockData = dataManager.getBlock(block.getLocation());
        if (blockData == null) return;

        event.setDropItems(false);

        if (event.getPlayer().getGameMode().equals(GameMode.CREATIVE)) {
            breakRadio(event.getBlock(), blockData, false, false);
            return;
        }
        breakRadio(event.getBlock(), blockData, false, true);
    }

    @org.bukkit.event.EventHandler
    public void onExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> {
            DataManager.RadioData blockData = dataManager.getBlock(block.getLocation());
            if (blockData != null) {
                breakRadio(block, blockData, false, false);
                return true;
            }
            return false;
        });
    }

    @org.bukkit.event.EventHandler
    public void onEntityExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> {
            DataManager.RadioData blockData = dataManager.getBlock(block.getLocation());
            if (blockData != null) {
                breakRadio(block, blockData, false, false);
                return true;
            }
            return false;
        });
    }

    @org.bukkit.event.EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.getAction().equals(Action.RIGHT_CLICK_BLOCK) || !event.getHand().equals(EquipmentSlot.HAND) || event.getClickedBlock() == null) {
            return;
        }

        DataManager.RadioData blockData = dataManager.getBlock(event.getClickedBlock().getLocation());
        if (blockData == null) return;

        ItemStack item = event.getItem() == null ? new ItemStack(Material.AIR) : event.getItem();
        boolean isRadio = item.getItemMeta() != null && item.getItemMeta().getPersistentDataContainer().has(NamespacedKey.fromString("radio"));

        if (isRadio) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        boolean redstoneMode = plugin.getConfig().getBoolean("radio-block.redstone_frequency", false);
        int currentPower = event.getClickedBlock().getBlockPower();

        int freq = blockData.getFrequency();
        String oldState = blockData.getState();

        if (player.isSneaking() && !redstoneMode && player.hasPermission("simple_voice_radio.can_change_frequency")) {
            freq = blockData.getFrequency() + 1;
            if (freq > plugin.getConfig().getInt("radio-block.max_frequency", 15)) freq = 1;
            player.getWorld().playSound(event.getClickedBlock().getLocation().toCenterLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, SoundCategory.MASTER, 1f, 2f);
        } else if (player.hasPermission("simple_voice_radio.can_switch_mode")) {
            if (redstoneMode && currentPower <= 0 || blockData.getState().equals("listen")) return;

            player.getWorld().playSound(event.getClickedBlock().getLocation(), Sound.BLOCK_COPPER_BULB_TURN_ON, SoundCategory.MASTER, 3, 0);

            if (blockData.getState().equals("input")) blockData.setState("output");
            else if (blockData.getState().equals("output")) blockData.setState("input");

            if (redstoneMode) freq = currentPower;
        }

        updateRadioData(blockData, freq);

        if (addon != null && !oldState.equals(blockData.getState())) addon.updateOutputChannels();
    }

    @org.bukkit.event.EventHandler
    public void onBlockPhysics(BlockPhysicsEvent event) {
        try {
            DataManager.RadioData blockData = dataManager.getBlock(event.getBlock().getLocation());
            if (blockData == null) return;

            if (!event.getBlock().getBlockData().getMaterial().equals(material)) {
                breakRadio(event.getBlock(), blockData, false, false);
            } else if (plugin.getConfig().getBoolean("radio-block.redstone_frequency", false)) {
                String oldState = blockData.getState();
                updateRadioData(blockData, event.getBlock().getBlockPower());

                if (addon != null && !oldState.equals(blockData.getState())) addon.updateOutputChannels();
            }
        } catch (Exception e) {
            SimpleVoiceRadio.LOGGER.error(e);
        }
    }
}