package org.one_cedrus.communication;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.one_cedrus.shared.MessageType;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@WebSocket
public class VWebSocket {
    private static final List<Session> sessions = new CopyOnWriteArrayList<>();
    private static final Map<String, List<Session>> sessionsByVault = new ConcurrentHashMap<>();
    private static final Map<Session, Set<String>> sessionToVaults = new ConcurrentHashMap<>();
    private static final Map<String, Set<Session>> vaultToSessions = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);

    // Periodically send HEARTBEAT message to keep sessions alive.
    static {
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            sessions.forEach(session -> {
                try {
                    session.getRemote().sendString(MessageType.HEARTBEAT.name());
                } catch (IOException e) {
                    sessions.remove(session);

                    throw new RuntimeException(e);
                }
            });
        }, 0, 30, TimeUnit.SECONDS);
    }

    @OnWebSocketConnect
    public void onWebSocketConnect(Session session) {
        System.out.println("[INFO]: WebSocket connected: " + session.getRemoteAddress());

        sessions.add(session);
    }

    @OnWebSocketClose
    public void onWebSocketClose(Session session, int statusCode, String reason) {
        System.out.println("[INFO]: WebSocket disconnected: " + session.getRemoteAddress() + " (code: " + statusCode
                + ", reason: " + reason + ")");
        cleanupSession(session);
    }

    @OnWebSocketMessage
    public void onWebSocketText(Session session, String message) {
        System.out.println("[DEBUG]: Received message: " + message);

        String[] parts = message.split(":", 2);
        if (parts.length < 2)
            return;

        MessageType messageType = MessageType.valueOf(parts[0]);
        String payload = parts[1];

        switch (messageType) {
            case HELLO:
                registerVaultMonitoring(session, payload);
                try {
                    session.getRemote().sendString(MessageType.STATUS + ":registered");
                } catch (IOException e) {
                    System.err.println("[ERROR]: Failed to send registration confirmation: " + e.getMessage());
                }
                break;
            case STATUS:
                // Handle client status update if needed
                break;
        }
    }

    private void registerVaultMonitoring(Session session, String vaultName) {
        sessionToVaults.computeIfAbsent(session, k -> new HashSet<>()).add(vaultName);
        vaultToSessions.computeIfAbsent(vaultName, k -> new HashSet<>()).add(session);

        System.out.println("[DEBUG]: Client monitoring vaults: " + sessionToVaults.get(session));
    }

    @OnWebSocketError
    public void onWebSocketError(Session session, Throwable error) {
        System.err.println("[ERROR]: WebSocket error for " + session.getRemoteAddress() + ": " + error.getMessage());
    }

    public static void notifyChange(String vaultName) {
        Set<Session> vaultSessions = vaultToSessions.get(vaultName);

        System.out.println("[DEBUG]: Notifying change for vault: " + vaultName);

        if (vaultSessions == null || vaultSessions.isEmpty()) {
            System.out.println("[DEBUG]: No sessions monitoring vault: " + vaultName);
            return;
        }

        System.out.println("[DEBUG]: Found " + vaultSessions.size() + " sessions monitoring vault: " + vaultName);

        vaultSessions.forEach(session -> {
            try {
                session.getRemote().sendString(MessageType.FILE_CHANGE + ":" + vaultName);
                System.out.println("[DEBUG]: Sent notification to: " + session.getRemoteAddress());
            } catch (IOException e) {
                System.out.println("[DEBUG]: Session closed, removing from vault: " + session.getRemoteAddress());
                cleanupSession(session);
            }
        });
    }

    private static void cleanupSession(Session session) {
        sessions.remove(session);

        Set<String> vaults = sessionToVaults.remove(session);
        if (vaults != null) {
            vaults.forEach(vault -> {
                Set<Session> vaultSessions = vaultToSessions.get(vault);
                if (vaultSessions != null) {
                    vaultSessions.remove(session);
                }
            });
        }
    }
}
