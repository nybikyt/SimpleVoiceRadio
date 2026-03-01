package dev.nybikyt.simpleVoiceRadio.Utils;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import dev.nybikyt.simpleVoiceRadio.Bridges.MineSkin;
import dev.nybikyt.simpleVoiceRadio.SimpleVoiceRadio;
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
    private final List<String> stateAssets = List.of(
            "assets/broadcast_state.png",
            "assets/input_state.png",
            "assets/listen_state.png",
            "assets/output_state.png"
    );
    private final String textureConfigPath = "texture.yml";
    private final String itemAssetPath = "assets/item.png";

    public YamlConfiguration getTextureConfig() {
        return textureConfig;
    }

    public SkinManager(SimpleVoiceRadio plugin) {
        this.plugin = plugin;
        this.api = new MineSkin(plugin);
        saveAssets();
    }

    public void reloadConfig() {
        File file = new File(plugin.getDataFolder(), textureConfigPath);
        textureConfig = YamlConfiguration.loadConfiguration(file);
    }

    public void saveConfig() {
        try {
            File file = new File(plugin.getDataFolder(), textureConfigPath);
            textureConfig.save(file);
        } catch (Exception e) {
            SimpleVoiceRadio.LOGGER.error("Failed to save texture config", e);
        }
    }

    private void saveAssets() {
        File textureConfigFile = new File(plugin.getDataFolder(), textureConfigPath);
        if (!textureConfigFile.exists()) {
            plugin.saveResource(textureConfigPath, false);
        }
        textureConfig = YamlConfiguration.loadConfiguration(textureConfigFile);

        saveResourceIfMissing(itemAssetPath);
        stateAssets.forEach(this::saveResourceIfMissing);
    }

    private void saveResourceIfMissing(String path) {
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) {
            plugin.saveResource(path, false);
        }
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
        api.validateApiKey().thenAccept(isValid -> {
            if (isValid) {
                processItemTexture();
                processStateTextures();
            }
        });
    }

    private void processItemTexture() {
        File itemFile = new File(plugin.getDataFolder(), itemAssetPath);
        if (!itemFile.exists()) return;

        try {
            String hash = calculateFileHash(itemFile);
            String savedHash = textureConfig.getString("parsed_textures.item.hash");

            if (hash.equals(savedHash)) return;

            SimpleVoiceRadio.LOGGER.info("Parsing texture: item");
            BufferedImage image = ImageIO.read(itemFile);

            transferTextureToHead("item", "item", image).thenAccept(value -> {
                if (value != null) {
                    textureConfig.set("parsed_textures.item.value", value);
                    textureConfig.set("parsed_textures.item.hash", hash);
                    saveConfig();
                    SimpleVoiceRadio.LOGGER.info("Done parsing texture: item");
                } else {
                    SimpleVoiceRadio.LOGGER.warn("Failed to parse texture: item");
                }
            });
        } catch (Exception e) {
            SimpleVoiceRadio.LOGGER.error("Failed to process texture: item", e);
        }
    }

    private void processStateTextures() {
        stateAssets.forEach(assetPath -> {
            File file = new File(plugin.getDataFolder(), assetPath);
            if (!file.exists()) return;

            try {
                String baseName = assetPath.substring(assetPath.lastIndexOf("/") + 1, assetPath.lastIndexOf("."));
                String hash = calculateFileHash(file);
                String savedHash = textureConfig.getString("parsed_textures." + baseName + ".hash");

                if (hash.equals(savedHash)) return;

                SimpleVoiceRadio.LOGGER.info("Parsing texture: {}", baseName);
                BufferedImage image = ImageIO.read(file);

                parseBlockTextures(baseName, image).thenAccept(textureList -> {
                    ConfigurationSection blockSection = textureConfig.getConfigurationSection("block");
                    int expectedCount = blockSection != null ? blockSection.getKeys(false).size() : 0;

                    if (textureList.size() == expectedCount) {
                        textureConfig.set("parsed_textures." + baseName + ".list", textureList);
                        textureConfig.set("parsed_textures." + baseName + ".hash", hash);
                        saveConfig();
                        SimpleVoiceRadio.LOGGER.info("Done parsing texture: {}", baseName);
                    } else {
                        SimpleVoiceRadio.LOGGER.warn("Failed parsing texture {}: {}/{} parts",
                                baseName, textureList.size(), expectedCount);
                    }
                });
            } catch (Exception e) {
                SimpleVoiceRadio.LOGGER.error("Failed to process texture: {}", assetPath, e);
            }
        });
    }

    private CompletableFuture<List<String>> parseBlockTextures(String baseName, BufferedImage sourceImage) {
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

    private CompletableFuture<String> transferTextureToHead(String name, String dataPath, BufferedImage image) {
        BufferedImage headTexture = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = headTexture.createGraphics();

        try {
            ConfigurationSection data = textureConfig.getConfigurationSection(dataPath);
            if (data == null) {
                return CompletableFuture.failedFuture(
                        new IllegalArgumentException("Data section not found: " + dataPath)
                );
            }

            for (String key : data.getKeys(false)) {
                ConfigurationSection sourceSection = data.getConfigurationSection(key);
                ConfigurationSection headSection = textureConfig.getConfigurationSection("head." + key);

                if (sourceSection != null && headSection != null) {
                    int srcX = sourceSection.getInt("x");
                    int srcY = sourceSection.getInt("y");
                    int dstX = headSection.getInt("x");
                    int dstY = headSection.getInt("y");

                    BufferedImage subImage = image.getSubimage(srcX, srcY, 8, 8);
                    graphics.drawImage(subImage, dstX, dstY, null);
                }
            }

            return api.uploadSkin(headTexture, name);
        } finally {
            graphics.dispose();
        }
    }
}