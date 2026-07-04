package dev.nybikyt.simpleVoiceRadio.Handlers;

import dev.nybikyt.simpleVoiceRadio.Misc.Item;
import dev.nybikyt.simpleVoiceRadio.Misc.RecipeHolder;
import dev.nybikyt.simpleVoiceRadio.SimpleVoiceRadio;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager.Radio;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager.RadioState;
import dev.nybikyt.simpleVoiceRadio.Utils.DisplayEntityManager;
import dev.nybikyt.simpleVoiceRadio.Utils.JukeboxManager;
import dev.nybikyt.simpleVoiceRadio.Utils.PluginConfig;
import dev.nybikyt.simpleVoiceRadio.Utils.Scheduler;
import dev.nybikyt.simpleVoiceRadio.SimpleVoiceAddon;
import dev.nybikyt.simpleVoiceRadio.Audio.AntennaManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Jukebox;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class EventHandler implements Listener {

    private static final Material RADIO_MATERIAL = Material.JUKEBOX;

    private final SimpleVoiceRadio plugin;
    private final PluginConfig config;
    private final DataManager dataManager;
    private final DisplayEntityManager displayEntityManager;
    private final SimpleVoiceAddon voiceAddon;
    private final Item item;

    public EventHandler(SimpleVoiceRadio plugin, PluginConfig config, DataManager dataManager, DisplayEntityManager displayEntityManager, SimpleVoiceAddon voiceAddon, Item item) {
        this.plugin = plugin;
        this.config = config;
        this.dataManager = dataManager;
        this.displayEntityManager = displayEntityManager;
        this.voiceAddon = voiceAddon;
        this.item = item;
    }

    private void invalidateChannels(Location location) {
        if (voiceAddon != null && voiceAddon.getChannelManager() != null) {
            voiceAddon.getChannelManager().invalidateRadio(location);
        }
    }

    private void forgetAntenna(Location location) {
        if (voiceAddon != null && voiceAddon.getAudioRouter() != null) {
            voiceAddon.getAudioRouter().getAntennaManager().forget(location);
        }
    }

    private void updateRadioData(Location location, Radio radio, int frequency) {
        dataManager.updateFrequency(location, frequency);

        TextDisplay display = radio.getFrequencyDisplay();
        if (display != null) {
            display.text(frequency > 0 ? displayEntityManager.formatFrequency(frequency) : Component.empty());
        }
        if (frequency <= 0 && config.redstoneFrequency()) {
            dataManager.updateState(location, RadioState.OUTPUT);
        }
        displayEntityManager.applyStateSkin(radio);
    }

    public void breakRadio(Block block, Radio radio, boolean shouldModify, boolean shouldDropItem) {
        radio.getTextures().forEach(Entity::remove);
        TextDisplay display = radio.getFrequencyDisplay();
        if (display != null) display.remove();

        if (block.getType() == RADIO_MATERIAL && block.getState() instanceof Jukebox jukebox) {
            jukebox.setRecord(null);
            jukebox.update();
        }

        dataManager.remove(block.getLocation());
        invalidateChannels(block.getLocation());
        forgetAntenna(block.getLocation());

        if (shouldModify) block.setType(Material.AIR);
        if (shouldDropItem) block.getWorld().dropItemNaturally(block.getLocation(), item.getItem());
    }

    @org.bukkit.event.EventHandler
    public void onRecipeInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof RecipeHolder) {
            event.setCancelled(true);
        }
    }

    @org.bukkit.event.EventHandler(priority = EventPriority.LOW)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!Item.isRadioItem(event.getItemInHand().getItemMeta())) return;
        if (dataManager.getRadioCountInChunk(event.getBlock().getLocation()) >= config.blocksPerChunkLimit()) {
            event.setCancelled(true);
            return;
        }

        Scheduler.runAtLater(plugin, event.getBlock().getLocation(), () -> {
            if (event.isCancelled()) return;

            Block block = event.getBlock();
            if (block.getType() != Material.PLAYER_HEAD && block.getType() != Material.PLAYER_WALL_HEAD) return;

            float yaw = Math.round(event.getPlayer().getLocation().getYaw() / 90f) * 90f;
            Location center = block.getLocation().toCenterLocation();
            center.setPitch(0f);
            center.setYaw(yaw);

            Location offset = center.clone().add(0, 1, 0);
            List<ItemDisplay> itemDisplays = displayEntityManager.createItemDisplays(offset);
            int frequency = config.redstoneFrequency() ? block.getBlockPower() : 1;

            TextDisplay textDisplay = displayEntityManager.createTextDisplay(offset, frequency);

            dataManager.add(block.getLocation(), frequency, RadioState.OUTPUT, itemDisplays, textDisplay);
            center.getBlock().setBlockData(Bukkit.createBlockData(RADIO_MATERIAL), true);
        }, 1L);
    }

    @org.bukkit.event.EventHandler
    public void onCraftPrepare(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || !Item.isRadioItem(result.getItemMeta())) return;

        HumanEntity viewer = event.getInventory().getViewers().isEmpty()
                ? null
                : event.getInventory().getViewers().getFirst();

        if (viewer instanceof Player player && !player.hasPermission("simple_voice_radio.can_craft")) {
            event.getInventory().setResult(null);
        }
    }

    @org.bukkit.event.EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Radio radio = dataManager.get(block.getLocation());
        if (radio == null) return;

        event.setDropItems(false);
        boolean shouldDrop = event.getPlayer().getGameMode() != GameMode.CREATIVE && radio.getState() != RadioState.DESTROYED;
        breakRadio(block, radio, false, shouldDrop);
    }

    @org.bukkit.event.EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> {
            Radio radio = dataManager.get(block.getLocation());
            if (radio != null) {
                breakRadio(block, radio, false, false);
                return true;
            }
            return false;
        });
    }

    @org.bukkit.event.EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }

        Block block = event.getClickedBlock();
        Radio radio = dataManager.get(block.getLocation());
        if (radio == null) return;

        if (radio.getState() == RadioState.DESTROYED) {
            event.setCancelled(true);
            return;
        }

        Player player = event.getPlayer();

        ItemStack heldItem = event.getItem();
        if (heldItem != null && AntennaManager.isLightningRod(heldItem.getType())) {
            event.setCancelled(true);
            placeLightningRod(player, block, heldItem, event.getBlockFace());
            return;
        }

        event.setCancelled(true);
        boolean redstoneMode = config.redstoneFrequency();
        int currentPower = block.getBlockPower();

        int frequency = radio.getFrequency();
        RadioState oldState = radio.getState();

        if (player.isSneaking() && !redstoneMode && player.hasPermission("simple_voice_radio.can_change_frequency")) {
            frequency = radio.getFrequency() + 1;
            if (frequency > config.maxFrequency()) frequency = 1;
            player.getWorld().playSound(block.getLocation().toCenterLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, SoundCategory.MASTER, 1f, 2f);
        } else if (player.hasPermission("simple_voice_radio.can_switch_mode")) {
            if (redstoneMode && currentPower <= 0 || radio.getState() == RadioState.BROADCAST || radio.getState() == RadioState.LISTEN) return;

            player.getWorld().playSound(block.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, SoundCategory.MASTER, 3, 2);

            if (radio.getState() == RadioState.INPUT) {
                dataManager.updateState(block.getLocation(), RadioState.OUTPUT);
            } else if (radio.getState() == RadioState.OUTPUT) {
                dataManager.updateState(block.getLocation(), RadioState.INPUT);
                if (config.signalSystemActive()) {
                    JukeboxManager.applyDisc(block.getLocation(), 0);
                }
            }

            if (redstoneMode) frequency = currentPower;
        }

        updateRadioData(block.getLocation(), radio, frequency);

        if (oldState != radio.getState()) {
            invalidateChannels(block.getLocation());
        }
    }

    private void destroyRadio(Location location, Radio radio) {
        dataManager.updateState(location, RadioState.DESTROYED);

        TextDisplay display = radio.getFrequencyDisplay();
        if (display != null) display.text(Component.empty());
        displayEntityManager.applyStateSkin(radio);

        if (config.signalSystemActive()) JukeboxManager.applyDisc(location, 0);
        invalidateChannels(location);
        forgetAntenna(location);

        location.getWorld().playSound(location.toCenterLocation(), Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1f, 0.6f);
    }

    @org.bukkit.event.EventHandler(ignoreCancelled = true)
    public void onLightningStrike(LightningStrikeEvent event) {
        if (!config.lightningDestroysRadio()) return;

        Block block = event.getLightning().getLocation().getBlock();
        if (!AntennaManager.isLightningRod(block.getType())) {
            block = block.getRelative(BlockFace.DOWN);
        }
        if (!AntennaManager.isLightningRod(block.getType())) return;

        int guard = 32;
        while (AntennaManager.isLightningRod(block.getType()) && guard-- > 0) {
            block = block.getRelative(BlockFace.DOWN);
        }

        Radio radio = dataManager.get(block.getLocation());
        if (radio == null || radio.getState() == RadioState.DESTROYED) return;

        destroyRadio(block.getLocation(), radio);
    }

    private void placeLightningRod(Player player, Block radioBlock, ItemStack heldItem, BlockFace face) {
        Block target = radioBlock.getRelative(face);
        if (!target.getType().isAir()) return;

        BlockState replacedState = target.getState();
        Directional rodData = (Directional) heldItem.getType().createBlockData();
        rodData.setFacing(face);
        target.setBlockData(rodData, true);

        BlockPlaceEvent placeEvent = new BlockPlaceEvent(target, replacedState, radioBlock, heldItem, player, true, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled() || !placeEvent.canBuild()) {
            replacedState.update(true, false);
            return;
        }

        if (player.getGameMode() != GameMode.CREATIVE) {
            heldItem.setAmount(heldItem.getAmount() - 1);
        }
        player.getWorld().playSound(target.getLocation(), Sound.BLOCK_COPPER_PLACE, SoundCategory.BLOCKS, 1f, 1f);
        player.swingMainHand();
    }

    @org.bukkit.event.EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Radio radio = dataManager.get(event.getBlock().getLocation());
        if (radio == null) return;

        if (event.getBlock().getType() != RADIO_MATERIAL) {
            breakRadio(event.getBlock(), radio, false, false);
            return;
        }

        if (radio.getState() == RadioState.DESTROYED) return;

        if (config.redstoneFrequency()) {
            RadioState oldState = radio.getState();
            updateRadioData(event.getBlock().getLocation(), radio, event.getBlock().getBlockPower());

            if (oldState != radio.getState()) {
                invalidateChannels(event.getBlock().getLocation());
            }
        }
    }
}
