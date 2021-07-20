CREATE TABLE mosaic_definitions (
    id uuid primary key,
    collection text references collections(id) not null,
    mosaic jsonb not null,
    histograms jsonb,
    created_at timestamp with time zone not null,
    serial_id serial not null
);

CREATE INDEX IF NOT EXISTS mosaic_definition_collection_idx ON mosaic_definitions (collection);

CREATE INDEX mosaic_definitions_serial_id_idx ON mosaic_definitions (serial_id);

CREATE INDEX mosaic_definitions_created_at_idx ON mosaic_definitions (created_at);