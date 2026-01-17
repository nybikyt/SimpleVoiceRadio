package org.nyt.simpleVoiceRadio.Utils;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.nyt.simpleVoiceRadio.SimpleVoiceRadio;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DisplayEntityManager {
    private final SimpleVoiceRadio plugin;

    public DisplayEntityManager(SimpleVoiceRadio plugin) {
        this.plugin = plugin;
    }

    public List<ItemDisplay> createItemDisplays(Location loc) {
        ConfigurationSection textureSection = plugin.getConfig().getConfigurationSection("radio-block.texture_parts");
        if (textureSection == null) return null;

        List<ItemDisplay> list = new ArrayList<>();

        textureSection.getKeys(false).forEach(key -> {
            ConfigurationSection partSection = textureSection.getConfigurationSection(key);
            if (partSection == null) return;

            ItemDisplay display = loc.getWorld().spawn(loc, ItemDisplay.class);

            ItemStack item = ItemStack.of(Material.PLAYER_HEAD);
            String skullSkin = partSection.getString("skull_skin");
            getSkullByValue(skullSkin, item);

            display.setItemStack(item);
            display.setViewRange(512);

            Vector3f translation = parseVector(
                    partSection.getString("translation"),
                    new Vector3f(0, 0, 0)
            );
            Vector3f scale = parseVector(
                    partSection.getString("scale"),
                    new Vector3f(1.001f, 1.001f, 1.001f)
            );

            updateTransformation(display, translation, null, scale, null);
            list.add(display);
        });

        return list;
    }

    public void setStateSkin(ItemDisplay display, String state) {
        String displaySkullSkin;
        if (state.equalsIgnoreCase("input")) {
            displaySkullSkin = plugin.getConfig().getString("radio-block.texture_parts.4.input_state");
        }
        else if (state.equalsIgnoreCase("listen")) {
            displaySkullSkin = plugin.getConfig().getString("radio-block.texture_parts.4.listen_state");
        }
        else {
            displaySkullSkin = plugin.getConfig().getString("radio-block.texture_parts.4.skull_skin");
        }
        ItemStack displayItem = display.getItemStack();
        getSkullByValue(displaySkullSkin, displayItem);
        display.setItemStack(displayItem);
    }


    public TextDisplay createTextDisplay(Location loc, int frequency) {
        TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class);

        display.text(Component.text(String.valueOf(frequency), NamedTextColor.DARK_RED));
        display.setBackgroundColor(Color.fromARGB(0,0,0,0));
        display.setViewRange(512);

        display.setBrightness(new Display.Brightness(15,15));
        Vector3f scale = new Vector3f(1.5f,1.435f,0);
        Vector3f translation = new Vector3f(0.0185f,-1.01f,-0.501f);

        Quaternionf leftRot = new Quaternionf().rotateY((float) Math.toRadians(180));
        updateTransformation(display, translation, leftRot, scale, null);

        return display;
    }

    public void getSkullByValue(String base64, ItemStack item) {
        if (base64 == null || base64.isEmpty()) {
            SimpleVoiceRadio.LOGGER.warn("Skull skin is null or empty");
            return;
        }

        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            try {
                UUID uuid = new UUID(base64.hashCode(), base64.hashCode());
                PlayerProfile profile = Bukkit.createProfile(uuid);

                ProfileProperty property = new ProfileProperty("textures", base64);
                profile.getProperties().clear();
                profile.getProperties().add(property);

                meta.setPlayerProfile(profile);
                item.setItemMeta(meta);
            }
            catch (Exception e) {
                SimpleVoiceRadio.LOGGER.error("Failed to parse skull skin: " + e.getMessage());
            }
        }
    }

    public void updateTransformation(Display display, Vector3f translation, Quaternionf leftRotation, Vector3f scale, Quaternionf rightRotation) {
        if (display == null) { return; }

        Transformation oldTrans = display.getTransformation();
        Transformation newTrans = new Transformation(
                translation != null ? translation : oldTrans.getTranslation(),
                leftRotation != null ? leftRotation : oldTrans.getLeftRotation(),
                scale != null ? scale : oldTrans.getScale(),
                rightRotation != null ? rightRotation : oldTrans.getRightRotation()
        );
        display.setTransformation(newTrans);
    }

    private Vector3f parseVector(String string, Vector3f defaultVector) {
        if (string == null || string.isEmpty()) return defaultVector;
        try {
            String[] parts = string.split(",");
            if (parts.length != 3) return defaultVector;
            float x = Float.parseFloat(parts[0]);
            float y = Float.parseFloat(parts[1]);
            float z = Float.parseFloat(parts[2]);
            return new Vector3f(x, y, z);
        } catch (Exception e) {
            return defaultVector;
        }
    }

}


