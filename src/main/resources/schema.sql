/* bootstrap DDL – pgvector + metadata columns */
create schema if not exists engineering_reference;

create extension if not exists pgcrypto;
create extension if not exists vector;

create table engineering_reference.canonical_files
(
    id             uuid                     default gen_random_uuid() not null
        primary key,
    file_name      varchar(255)                                       not null,
    path           varchar(1024)                                      not null,
    module         varchar(255)                                       not null,
    module_version varchar(255),
    file_type      varchar(255)                                       not null,
    repo_clone_url varchar(1024),
    repo_ref       varchar(255)             default 'master'::character varying,
    path_in_repo   varchar(1024),
    content        text,
    deprecated     boolean                  default false             not null,
    created_at     timestamp with time zone default CURRENT_TIMESTAMP not null,
    updated_at     timestamp with time zone default CURRENT_TIMESTAMP not null
);

create table engineering_reference.file_embeddings
(
    id                uuid                     default gen_random_uuid() not null
        primary key,
    file_name         varchar(255)                                       not null,
    path              varchar(255)                                       not null,
    module            varchar(255)                                       not null,
    file_type         varchar(255)                                       not null,
    embedding         vector(1536)                                       not null,
    content           text                                               not null,
    deprecated        boolean                  default false,
    created_at        timestamp with time zone default CURRENT_TIMESTAMP,
    chunk_idx         integer,
    chunk_of          integer,
    module_version    varchar(255),
    canonical_file_id uuid
        constraint fk_file_embeddings_canonical_file
            references engineering_reference.canonical_files
);

create index if not exists file_embeddings_embedding_idx
    on engineering_reference.file_embeddings
        using ivfflat (embedding vector_cosine_ops)
    with (lists = 100);

create index if not exists file_embeddings_content_fts_idx
    on engineering_reference.file_embeddings
        using gin (to_tsvector('simple', coalesce(content, '')));

create index ix_file_embeddings_canonical_file_id
    on engineering_reference.file_embeddings (canonical_file_id);

create unique index uq_canonical_files_mod_ver_path
    on engineering_reference.canonical_files
        (module, COALESCE(module_version, ''::character varying), path);

create index ix_canonical_files_path
    on engineering_reference.canonical_files (path);

create index ix_canonical_files_module
    on engineering_reference.canonical_files (module);

create index ix_canonical_files_repo
    on engineering_reference.canonical_files (repo_clone_url);

create or replace function engineering_reference.set_updated_at() returns trigger
    language plpgsql
as
$$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

drop trigger if exists trg_canonical_files_updated_at
    on engineering_reference.canonical_files;

create trigger trg_canonical_files_updated_at
    before update on engineering_reference.canonical_files
    for each row execute function engineering_reference.set_updated_at();
