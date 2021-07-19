CREATE TABLE mosaic_definitions (
    id uuid primary key,
    collection text references collections(id) not null,
    mosaic jsonb not null,
    histograms jsonb
);

CREATE INDEX IF NOT EXISTS mosaic_definition_collection_idx ON mosaic_definitions (collection);