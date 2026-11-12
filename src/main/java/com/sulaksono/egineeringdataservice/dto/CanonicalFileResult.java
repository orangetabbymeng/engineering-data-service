package com.sulaksono.egineeringdataservice.dto;

import com.sulaksono.egineeringdataservice.model.CanonicalFile;
import com.sulaksono.egineeringdataservice.model.FileType;

import java.util.UUID;

public record CanonicalFileResult(
        UUID id,
        String fileName,
        String path,
        String module,
        String moduleVersion,
        FileType fileType,
        String repoCloneUrl,
        String repoRef,
        String pathInRepo,
        String content,
        boolean deprecated
) {
    public static CanonicalFileResult from(CanonicalFile file) {
        return new CanonicalFileResult(
                file.getId(), file.getFileName(), file.getPath(), file.getModule(),
                file.getModuleVersion(), file.getFileType(), file.getRepoCloneUrl(),
                file.getRepoRef(), file.getPathInRepo(), file.getContent(), file.isDeprecated());
    }
}
