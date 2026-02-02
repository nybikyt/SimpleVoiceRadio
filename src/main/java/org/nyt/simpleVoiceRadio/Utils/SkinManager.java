package org.nyt.simpleVoiceRadio.Utils;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.nyt.simpleVoiceRadio.Bridges.MineSkin;
import org.nyt.simpleVoiceRadio.SimpleVoiceRadio;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class SkinManager {
    private final SimpleVoiceRadio plugin;
    private YamlConfiguration textureConfig;
    private final MineSkin api;
    private final List<String> assets = List.of(
            "assets/broadcast_state.png",
            "assets/input_state.png",
            "assets/listen_state.png",
            "assets/output_state.png"
    );

    public YamlConfiguration getTextureConfig() {
        return textureConfig;
    }

    public SkinManager(SimpleVoiceRadio plugin) {
        this.plugin = plugin;
        this.api = new MineSkin(plugin);
    }


    public void prepare() {
        File textureConfigFile = new File(plugin.getDataFolder(), "texture.yml");
        if (!textureConfigFile.exists()) {
            plugin.saveResource("texture.yml", false);
        }

        textureConfig = YamlConfiguration.loadConfiguration(textureConfigFile);

        saveAssets();

        api.validateApiKey().thenAccept(isValid -> {
            if (isValid) {
                setup();
            }
        });
    }

    private void saveAssets() {
        assets.forEach(assetPath -> {
            File file = new File(plugin.getDataFolder(), assetPath);
            if (!file.exists()) {
                plugin.saveResource(assetPath, false);
            }
        });
    }

    private String calculateFileHash(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] hashBytes = md.digest(Files.readAllBytes(file.toPath()));
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public void setup() {
        assets.forEach(assetPath -> {
            File file = new File(plugin.getDataFolder(), assetPath);

            try {
                String baseName = assetPath.substring(assetPath.lastIndexOf("/") + 1, assetPath.lastIndexOf("."));
                String currentHash = calculateFileHash(file);
                String savedHash = textureConfig.getString("parsed_textures." + baseName + ".hash");

                if (savedHash == null || !savedHash.equals(currentHash)) {
                    SimpleVoiceRadio.LOGGER.info("Parsing texture: {}", baseName);

                    BufferedImage image = ImageIO.read(file);
                    ConfigurationSection blockSection = textureConfig.getConfigurationSection("block");
                    int expectedBlockCount = blockSection != null ? blockSection.getKeys(false).size() : 0;

                    parseTextures(baseName, image).thenAccept(textureList -> {
                        if (textureList.size() == expectedBlockCount) {
                            textureConfig.set("parsed_textures." + baseName + ".list", textureList);
                            textureConfig.set("parsed_textures." + baseName + ".hash", currentHash);

                            try {
                                textureConfig.save(new File(plugin.getDataFolder(), "texture.yml"));
                                SimpleVoiceRadio.LOGGER.info("Done parsing texture: {}", baseName);
                            } catch (Exception e) {
                                SimpleVoiceRadio.LOGGER.error("Failed to save texture config", e);
                            }
                        } else {
                            SimpleVoiceRadio.LOGGER.warn("Skipped saving texture {}: {}/{} parts parsed successfully. Keeping default values.",
                                    baseName, textureList.size(), expectedBlockCount);
                        }
                    });
                }
            } catch (Exception e) {
                SimpleVoiceRadio.LOGGER.error("Failed to process texture: {}", assetPath, e);
            }
        });
    }

    public CompletableFuture<List<String>> parseTextures(String baseName, BufferedImage sourceImage) {
        ConfigurationSection blockSection = textureConfig.getConfigurationSection("block");

        if (blockSection == null) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        List<CompletableFuture<String>> futures = blockSection.getKeys(false).stream()
                .map(blockKey -> transferTextureToHead(baseName, "block." + blockKey, sourceImage)
                        .exceptionally(throwable -> null))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList())
                );
    }

    public CompletableFuture<String> transferTextureToHead(String name, String dataPath, BufferedImage image) {
        BufferedImage headTexture = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = headTexture.createGraphics();

        try {
            ConfigurationSection data = textureConfig.getConfigurationSection(dataPath);

            if (data == null) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("Data section not found: " + dataPath));
            }

            for (String key : data.getKeys(false)) {
                ConfigurationSection value = data.getConfigurationSection(key);
                ConfigurationSection headData = textureConfig.getConfigurationSection("head." + key);

                if (value != null && headData != null) {
                    int x = value.getInt("x");
                    int y = value.getInt("y");
                    int headX = headData.getInt("x");
                    int headY = headData.getInt("y");

                    BufferedImage subImage = image.getSubimage(x, y, 8, 8);
                    graphics.drawImage(subImage, headX, headY, null);
                }
            }

            return api.uploadSkin(headTexture, name);

        } finally {
            graphics.dispose();
        }
    }
}