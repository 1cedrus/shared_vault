package org.one_cedrus;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.NotNull;
import org.one_cedrus.communication.VWebSocket;
import org.one_cedrus.shared.ChangeLog;
import org.one_cedrus.shared.FileChange;
import org.one_cedrus.util.VaultUtils;
import spark.Request;
import spark.Response;

import javax.servlet.MultipartConfigElement;
import javax.servlet.http.Part;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static spark.Spark.*;

public class Server {
    public static void main(String[] args) {
        // TODO: Make port configurable via environment variable or command line
        // argument
        port(4289);

        webSocket("/v", VWebSocket.class);

        path("/vault", () -> {
            // Create new vault with initial change logs and files
            post("", Server::registerNewVault);

            // Get all files in a vault as ZIP
            get("", Server::fetchAExistedVault);

            // Get all change logs for a vault
            get("/:name/change_logs", Server::changeLogs);

            // Get change logs since a specific timestamp version
            get("/:name/change_logs/since/:timestamp", Server::changeLogsSinceTimestampVersion);

            // Get change log at specific timestamp version
            get("/:name/change_logs/:timestamp", Server::changeLogsAtTimestampVersion);

            // Get a specific file by hash
            get("/:name/files/:hash", Server::fileContentWithHash);

            // Upload change logs with files
            post("/:name/sync", Server::syncChangeFromLocal);
        });
    }

