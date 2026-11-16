package com.vku.chat.server;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class MediaPacket {
    public static final byte MAGIC_1 = 0x56; // 'V'
    public static final byte MAGIC_2 = 0x4B; // 'K'

    public static final byte TYPE_REGISTER = 1;
    public static final byte TYPE_VIDEO_FRAME = 2;
    public static final byte TYPE_AUDIO_CHUNK = 3;
    public static final byte TYPE_VIDEO_PAUSED = 4;
    private static final int HEADER_SIZE = 25;

    private byte type;
    private String sender;
    private String target;
    private int seq;
    private byte[] payload;
    private final UUID callId;

    public MediaPacket(byte type, UUID callId, String sender, String target, int seq, byte[] payload) {
        this.type = type;
        this.callId = callId;
        this.sender = sender != null ? sender : "";
        this.target = target != null ? target : "";
        this.seq = seq;
        this.payload = payload != null ? payload : new byte[0];
    }

    public byte getType() { return type; }
    public UUID getCallId() { return callId; }
    public String getSender() { return sender; }
    public String getTarget() { return target; }
    public int getSeq() { return seq; }
    public byte[] getPayload() { return payload; }

    public byte[] encode() {
        byte[] sBytes = sender.getBytes(StandardCharsets.UTF_8);
        byte[] tBytes = target.getBytes(StandardCharsets.UTF_8);
        int totalLen = HEADER_SIZE + sBytes.length + tBytes.length + payload.length;
        ByteBuffer buf = ByteBuffer.allocate(totalLen);
        buf.put(MAGIC_1);
        buf.put(MAGIC_2);
        buf.put(type);
        buf.putLong(callId.getMostSignificantBits());
        buf.putLong(callId.getLeastSignificantBits());
        buf.put((byte) sBytes.length);
        buf.put(sBytes);
        buf.put((byte) tBytes.length);
        buf.put(tBytes);
        buf.putInt(seq);
        buf.put(payload);
        return buf.array();
    }

    public static MediaPacket decode(byte[] data, int length) {
        if (data == null || length < HEADER_SIZE) return null;
        try {
            ByteBuffer buf = ByteBuffer.wrap(data, 0, length);
            byte m1 = buf.get();
            byte m2 = buf.get();
            if (m1 != MAGIC_1 || m2 != MAGIC_2) return null;
            byte type = buf.get();
            UUID callId = new UUID(buf.getLong(), buf.getLong());
            int sLen = buf.get() & 0xFF;
            if (buf.remaining() < sLen + 1) return null;
            byte[] sBytes = new byte[sLen];
            buf.get(sBytes);
            String sender = new String(sBytes, StandardCharsets.UTF_8);

            int tLen = buf.get() & 0xFF;
            if (buf.remaining() < tLen + 4) return null;
            byte[] tBytes = new byte[tLen];
            buf.get(tBytes);
            String target = new String(tBytes, StandardCharsets.UTF_8);

            int seq = buf.getInt();
            byte[] payload = new byte[buf.remaining()];
            buf.get(payload);

            return new MediaPacket(type, callId, sender, target, seq, payload);
        } catch (Exception e) {
            return null;
        }
    }
}

