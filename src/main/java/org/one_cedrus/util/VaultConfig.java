package org.one_cedrus.util;

import com.google.gson.Gson;
import org.one_cedrus.shared.ChangeLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class VaultConfig {
    private String vaultName;
    private String serverUrl;
    private String websocketUrl;
    private int debounceSeconds;
    private ChangeLog currentChangeLog;

    public VaultConfig(String vaultName, String serverUrl, int debounceSeconds) {
        this.vaultName = vaultName;
        this.serverUrl = serverUrl;
        this.debounceSeconds = debounceSeconds;

        // Auto-generate WebSocket URL
        if (serverUrl.startsWith("https://")) {
            this.websocketUrl = serverUrl.replace("https://", "wss://") + "/v";
        } else {
            this.websocketUrl = serverUrl.replace("http://", "ws://") + "/v";
        }
    }

    // Getters and setters
    public String getVaultName() {
        return vaultName;
    }

    public void setVaultName(String vaultName) {
        this.vaultName = vaultName;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getWebsocketUrl() {
        return websocketUrl;
    }

    public ChangeLog getCurrentChangeLog() {
        return currentChangeLog;
    }

    public void setCurrentChangeLog(ChangeLog currentChangeLog) {
        this.currentChangeLog = currentChangeLog;
    }

    public void setWebsocketUrl(String websocketUrl) {
        this.websocketUrl = websocketUrl;
    }

    public int getDebounceSeconds() {
        return debounceSeconds;
    }

    public void setDebounceSeconds(int debounceSeconds) {
        this.debounceSeconds = debounceSeconds;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    public static VaultConfig fromJson(String json) {
        return new Gson().fromJson(json, VaultConfig.class);
    }

    public static VaultConfig loadFromFile(Path configPath) throws IOException {
        if (!Files.exists(configPath)) {
            throw new IOException("Config file not found: " + configPath);
        }

        String content = Files.readString(configPath);
        return fromJson(content);
    }

    public void saveToFile(Path configPath) throws IOException {
        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, toJson());
    }
}