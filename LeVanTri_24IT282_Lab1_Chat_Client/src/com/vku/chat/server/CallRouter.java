package com.vku.chat.server;

import com.vku.chat.model.ChatMessage;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

final class CallRouter {
    private static final long RING_TIMEOUT_MS = 35_000;
    private final Function<String, ClientHandler> onlineClient;
    private final Map<String, Call> calls = new HashMap<>();

    CallRouter(Function<String, ClientHandler> onlineClient) {
        this.onlineClient = onlineClient;
    }

    synchronized void signal(ChatMessage message) {
        String sender = message.getSender();
        String recipient = message.getRecipient();
        UUID id = message.getCallId();
        if (recipient == null || id == null || sender.equals(recipient)) return;

        Call call = currentCall(sender);
        if (message.getType() == ChatMessage.Type.CALL_REQUEST) {
            if (onlineClient.apply(recipient) == null) {
                reject(message, "Tài khoản này đã ngoại tuyến.");
            } else if (call != null || currentCall(recipient) != null) {
                reject(message, "Một trong hai tài khoản đang có cuộc gọi.");
            } else {
                Call pending = new Call(id, sender, recipient, "VIDEO".equals(message.getContent()));
                calls.put(sender, pending);
                calls.put(recipient, pending);
                deliver(recipient, message);
            }
            return;
        }
        if (call == null || !call.id.equals(id) || !recipient.equals(call.peer(sender))) return;

        switch (message.getType()) {
            case CALL_ACCEPT:
                if (call.connected || !sender.equals(call.callee)) return;
                call.connected = true;
                break;
            case CALL_REJECT:
                if (call.connected || !sender.equals(call.callee)) return;
                remove(call);
                break;
            case CALL_END:
                remove(call);
                break;
            default:
                return;
        }
        deliver(recipient, message);
    }

    synchronized InetSocketAddress relayTarget(MediaPacket packet, InetSocketAddress source) {
        Call call = calls.get(packet.getSender());
        if (call == null || !call.connected || !call.id.equals(packet.getCallId())
                || !call.peer(packet.getSender()).equals(packet.getTarget())) return null;
        if (packet.getType() == MediaPacket.TYPE_REGISTER) {
            ClientHandler client = onlineClient.apply(packet.getSender());
            if (client != null && client.getAddress().equals(source.getAddress())) {
                call.endpoints.put(packet.getSender(), source);
            }
            return null;
        }
        if (!source.equals(call.endpoints.get(packet.getSender()))) return null;
        boolean audio = packet.getType() == MediaPacket.TYPE_AUDIO_CHUNK;
        boolean video = packet.getType() == MediaPacket.TYPE_VIDEO_FRAME || packet.getType() == MediaPacket.TYPE_VIDEO_PAUSED;
        return audio || (call.video && video) ? call.endpoints.get(packet.getTarget()) : null;
    }

    synchronized void disconnected(String username) {
        Call call = calls.get(username);
        if (call == null) return;
        remove(call);
        String peer = call.peer(username);
        ChatMessage ended = ChatMessage.callEnd(username, peer);
        ended.setCallId(call.id);
        ended.setContent("Đối phương đã mất kết nối.");
        deliver(peer, ended);
    }

    private Call currentCall(String username) {
        Call call = calls.get(username);
        if (call != null && !call.connected && System.currentTimeMillis() >= call.expiresAt) {
            remove(call);
            return null;
        }
        return call;
    }

    private void remove(Call call) {
        calls.remove(call.caller);
        calls.remove(call.callee);
    }

    private void reject(ChatMessage request, String reason) {
        ChatMessage reply = ChatMessage.callReject(request.getRecipient(), request.getSender(), reason);
        reply.setCallId(request.getCallId());
        deliver(request.getSender(), reply);
    }

    private void deliver(String username, ChatMessage message) {
        ClientHandler target = onlineClient.apply(username);
        if (target != null) target.sendMessage(message);
    }

    private static final class Call {
        final UUID id;
        final String caller;
        final String callee;
        final boolean video;
        final long expiresAt = System.currentTimeMillis() + RING_TIMEOUT_MS;
        final Map<String, InetSocketAddress> endpoints = new HashMap<>();
        boolean connected;

        Call(UUID id, String caller, String callee, boolean video) {
            this.id = id;
            this.caller = caller;
            this.callee = callee;
            this.video = video;
        }

        String peer(String username) {
            return caller.equals(username) ? callee : caller;
        }
    }
}
