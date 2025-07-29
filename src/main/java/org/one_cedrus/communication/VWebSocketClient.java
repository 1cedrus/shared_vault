package org.one_cedrus.communication;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.one_cedrus.shared.MessageType;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class VWebSocketClient extends WebSocketClient {
    private final String vaultName;
    private final Consumer<String> onFileChangeMessage;
    private final Consumer<Integer> onCloseHandler;
    private final static byte MAX_RETRY = 5;
    private final static AtomicInteger retryCount = new AtomicInteger(0);

    public VWebSocketClient(URI webSocketURI, String vaultName, Consumer<String> onFileChangeMessage,
            Consumer<Integer> onCloseHandler) {
        super(webSocketURI);
        this.vaultName = vaultName;
        this.onFileChangeMessage = onFileChangeMessage;
        this.onCloseHandler = onCloseHandler;
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        System.out.println("[INFO]: WebSocket connected");

        // Send HELLO messages to register for vault notifications
        send(String.format("%s:%s", MessageType.HELLO.name(), vaultName));
    }

    @Override
    public void onMessage(String message) {
        System.out.println("[DEBUG]: WebSocket message: " + message);

        String[] parts = message.split(":", 2);
        MessageType type = MessageType.valueOf(parts[0]);

        switch (type) {
            case MessageType.FILE_CHANGE -> {
                if (parts.length >= 2) {
                    onFileChangeMessage.accept(parts[1]);
                }
            }
            case MessageType.STATUS -> {
                System.out.println("[INFO]: WebSocket registration confirmed");
            }
            case MessageType.HEARTBEAT -> {
                // Not implemented!
            }
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("[INFO]: WebSocket disconnected: " + reason + " (code: " + code + ")");

        if (retryCount.incrementAndGet() == MAX_RETRY) {
            onCloseHandler.accept(code);
        }
    }

    @Override
    public void onError(Exception ex) {
        System.err.println("[ERROR]: WebSocket error: " + ex.getMessage());
    }
}