package dev.nybikyt.simpleVoiceRadio.Bridges;

import org.mineskin.JsoupRequestHandler;
import org.mineskin.MineSkinClient;
import org.mineskin.data.Visibility;
import org.mineskin.request.GenerateRequest;
import dev.nybikyt.simpleVoiceRadio.SimpleVoiceRadio;

import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;

public class MineSkin {
    private final MineSkinClient CLIENT;

    public MineSkin(SimpleVoiceRadio plugin) {
        String apiKey = plugin.getConfig().getString("skin.api_key", null);

        this.CLIENT = MineSkinClient.builder()
                .requestHandler(JsoupRequestHandler::new)
                .userAgent("SimpleVoiceRadio/0.0.4")
                .apiKey(apiKey)
                .build();
    }

    public CompletableFuture<Boolean> validateApiKey() {
        return CLIENT.misc().getUser()
                .thenApply(userInfo -> {
                    SimpleVoiceRadio.LOGGER.info("MineSkin API key validated. Custom skins are available!");
                    return true;
                })
                .exceptionally(throwable -> {
                    SimpleVoiceRadio.LOGGER.warn("MineSkin API key invalid. Using default skin. Specify valid key in config.yml under skin.api_key");
                    return false;
                });
    }

    public CompletableFuture<String> uploadSkin(BufferedImage imageData, String name) {
        GenerateRequest request = GenerateRequest.upload(imageData)
                .name(name)
                .visibility(Visibility.PUBLIC);

        return CLIENT.queue().submit(request)
                .thenCompose(queueResponse -> queueResponse.getJob().waitForCompletion(CLIENT))
                .thenCompose(jobResponse -> jobResponse.getOrLoadSkin(CLIENT))
                .thenApply(skinInfo -> skinInfo.texture().data().value())
                .exceptionally(throwable -> {
                    SimpleVoiceRadio.LOGGER.error("Failed to upload skin - {}: {}", name, throwable.getMessage());
                    return null;
                });
    }
}