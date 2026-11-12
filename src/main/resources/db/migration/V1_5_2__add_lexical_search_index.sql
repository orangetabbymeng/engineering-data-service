create index if not exists file_embeddings_content_fts_idx
    on engineering_reference.file_embeddings
    using gin (to_tsvector('simple', coalesce(content, '')));
