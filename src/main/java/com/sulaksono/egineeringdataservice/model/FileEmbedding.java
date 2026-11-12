package com.sulaksono.egineeringdataservice.model;

import com.sulaksono.egineeringdataservice.model.converter.FloatArrayVectorConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "file_embeddings", schema = "engineering_reference")
public class FileEmbedding {

    @Id
    @GeneratedValue
    private UUID id;

    private String fileName;

    private String path;
    private String module;

    @Enumerated(EnumType.STRING)
    private FileType fileType;

    @Setter
    @Column(columnDefinition = "vector")
    @Convert(converter = FloatArrayVectorConverter.class)
    private float[] embedding;

    @CreationTimestamp
    private Instant createdAt;

    @Column(columnDefinition = "text")
    private String content;

    private boolean deprecated = false;

    private int chunkIdx;
    private int chunkOf;

    private String moduleVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "canonical_file_id", nullable = false)
    private CanonicalFile canonicalFile;

    public FileEmbedding() {}

    public FileEmbedding(Details details, Chunk chunk, float[] embedding,
                         String content, CanonicalFile canonicalFile) {
        this.fileName = details.fileName();
        this.path = details.path();
        this.module = details.module();
        this.chunkIdx = chunk.index();
        this.chunkOf = chunk.total();
        this.fileType = details.fileType();
        this.embedding = embedding;
        this.content = content;
        this.moduleVersion = details.moduleVersion();
        this.canonicalFile = canonicalFile;
    }

    public record Details(String fileName, String path, String module,
                          FileType fileType, String moduleVersion) { }

    public record Chunk(int index, int total) { }
}