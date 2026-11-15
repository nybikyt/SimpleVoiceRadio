package dev.nybikyt.simpleVoiceRadio.Audio;

import dev.nybikyt.simpleVoiceRadio.api.AudioType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public final class Playback {

    private static final long HOLD_MILLIS = 300L;
    private static final AudioType[] TYPES = AudioType.values();

    private final AtomicLongArray lastAudio = new AtomicLongArray(TYPES.length);
    private final Map<UUID, AtomicLong> lastVoice = new ConcurrentHashMap<>();

    public void mark(AudioType type, UUID speaker, long now) {
        lastAudio.lazySet(type.ordinal(), now);
        if (speaker == null) return;

        AtomicLong last = lastVoice.get(speaker);
        if (last == null) {
            lastVoice.values().removeIf(time -> now - time.get() >= HOLD_MILLIS);
            last = lastVoice.computeIfAbsent(speaker, id -> new AtomicLong());
        }
        last.lazySet(now);
    }

    public boolean isPlaying(AudioType type) {
        return System.currentTimeMillis() - lastAudio.get(type.ordinal()) < HOLD_MILLIS;
    }

    public boolean isPlaying() {
        for (AudioType type : TYPES) {
            if (isPlaying(type)) return true;
        }
        return false;
    }

    public List<UUID> getSpeakers() {
        long now = System.currentTimeMillis();
        List<UUID> speakers = new ArrayList<>();
        lastVoice.forEach((id, time) -> {
            if (now - time.get() < HOLD_MILLIS) speakers.add(id);
        });
        return speakers;
    }
}
