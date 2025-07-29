package org.one_cedrus.util;

import org.one_cedrus.exception.HashCalculationException;
import org.one_cedrus.exception.VaultException;
import org.one_cedrus.shared.ChangeLog;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VaultUtils {

    public static String calculateFileHash(File file) throws IOException, HashCalculationException {
        return HashCalculator.calculateFileHash(file);
    }

    public static String calculateInputStreamHash(InputStream inputStream)
            throws IOException, HashCalculationException {
        return HashCalculator.calculateInputStreamHash(inputStream);
    }

    public static String calculateByteArrayHash(byte[] fileContent) throws HashCalculationException {
        return HashCalculator.calculateByteArrayHash(fileContent);
    }

    public static void ensureVaultStructure(String vaultName) throws VaultException {
        File vaultDir = new File("vaults/" + vaultName);
        File changeLogsDir = new File(vaultDir, "change_logs");
        File filesDir = new File(vaultDir, "files");

        // This is rarely happen!
        if (vaultDir.exists()) {
            throw new VaultException(
                    "There was a vaultDir with the same name existed! Let's use a different vaultName");
        }

        boolean success = vaultDir.mkdirs() && changeLogsDir.mkdirs() && filesDir.mkdirs();
        if (!success) {
            throw new VaultException("Failed to create vault directory structure for: " + vaultName);
        }
    }

    public static File getVaultDir(String vaultName) {
        return new File("vaults/" + vaultName);
    }

    public static File getChangeLogsDir(String vaultName) {
        return new File("vaults/" + vaultName + "/change_logs");
    }

    public static File getFilesDir(String vaultName) {
        return new File("vaults/" + vaultName + "/files");
    }

    public static boolean vaultExists(String vaultName) {
        return getVaultDir(vaultName).exists();
    }

    public static List<File> getChangeLogFiles(String vaultName) {
        File changeLogsDir = getChangeLogsDir(vaultName);
        if (!changeLogsDir.exists()) {
            return new ArrayList<>();
        }

        File[] files = changeLogsDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) {
            return new ArrayList<>();
        }

        List<File> changeLogFiles = Arrays.asList(files);
        changeLogFiles.sort((f1, f2) -> f1.getName().compareTo(f2.getName()));
        return changeLogFiles;
    }

    public static String generateChangeLogFileName(long timestamp) {
        return String.format("%015d.json", timestamp);
    }

    public static void saveChangeLog(String vaultName, ChangeLog changeLog) throws IOException {
        File changeLogsDir = getChangeLogsDir(vaultName);
        String fileName = generateChangeLogFileName(changeLog.getTimestamp());
        File changeLogFile = new File(changeLogsDir, fileName);

        try (FileWriter writer = new FileWriter(changeLogFile)) {
            writer.write(changeLog.toJson());
        }
    }

    public static void saveFileByHash(String vaultName, String hash, InputStream inputStream) throws IOException {
        File filesDir = getFilesDir(vaultName);
        File targetFile = new File(filesDir, hash);

        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }
    }

    public static void saveFileByHash(String vaultName, String hash, byte[] fileContent) throws IOException {
        File filesDir = getFilesDir(vaultName);
        File targetFile = new File(filesDir, hash);

        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            fos.write(fileContent);
        }
    }

    public static File getFileByHash(String vaultName, String hash) {
        File filesDir = getFilesDir(vaultName);
        File file = new File(filesDir, hash);
        return file.exists() ? file : null;
    }
}
