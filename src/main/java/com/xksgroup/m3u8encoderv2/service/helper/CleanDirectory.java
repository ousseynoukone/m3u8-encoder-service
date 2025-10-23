package com.xksgroup.m3u8encoderv2.service.helper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


@Service
@Slf4j
public class CleanDirectory {

    public  void cleanFileAndDirectory(){
        Path hlsRootDir = Paths.get("hls-v2");
        if (Files.exists(hlsRootDir)) {
            deleteDirectoryRecursively(hlsRootDir);
            log.info("hls-v2 directory cleaned");
        }

        Path uploadRootDir = Paths.get("upload-v2");
        if (Files.exists(uploadRootDir)) {
            deleteDirectoryRecursively(uploadRootDir);
            log.info("upload-v2 directory cleaned");
        }

    }


    private void deleteDirectoryRecursively(Path directory) {
        try {
            if (Files.exists(directory)) {
                Files.walk(directory)
                        .sorted((a, b) -> b.compareTo(a)) // Delete files first, then directories
                        .forEach(this::deleteFile);
            }
        } catch (IOException e) {
            log.warn("Error deleting directory {}: {}", directory, e.getMessage());
        }
    }

    private void deleteFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Error deleting {}: {}", path, e.getMessage());
        }
    }
}
