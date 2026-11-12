package com.sulaksono.egineeringdataservice.util;


import com.sulaksono.egineeringdataservice.model.FileType;

/**
 * Thin wrapper around {@link FileType#fromFileName(String)} for readability.
 */
public final class FileTypeResolver {

    private FileTypeResolver() { }

    public static FileType resolve(String fileName) {
        return FileType.fromFileName(fileName);
    }
}