    public static String generateVaultName() {
        SecureRandom random = new SecureRandom();
        byte[] buffer = new byte[32];
        random.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer).toLowerCase();
    }

    /**
     * Helper method to process change logs and files from multipart request
     * Used by both vault creation and sync APIs
     */
    private static void processChangeLogAndFiles(String vaultName, Collection<Part> parts) throws Exception {
        Gson gson = new Gson();
        List<ChangeLog> changeLogs = null;
        Map<String, Part> filesByHash = new HashMap<>();

        for (Part part : parts) {
            String fieldName = part.getName();

            if ("change_logs".equals(fieldName)) {
                // Read change logs JSON array
                try (InputStream inputStream = part.getInputStream()) {
                    String changeLogsJson = new String(inputStream.readAllBytes());

                    Type listType = new TypeToken<List<ChangeLog>>() {
                    }.getType();
                    changeLogs = gson.fromJson(changeLogsJson, listType);
                }
            } else if (fieldName != null && fieldName.startsWith("file_")) {
                // File parts should be named "file_<hash>"
                String hash = fieldName.substring(5); // Remove "file_" prefix
                filesByHash.put(hash, part);
            }
        }

        if (changeLogs == null || changeLogs.isEmpty()) {
            throw new IllegalArgumentException("Missing change_logs field or empty array");
        }

        // Collect all required hashes from all change logs
        Set<String> requiredHashes = extractContainHashes(changeLogs);

        // Save files with hash verification
        for (String hash : requiredHashes) {
            Part filePart = filesByHash.get(hash);
            if (filePart == null) {
                throw new IllegalArgumentException("Missing file for hash: " + hash);
            }

            try (InputStream inputStream = filePart.getInputStream()) {
                // Read file content once
                byte[] fileContent = inputStream.readAllBytes();

                // Verify hash matches
                String actualHash = VaultUtils.calculateByteArrayHash(fileContent);
                if (!hash.equals(actualHash)) {
                    throw new IllegalArgumentException(
                            "Hash mismatch for file. Expected: " + hash + ", Actual: " + actualHash);
                }

                VaultUtils.saveFileByHash(vaultName, hash, fileContent);
            }
        }

        for (ChangeLog changeLog : changeLogs) {
            VaultUtils.saveChangeLog(vaultName, changeLog);
        }

    }

    @NotNull
    private static Set<String> extractContainHashes(List<ChangeLog> changeLogs) {
        Set<String> requiredHashes = new HashSet<>();

        for (ChangeLog changeLog : changeLogs) {
            if (changeLog.getChanges().getAdded() != null) {
                for (FileChange fc : changeLog.getChanges().getAdded()) {
                    requiredHashes.add(fc.getHash());
                }
            }
            if (changeLog.getChanges().getModified() != null) {
                for (FileChange fc : changeLog.getChanges().getModified()) {
                    requiredHashes.add(fc.getHash());
                }
            }
        }
        return requiredHashes;
    }

    private static Object registerNewVault(Request req, Response res) throws Exception {
        String vaultName = generateVaultName();
        VaultUtils.ensureVaultStructure(vaultName);

        req.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/tmp"));

        processChangeLogAndFiles(vaultName, req.raw().getParts());
        return vaultName;
    }

    private static Object fetchAExistedVault(Request req, Response res) throws Exception {
        String vaultName = req.queryParams("name");
        if (vaultName == null) {
            halt(400, "Missing vault name parameter");
        }

        if (!VaultUtils.vaultExists(vaultName)) {
            halt(404, "Vault does not exist");
        }

        res.raw().setContentType("application/zip");
        res.raw().setHeader("Content-Disposition", "attachment; filename=\"vault.zip\"");

        try (ZipOutputStream zos = new ZipOutputStream(res.raw().getOutputStream())) {
            // Add all change logs
            File changeLogsDir = VaultUtils.getChangeLogsDir(vaultName);
            if (changeLogsDir.exists()) {
                zos.putNextEntry(new ZipEntry("change_logs/"));
                zos.closeEntry();

                for (File changeLogFile : Objects.requireNonNull(changeLogsDir.listFiles())) {
                    if (changeLogFile.isFile()) {
                        zos.putNextEntry(new ZipEntry("change_logs/" + changeLogFile.getName()));
                        Files.copy(changeLogFile.toPath(), zos);
                        zos.closeEntry();
                    }
                }
            }

            // Add all files
            File filesDir = VaultUtils.getFilesDir(vaultName);
            if (filesDir.exists()) {
                zos.putNextEntry(new ZipEntry("files/"));
                zos.closeEntry();

                for (File file : Objects.requireNonNull(filesDir.listFiles())) {
                    if (file.isFile()) {
                        zos.putNextEntry(new ZipEntry("files/" + file.getName()));
                        Files.copy(file.toPath(), zos);
                        zos.closeEntry();
                    }
                }
            }

            zos.finish();
        }

        return res;
    }

    private static Object changeLogs(Request req, Response res) {
        String vaultName = req.params(":name");

        if (!VaultUtils.vaultExists(vaultName)) {
            halt(404, "Vault does not exist");
        }

        res.type("application/json");

        List<File> changeLogFiles = VaultUtils.getChangeLogFiles(vaultName);
        List<ChangeLog> changeLogs = new ArrayList<>();

        for (File changeLogFile : changeLogFiles) {
            try {
                String content = Files.readString(changeLogFile.toPath());
                ChangeLog changeLog = ChangeLog.fromJson(content);
                changeLogs.add(changeLog);
            } catch (IOException e) {
                System.err.println(
                        "[ERROR]: Error reading change log: " + changeLogFile.getName() + " - " + e.getMessage());
            }
        }

        return new Gson().toJson(changeLogs);
    }

    private static Object changeLogsSinceTimestampVersion(Request req, Response res) {
        String vaultName = req.params(":name");
        String timestampStr = req.params(":timestamp");

        if (!VaultUtils.vaultExists(vaultName)) {
            halt(404, "Vault does not exist");
        }

        long sinceTimestamp;
        try {
            sinceTimestamp = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            halt(400, "Invalid timestamp format");
            return null;
        }

        res.type("application/json");

        List<File> changeLogFiles = VaultUtils.getChangeLogFiles(vaultName);
        List<ChangeLog> changeLogs = new ArrayList<>();

        for (File changeLogFile : changeLogFiles) {
            try {
                String content = Files.readString(changeLogFile.toPath());
                ChangeLog changeLog = ChangeLog.fromJson(content);

                // Only include change logs with timestamp greater than sinceTimestamp
                if (changeLog.getTimestamp() > sinceTimestamp) {
                    changeLogs.add(changeLog);
                }
            } catch (IOException e) {
                System.err.println(
                        "[ERROR]: Error reading change log: " + changeLogFile.getName() + " - " + e.getMessage());
            }
        }

        // Sort by timestamp to ensure chronological order
        changeLogs.sort(Comparator.comparingLong(ChangeLog::getTimestamp));

        return new Gson().toJson(changeLogs);
    }

    private static Object changeLogsAtTimestampVersion(Request req, Response res) throws Exception {
        String vaultName = req.params(":name");
        String timestampStr = req.params(":timestamp");

        if (!VaultUtils.vaultExists(vaultName)) {
            halt(404, "Vault does not exist");
        }

        res.type("application/json");

        File changeLogsDir = VaultUtils.getChangeLogsDir(vaultName);
        File file = Arrays
                .stream(Objects.requireNonNull(changeLogsDir
                        .listFiles((dir, name) -> name.endsWith(".json") && name.contains(timestampStr))))
                .findFirst().orElse(null);

        if (file == null) {
            halt(404, "Change log file not found");
        }

        return new Gson().toJson(Files.readString(file.toPath()));
    }

    private static Object fileContentWithHash(Request req, Response res) throws Exception {
        String vaultName = req.params(":name");
        String hash = req.params(":hash");

        if (!VaultUtils.vaultExists(vaultName)) {
            halt(404, "Vault does not exist");
        }

        File file = VaultUtils.getFileByHash(vaultName, hash);
        if (file == null) {
            halt(404, "File not found");
        }

        return Files.readAllBytes(file.toPath());
    }

    private static Object syncChangeFromLocal(Request req, Response res) {
        String vaultName = req.params(":name");

        if (!VaultUtils.vaultExists(vaultName)) {
            halt(404, "Vault does not exist");
        }

        req.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/tmp"));

        try {
            processChangeLogAndFiles(vaultName, req.raw().getParts());

            VWebSocket.notifyChange(vaultName);

            return "Sync completed successfully";
        } catch (IllegalArgumentException e) {
            res.status(400);
            return "Bad request: " + e.getMessage();
        } catch (Exception e) {
            res.status(500);
            return "Sync failed: " + e.getMessage();
        }
    }
}
