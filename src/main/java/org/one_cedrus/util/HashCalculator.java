package org.one_cedrus.util;

import org.one_cedrus.exception.HashCalculationException;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for calculating SHA-256 hashes from various sources
 */
public class HashCalculator {
    private static final String ALGORITHM = "SHA-256";
    private static final int BUFFER_SIZE = 8192;

    /**
     * Calculate SHA-256 hash from a file
     */
    public static String calculateFileHash(File file) throws IOException, HashCalculationException {
        try (FileInputStream fis = new FileInputStream(file)) {
            return calculateInputStreamHash(fis);
        }
    }

    /**
     * Calculate SHA-256 hash from an InputStream
     */
    public static String calculateInputStreamHash(InputStream inputStream)
            throws IOException, HashCalculationException {
        try {
            MessageDigest md = MessageDigest.getInstance(ALGORITHM);
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
            return bytesToHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new HashCalculationException(ALGORITHM + " algorithm not available", e);
        }
    }

    /**
     * Calculate SHA-256 hash from a byte array
     */
    public static String calculateByteArrayHash(byte[] data) throws HashCalculationException {
        try {
            MessageDigest md = MessageDigest.getInstance(ALGORITHM);
            md.update(data);
            return bytesToHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new HashCalculationException(ALGORITHM + " algorithm not available", e);
        }
    }

    /**
     * Convert byte array to hexadecimal string
     */
    private static String bytesToHex(byte[] hashBytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}