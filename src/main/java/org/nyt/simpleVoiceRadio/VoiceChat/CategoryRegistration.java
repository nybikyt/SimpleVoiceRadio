package org.nyt.simpleVoiceRadio.VoiceChat;

import de.maxhenkel.voicechat.api.VolumeCategory;
import org.nyt.simpleVoiceRadio.SimpleVoiceRadio;
import org.nyt.simpleVoiceRadio.VoiceAddon;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

public class CategoryRegistration {
    private static final String RADIO_CATEGORY = "sv_radio";

    public static String getRadioCategory() {
        return RADIO_CATEGORY;
    }

    public void registerVolumeCategory() {
        try {
            VolumeCategory radioCategory = VoiceAddon.getApi().volumeCategoryBuilder()
                    .setId(RADIO_CATEGORY)
                    .setName("Radio")
                    .setNameTranslationKey("simple_voice_radio.category.name")
                    .setDescription("The volume of all radio-blocks")
                    .setDescriptionTranslationKey("simple_voice_radio.category.description")
                    .setIcon(loadIcon("assets/logo.png"))
                    .build();
            VoiceAddon.getApi().registerVolumeCategory(radioCategory);
        }
        catch (Exception e) {
            SimpleVoiceRadio.LOGGER.error("Failed to register volume category {}", e.getMessage());
        }
    }

    private int[][] loadIcon(String path) {
        try (InputStream stream = SimpleVoiceRadio.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) return null;

            BufferedImage image = ImageIO.read(stream);
            if (image == null || image.getWidth() != 16 || image.getHeight() != 16) {
                return null;
            }

            int[][] pixels = new int[16][16];
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    pixels[x][y] = image.getRGB(x, y);
                }
            }
            return pixels;

        } catch (IOException e) {
            SimpleVoiceRadio.LOGGER.error("Failed to load icon: {}", path, e);
            return null;
        }
    }
}
