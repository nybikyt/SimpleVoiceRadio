package dev.nybikyt.simpleVoiceRadio.Utils;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.nybikyt.simpleVoiceRadio.SimpleVoiceRadio;
import dev.nybikyt.simpleVoiceRadio.Utils.DataManager.Radio;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DisplayEntityManager {

    private final SimpleVoiceRadio plugin;
    private final PluginConfig config;
    private final SkinManager skinManager;

    public DisplayEntityManager(SimpleVoiceRadio plugin, PluginConfig config, SkinManager skinManager) {
        this.plugin = plugin;
        this.config = config;
        this.skinManager = skinManager;
    }

    public List<ItemDisplay> createItemDisplays(Location loc) {
        List<?> defaultTexture = skinManager.getTextureConfig().getList("parsed_textures.output_state.list");
        if (defaultTexture == null) {
            SimpleVoiceRadio.LOGGER.warn("No parsed textures found, radio block will be invisible");
            return new ArrayList<>();
        }

        List<ItemDisplay> list = new ArrayList<>();

        int loopIndex = 0;
        for (Object skin : defaultTexture) {
            loopIndex++;
            for (int count = 1; count <= 2; count++) {

                ItemStack item = new ItemStack(Material.PLAYER_HEAD);
                setSkullByValue((String) skin, item);

                int finalCount = count;
                int finalLoopIndex = loopIndex;

                ItemDisplay display = loc.getWorld().spawn(loc, ItemDisplay.class, entity -> {
                    entity.setItemStack(item);
                    entity.setViewRange((float) config.viewRange() / 64);

                    int translationIndex = (finalCount == 1) ? finalLoopIndex : finalLoopIndex + 4;

                    Vector3f translation = parseVector(
                            skinManager.getTextureConfig().getString("offset." + translationIndex),
                            new Vector3f(0, 0, 0)
                    );
                    Vector3f scale = parseVector(
                            skinManager.getTextureConfig().getString("offset.scale"),
                            new Vector3f(1.001f, 1.001f, 1.001f)
                    );

                    updateTransformation(entity, translation, null, scale, null);
                });

                list.add(display);
            }
        }
        return list;
    }

    public TextDisplay createTextDisplay(Location loc, int frequency) {
        return loc.getWorld().spawn(loc, TextDisplay.class, entity -> {
            entity.text(formatFrequency(frequency));
            entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            entity.setViewRange((float) config.viewRange() / 64);
            entity.setBrightness(new Display.Brightness(15, 15));

            Vector3f scale = parseVector(
                    skinManager.getTextureConfig().getString("frequency_display.scale"),
                    new Vector3f(0, 0, 0)
            );
            Vector3f translation = parseVector(
                    skinManager.getTextureConfig().getString("frequency_display.offset"),
                    new Vector3f(0, 0, 0)
            );

            Quaternionf leftRotation = new Quaternionf().rotateY((float) Math.toRadians(180));
            updateTransformation(entity, translation, leftRotation, scale, null);
        });
    }

    public Component formatFrequency(int frequency) {
        return Component.text(
                String.format(skinManager.getTextureConfig().getString("frequency_display.number_format", "%d"), frequency),
                TextColor.fromHexString(skinManager.getTextureConfig().getString("frequency_display.color", "#AA0000"))
        );
    }

    public void applyStateSkin(Radio radio) {
        setStateSkin(radio.getTextures(), radio.getState().key());
    }

    public void scheduleStateSkin(Location location, Radio radio) {
        Scheduler.runAt(plugin, location, () -> applyStateSkin(radio));
    }

    public void setStateSkin(List<ItemDisplay> displays, String state) {
        List<?> textureList = skinManager.getTextureConfig().getList("parsed_textures." + state + "_state.list");
        if (textureList == null || displays == null) return;

        int displayIndex = 0;

        for (Object skin : textureList) {
            for (int count = 1; count <= 2; count++) {
                if (displayIndex >= displays.size()) return;

                ItemDisplay display = displays.get(displayIndex);
                ItemStack item = new ItemStack(Material.PLAYER_HEAD);
                setSkullByValue((String) skin, item);
                display.setItemStack(item);

                displayIndex++;
            }
        }
    }

    public void setSkullByValue(String base64, ItemStack item) {
        if (base64 == null || base64.isEmpty()) {
            SimpleVoiceRadio.LOGGER.warn("Skull skin is null or empty");
            return;
        }

        if (!(item.getItemMeta() instanceof SkullMeta meta)) return;

        try {
            UUID uuid = new UUID(base64.hashCode(), base64.hashCode());
            PlayerProfile profile = Bukkit.createProfile(uuid);
            profile.getProperties().clear();
            profile.getProperties().add(new ProfileProperty("textures", base64));
            meta.setPlayerProfile(profile);
            item.setItemMeta(meta);
        } catch (Exception e) {
            SimpleVoiceRadio.LOGGER.error("Failed to parse skull skin: {}", e.getMessage());
        }
    }

    private void updateTransformation(Display display, Vector3f translation, Quaternionf leftRotation, Vector3f scale, Quaternionf rightRotation) {
        if (display == null) return;

        Transformation oldTransformation = display.getTransformation();
        Transformation newTransformation = new Transformation(
                translation != null ? translation : oldTransformation.getTranslation(),
                leftRotation != null ? leftRotation : oldTransformation.getLeftRotation(),
                scale != null ? scale : oldTransformation.getScale(),
                rightRotation != null ? rightRotation : oldTransformation.getRightRotation()
        );
        display.setTransformation(newTransformation);
    }

    private Vector3f parseVector(String string, Vector3f defaultVector) {
        if (string == null || string.isEmpty()) return defaultVector;
        try {
            String[] parts = string.split(",");
            if (parts.length != 3) return defaultVector;
            return new Vector3f(Float.parseFloat(parts[0]), Float.parseFloat(parts[1]), Float.parseFloat(parts[2]));
        } catch (Exception e) {
            return defaultVector;
        }
    }
}
