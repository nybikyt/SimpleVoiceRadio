package org.nyt.simpleVoiceRadio.Utils;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import org.nyt.simpleVoiceRadio.SimpleVoiceRadio;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DisplayEntityManager {
    private final SimpleVoiceRadio plugin;
    private final SkinManager skinManager;

    public DisplayEntityManager(SimpleVoiceRadio plugin, SkinManager skinManager) {
        this.plugin = plugin;
        this.skinManager = skinManager;
    }

    public List<ItemDisplay> createItemDisplays(Location loc) {
        List<?> defaultTexture = skinManager.getTextureConfig().getList("parsed_textures.output_state.list");
        if (defaultTexture == null) return null;

        List<ItemDisplay> list = new ArrayList<>();

        int loopIndex = 0;
        for (Object skin : defaultTexture) {
            loopIndex++;
            for (int count = 1; count <= 2; count++) {

                ItemDisplay display = loc.getWorld().spawn(loc, ItemDisplay.class);
                ItemStack item = ItemStack.of(Material.PLAYER_HEAD);
                setSkullByValue((String) skin, item);

                display.setItemStack(item);
                display.setViewRange((float) plugin.getConfig().getDouble("radio-block.view_range", 64));

                int translationIndex = (count == 1) ? loopIndex : loopIndex + 4;

                Vector3f translation = parseVector(
                        skinManager.getTextureConfig().getString("offset." + translationIndex),
                        new Vector3f(0, 0, 0)
                );
                Vector3f scale = parseVector(
                        skinManager.getTextureConfig().getString("offset.scale"),
                        new Vector3f(1.001f, 1.001f, 1.001f)
                );

                updateTransformation(display, translation, null, scale, null);
                list.add(display);
            }
        }
        return list;
    }

    public void setStateSkin(List<ItemDisplay> displays, String state) {
        List<?> textureList = skinManager.getTextureConfig().getList("parsed_textures." + state + "_state.list");
        if (textureList == null || displays == null) return;

        int displayIndex = 0;

        for (Object skin : textureList) {
            for (int count = 1; count <= 2; count++) {
                if (displayIndex >= displays.size()) return;

                ItemDisplay display = displays.get(displayIndex);
                ItemStack item = ItemStack.of(Material.PLAYER_HEAD);
                setSkullByValue((String) skin, item);
                display.setItemStack(item);

                displayIndex++;
            }
        }
    }


    public TextDisplay createTextDisplay(Location loc, int frequency) {
        TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class);

        display.text(
                Component.text(
                        String.format(skinManager.getTextureConfig().getString(
                                "frequency_display.number_format", "%d"),
                                frequency
                        ),
                        TextColor.fromHexString(skinManager.getTextureConfig().getString(
                                "frequency_display.color", "#AA0000")
                        )
                )
        );
        display.setBackgroundColor(Color.fromARGB(0,0,0,0));
        display.setViewRange((float) plugin.getConfig().getDouble("radio-block.view_range", 64));

        display.setBrightness(new Display.Brightness(15,15));

        Vector3f scale = parseVector(
                skinManager.getTextureConfig().getString("frequency_display.scale"),
                new Vector3f(0, 0, 0)
        );

        Vector3f translation = parseVector(
                skinManager.getTextureConfig().getString("frequency_display.offset"),
                new Vector3f(0, 0, 0)
        );

        Quaternionf leftRot = new Quaternionf().rotateY((float) Math.toRadians(180));
        updateTransformation(display, translation, leftRot, scale, null);

        return display;
    }

    public void setSkullByValue(String base64, ItemStack item) {
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


