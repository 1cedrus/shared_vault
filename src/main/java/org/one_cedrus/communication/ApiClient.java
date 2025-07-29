package org.one_cedrus.communication;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import okhttp3.*;
import org.one_cedrus.shared.ChangeLog;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class ApiClient {
    private final String serverUrl;
    private final OkHttpClient client;
    private final Gson gson;

    public ApiClient(String serverUrl) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.client = new OkHttpClient();
        this.gson = new Gson();
    }

    public String createVault(ChangeLog initialCL, Map<String, Path> files) throws IOException {
        String url = serverUrl + "/vault";

        MultipartBody.Builder builder = new MultipartBody.Builder()
            .setType(MultipartBody.FORM);

        // Add change logs as JSON
        builder.addFormDataPart("change_logs", gson.toJson(List.of(initialCL)));

        // Add files
        for (Map.Entry<String, Path> entry : files.entrySet()) {
            String hash = entry.getKey();
            Path filePath = entry.getValue();

            RequestBody fileBody = RequestBody.create(
                Files.readAllBytes(filePath),
                MediaType.parse("application/octet-stream"));
            builder.addFormDataPart("file_" + hash, hash, fileBody);
        }

        Request request = new Request.Builder()
            .url(url)
            .post(builder.build())
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to create vault: " + response.code() + " - " + response.body().string());
            }
            return response.body().string().trim();
        }
    }

    public void syncVault(String vaultName, List<ChangeLog> changeLogs, Map<String, Path> files) throws IOException {
        String url = serverUrl + "/vault/" + vaultName + "/sync";

        MultipartBody.Builder builder = new MultipartBody.Builder()
            .setType(MultipartBody.FORM);

        // Add change logs as JSON
        builder.addFormDataPart("change_logs", gson.toJson(changeLogs));

        // Add files
        for (Map.Entry<String, Path> entry : files.entrySet()) {
            String hash = entry.getKey();
            Path filePath = entry.getValue();

            RequestBody fileBody = RequestBody.create(
                Files.readAllBytes(filePath),
                MediaType.parse("application/octet-stream"));
            builder.addFormDataPart("file_" + hash, hash, fileBody);
        }

        Request request = new Request.Builder()
            .url(url)
            .post(builder.build())
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to sync vault: " + response.code() + " - " + response.body().string());
            }
        }
    }

    public List<ChangeLog> getChangeLogsSince(String vaultName, long sinceTimestamp) throws IOException {
        String url = serverUrl + "/vault/" + vaultName + "/change_logs/since/" + sinceTimestamp;

        Request request = new Request.Builder()
            .url(url)
            .get()
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException(
                    "Failed to get change logs since " + sinceTimestamp + ": " + response.code() + " - "
                        + response.body().string());
            }

            String json = response.body().string();
            Type listType = new TypeToken<List<ChangeLog>>() {
            }.getType();
            return gson.fromJson(json, listType);
        }
    }

    public List<ChangeLog> getChangeLogs(String vaultName) throws IOException {
        String url = serverUrl + "/vault/" + vaultName + "/change_logs";

        Request request = new Request.Builder()
            .url(url)
            .get()
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException(
                    "Failed to get change logs: " + response.code() + " - " + response.body().string());
            }

            String json = response.body().string();
            Type listType = new TypeToken<List<ChangeLog>>() {
            }.getType();
            return gson.fromJson(json, listType);
        }
    }

    public byte[] getFile(String vaultName, String fileHash) throws IOException {
        String url = serverUrl + "/vault/" + vaultName + "/files/" + fileHash;

        Request request = new Request.Builder()
            .url(url)
            .get()
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get file: " + response.code() + " - " + response.body().string());
            }

            return response.body().bytes();
        }
    }
}