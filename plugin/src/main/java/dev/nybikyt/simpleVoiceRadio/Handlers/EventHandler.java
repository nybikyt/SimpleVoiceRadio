package dev.nybikyt.simpleVoiceRadio.Handlers;

import dev.nybikyt.simpleVoiceRadio.Misc.Item;
import dev.nybikyt.simpleVoiceRadio.Misc.RecipeHolder;
import dev.nybikyt.simpleVoiceRadio.SimpleVoiceRadio;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager.Radio;
import dev.nybikyt.simpleVoiceRadio.api.RadioBlock;
import dev.nybikyt.simpleVoiceRadio.api.RadioState;
import dev.nybikyt.simpleVoiceRadio.api.events.RadioBreakEvent;
import dev.nybikyt.simpleVoiceRadio.api.events.RadioFrequencyChangeEvent;
import dev.nybikyt.simpleVoiceRadio.api.events.RadioPlaceEvent;
import dev.nybikyt.simpleVoiceRadio.Utils.DisplayEntityManager;
import dev.nybikyt.simpleVoiceRadio.Utils.JukeboxManager;
import dev.nybikyt.simpleVoiceRadio.Utils.PluginConfig;
import dev.nybikyt.simpleVoiceRadio.Utils.Scheduler;
import dev.nybikyt.simpleVoiceRadio.Voice.VoiceAddon;
import dev.nybikyt.simpleVoiceRadio.Audio.AntennaManager;
import dev.nybikyt.simpleVoiceRadio.Audio.SignalManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
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
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.HopperInventorySearchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.JukeboxInventory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EventHandler implements Listener {

    private static final Material RADIO_MATERIAL = Material.JUKEBOX;

    private final SimpleVoiceRadio plugin;
    private final PluginConfig config;
    private final DataManager dataManager;
    private final DisplayEntityManager displayEntityManager;
    private final VoiceAddon voiceAddon;
    private final Item item;

    public EventHandler(SimpleVoiceRadio plugin, PluginConfig config, DataManager dataManager, DisplayEntityManager displayEntityManager, VoiceAddon voiceAddon, Item item) {
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

    public RadioBlock wrap(Location location, Radio radio) {
        return new ApiRadio(plugin, location, radio);
    }

    public void updateRadioData(Location location, Radio radio, int frequency) {
        dataManager.updateFrequency(location, frequency);

        TextDisplay display = radio.getFrequencyDisplay();
        if (display != null) {
            display.text(displayEntityManager.formatFrequency(frequency));
        }
        if (frequency <= 0 && config.redstoneFrequency() && dataManager.forceState(location, RadioState.OUTPUT)) {
            applyState(location, radio, RadioState.OUTPUT);
        }
    }

    public boolean switchState(Location location, Radio radio, RadioState state, Player player) {
        if (!dataManager.updateState(location, state, player)) return false;

        applyState(location, radio, state);
        return true;
    }

    private void applyState(Location location, Radio radio, RadioState state) {
        if (state == RadioState.INPUT && config.signalSystemActive()) {
            SignalManager.setLevel(plugin, location, radio, 0, false);
        }
        displayEntityManager.applyStateSkin(radio);
        invalidateChannels(location);
    }

    public RadioBlock createRadio(Block block, float yaw, int frequency, Player player) {
        Radio oldRadio = dataManager.get(block.getLocation());
        if (oldRadio != null) breakRadio(block, oldRadio, false, false);

        Location center = block.getLocation().toCenterLocation();
        center.setPitch(0f);
        center.setYaw(yaw);

        Location offset = center.clone().add(0, 1, 0);
        List<ItemDisplay> itemDisplays = displayEntityManager.createItemDisplays(offset);
        TextDisplay textDisplay = displayEntityManager.createTextDisplay(offset, frequency);

        Radio radio = dataManager.add(block.getLocation(), frequency, RadioState.OUTPUT, itemDisplays, textDisplay);
        block.setBlockData(Bukkit.createBlockData(RADIO_MATERIAL), true);

        RadioBlock radioBlock = wrap(block.getLocation(), radio);
        new RadioPlaceEvent(radioBlock, player).callEvent();
        return radioBlock;
    }

    public void breakRadio(Block block, Radio radio, boolean shouldModify, boolean shouldDropItem) {
        breakRadio(block, radio, shouldModify, shouldDropItem, null);
    }

    public void breakRadio(Block block, Radio radio, boolean shouldModify, boolean shouldDropItem, Player player) {
        if (!dataManager.remove(block.getLocation(), radio)) return;
        new RadioBreakEvent(wrap(block.getLocation(), radio), player).callEvent();

        radio.getTextures().forEach(Entity::remove);
        TextDisplay display = radio.getFrequencyDisplay();
        if (display != null) display.remove();

        if (block.getType() == RADIO_MATERIAL && block.getState() instanceof Jukebox jukebox) {
            jukebox.setRecord(null);
            jukebox.update();
        }

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
            int frequency = config.redstoneFrequency() ? block.getBlockPower() : 1;
            createRadio(block, yaw, frequency, event.getPlayer());
        }, 1L);
    }

    @org.bukkit.event.EventHandler
    public void onCraftPrepare(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || !Item.isRadioItem(result.getItemMeta())) return;

        HumanEntity viewer = event.getInventory().getViewers().isEmpty()
                ? null
                : event.getInventory().getViewers().get(0);

        if (viewer instanceof Player player && !player.hasPermission("simple_voice_radio.can_craft")) {
            event.getInventory().setResult(null);
        }
    }

    @org.bukkit.event.EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreakDrops(BlockBreakEvent event) {
        if (dataManager.get(event.getBlock().getLocation()) != null) event.setDropItems(false);
    }

    @org.bukkit.event.EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Radio radio = dataManager.get(block.getLocation());
        if (radio == null) return;

        boolean shouldDrop = event.getPlayer().getGameMode() != GameMode.CREATIVE && radio.getState() != RadioState.DESTROYED;
        breakRadio(block, radio, false, shouldDrop, event.getPlayer());
    }

    @org.bukkit.event.EventHandler(ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (isRadioInventory(event.getSource()) || isRadioInventory(event.getDestination())) {
            event.setCancelled(true);
        }
    }

    @org.bukkit.event.EventHandler
    public void onHopperSearch(HopperInventorySearchEvent event) {
        if (event.getInventory() instanceof JukeboxInventory && dataManager.get(event.getSearchBlock().getLocation()) != null) {
            event.setInventory(null);
        }
    }

    @org.bukkit.event.EventHandler(ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (JukeboxManager.isSignalDisc(event.getEntity().getItemStack())) event.setCancelled(true);
    }

    @org.bukkit.event.EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Block block = event.getBlock();
        Radio radio = dataManager.get(block.getLocation());
        if (radio == null) return;

        event.setCancelled(true);
        breakRadio(block, radio, true, false);
    }

    @org.bukkit.event.EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        Chunk chunk = event.getChunk();
        List<Radio> radios = dataManager.getRadiosInChunk(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        if (radios.isEmpty()) return;

        Map<UUID, Entity> entities = new HashMap<>();
        event.getEntities().forEach(entity -> entities.put(entity.getUniqueId(), entity));
        radios.forEach(radio -> displayEntityManager.refreshDisplays(radio, entities));
    }

    private boolean isRadioInventory(Inventory inventory) {
        if (!(inventory instanceof JukeboxInventory)) return false;
        Location location = inventory.getLocation();
        return location != null && dataManager.get(location) != null;
    }

    @org.bukkit.event.EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;

        Block block = event.getClickedBlock();
        Radio radio = dataManager.get(block.getLocation());
        if (radio == null) return;

        event.setCancelled(true);
        if (radio.getState() == RadioState.DESTROYED) return;

        Player player = event.getPlayer();

        ItemStack heldItem = event.getItem();
        if (heldItem != null && AntennaManager.isLightningRod(heldItem.getType())) {
            placeLightningRod(player, block, heldItem, event.getBlockFace(), event.getHand());
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) return;

        boolean redstoneMode = config.redstoneFrequency();
        int currentPower = block.getBlockPower();
        RadioBlock radioBlock = wrap(block.getLocation(), radio);

        if (player.isSneaking() && !redstoneMode && player.hasPermission("simple_voice_radio.can_change_frequency")) {
            int frequency = radio.getFrequency() + 1;
            if (frequency > config.maxFrequency()) frequency = 1;
            if (frequency == radio.getFrequency()) return;
            if (!new RadioFrequencyChangeEvent(radioBlock, player, radio.getFrequency(), frequency).callEvent()) return;

            player.getWorld().playSound(block.getLocation().toCenterLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, SoundCategory.MASTER, 1f, 2f);
            updateRadioData(block.getLocation(), radio, frequency);
        } else if (player.hasPermission("simple_voice_radio.can_switch_mode")) {
            if (redstoneMode && currentPower <= 0 || radio.getState() == RadioState.BROADCAST || radio.getState() == RadioState.LISTEN) return;

            RadioState newState = radio.getState() == RadioState.INPUT ? RadioState.OUTPUT : RadioState.INPUT;
            if (!switchState(block.getLocation(), radio, newState, player)) return;

            player.getWorld().playSound(block.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, SoundCategory.MASTER, 3, 2);
            if (redstoneMode && currentPower != radio.getFrequency()
                    && new RadioFrequencyChangeEvent(radioBlock, null, radio.getFrequency(), currentPower).callEvent()) {
                updateRadioData(block.getLocation(), radio, currentPower);
            }
        }
    }

    public boolean destroyRadio(Location location, Radio radio) {
        if (!dataManager.updateState(location, RadioState.DESTROYED, null)) return false;

        TextDisplay display = radio.getFrequencyDisplay();
        if (display != null) display.text(Component.empty());
        displayEntityManager.applyStateSkin(radio);

        if (config.signalSystemActive()) SignalManager.setLevel(plugin, location, radio, 0, false);
        invalidateChannels(location);
        forgetAntenna(location);

        location.getWorld().playSound(location.toCenterLocation(), Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1f, 0.6f);
        return true;
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

    private void placeLightningRod(Player player, Block radioBlock, ItemStack heldItem, BlockFace face, EquipmentSlot hand) {
        Block target = radioBlock.getRelative(face);
        if (!target.getType().isAir()) return;

        BlockState replacedState = target.getState();
        Directional rodData = (Directional) heldItem.getType().createBlockData();
        rodData.setFacing(face);
        target.setBlockData(rodData, true);

        BlockPlaceEvent placeEvent = new BlockPlaceEvent(target, replacedState, radioBlock, heldItem, player, true, hand);
        Bukkit.getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled() || !placeEvent.canBuild()) {
            replacedState.update(true, false);
            return;
        }

        if (player.getGameMode() != GameMode.CREATIVE) {
            heldItem.setAmount(heldItem.getAmount() - 1);
        }
        player.getWorld().playSound(target.getLocation(), Sound.BLOCK_COPPER_PLACE, SoundCategory.BLOCKS, 1f, 1f);
        if (hand == EquipmentSlot.HAND) {
            player.swingMainHand();
        } else {
            player.swingOffHand();
        }
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
            Location location = event.getBlock().getLocation();
            int power = event.getBlock().getBlockPower();
            if (power == radio.getFrequency()) return;
            if (!new RadioFrequencyChangeEvent(wrap(location, radio), null, radio.getFrequency(), power).callEvent()) return;

            updateRadioData(location, radio, power);
        }
    }
}
