package dev.nybikyt.simpleVoiceRadio.Voice;

import org.bukkit.Location;

public interface VoiceBackend {

    Encoder createEncoder();

    Decoder createDecoder();

    Channel createChannel(Location location);

    boolean hasPlayersInRange(Location location, double radius);

    interface Encoder {

        byte[] encode(short[] samples);

        void close();
    }

    interface Decoder {

        short[] decode(byte[] data);

        void close();
    }

    interface Channel {

        void send(byte[] opusData);

        void flush();
    }
}
