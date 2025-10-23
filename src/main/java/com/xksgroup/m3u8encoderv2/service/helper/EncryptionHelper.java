package com.xksgroup.m3u8encoderv2.service.helper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;

@Slf4j
@Component
public class EncryptionHelper {

    private static final int AES_KEY_SIZE = 16; // 128 bits
    private static final int AES_IV_SIZE = 16;  // 128 bits

    /**
     * Generate a random AES-128 encryption key
     */
    public byte[] generateEncryptionKey() {
        SecureRandom random = new SecureRandom();
        byte[] key = new byte[AES_KEY_SIZE];
        random.nextBytes(key);
        log.debug("Generated new AES-128 encryption key");
        return key;
    }

    /**
     * Generate a random initialization vector (IV)
     */
    public byte[] generateIV() {
        SecureRandom random = new SecureRandom();
        byte[] iv = new byte[AES_IV_SIZE];
        random.nextBytes(iv);
        log.debug("Generated new AES-128 IV");
        return iv;
    }

    /**
     * Write encryption key to file
     */
    public void writeKeyToFile(byte[] key, Path keyFile) throws IOException {
        Files.write(keyFile, key);
        log.info("Wrote encryption key to file: {}", keyFile);
    }

    /**
     * Write IV to file as hex string
     */
    public void writeIVToFile(byte[] iv, Path ivFile) throws IOException {
        String hexIV = HexFormat.of().formatHex(iv);
        Files.write(ivFile, hexIV.getBytes());
        log.info("Wrote IV to file: {}", ivFile);
    }

    /**
     * Read encryption key from file
     */
    public byte[] readKeyFromFile(Path keyFile) throws IOException {
        byte[] key = Files.readAllBytes(keyFile);
        log.debug("Read encryption key from file: {}", keyFile);
        return key;
    }

    /**
     * Read IV from file
     */
    public byte[] readIVFromFile(Path ivFile) throws IOException {
        String hexIV = Files.readString(ivFile).trim();
        byte[] iv = HexFormat.of().parseHex(hexIV);
        log.debug("Read IV from file: {}", ivFile);
        return iv;
    }

    /**
     * Convert byte array to hex string
     */
    public String bytesToHex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Convert hex string to byte array
     */
    public byte[] hexToBytes(String hex) {
        return HexFormat.of().parseHex(hex);
    }

    /**
     * Generate encryption key file name for a job
     */
    public String getKeyFileName(String jobId) {
        return "key_" + jobId + ".key";
    }

    /**
     * Generate IV file name for a job
     */
    public String getIVFileName(String jobId) {
        return "iv_" + jobId + ".txt";
    }

    /**
     * Generate key info file name for FFmpeg
     */
    public String getKeyInfoFileName(String jobId) {
        return "keyinfo_" + jobId + ".txt";
    }

    /**
     * Create key info file for FFmpeg HLS encryption
     * Format:
     * Line 1: Key URI (URL where the key can be retrieved)
     * Line 2: Key file path (local path to the key file)
     * Line 3: IV (optional, hex string)
     */
    public void createKeyInfoFile(Path keyInfoFile, String keyUri, Path keyFile, byte[] iv) throws IOException {
        StringBuilder content = new StringBuilder();
        content.append(keyUri).append("\n");
        content.append(keyFile.toAbsolutePath().toString()).append("\n");
        if (iv != null) {
            content.append(bytesToHex(iv)).append("\n");
        }
        
        Files.write(keyInfoFile, content.toString().getBytes());
        log.info("Created key info file: {} with key URI: {}", keyInfoFile, keyUri);
    }

    /**
     * Generate secure key URI for the proxy endpoint
     */
    public String generateKeyUri(String protocol, String serverHost, String jobId) {
        String baseUrl = String.format("%s://%s", protocol, serverHost);
        return String.format("%s/proxy/key/%s", baseUrl, jobId);
    }


    /**
     * Validate encryption key format
     */
    public boolean isValidKey(byte[] key) {
        return key != null && key.length == AES_KEY_SIZE;
    }

    /**
     * Validate IV format
     */
    public boolean isValidIV(byte[] iv) {
        return iv != null && iv.length == AES_IV_SIZE;
    }
}

