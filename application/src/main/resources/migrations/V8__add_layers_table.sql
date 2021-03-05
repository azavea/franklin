CREATE TABLE layers (
    id text primary key,
    geom geometry ('GEOMETRY', 4326) not null,
    layer jsonb not null,
    created_at timestamp with time zone not null default now(),
    serial_id serial not null
);

CREATE INDEX IF NOT EXISTS layers_start_datetime_idx ON layers USING gin ((layer -> 'properties' -> 'start_datetime'));

CREATE INDEX IF NOT EXISTS layers_end_datetime_idx ON layers USING gin ((layer -> 'properties' -> 'end_datetime'));

CREATE INDEX IF NOT EXISTS layers_geometry_idx ON layers USING gist(geom);

CREATE INDEX layers_serial_id_idx ON layers (serial_id);

CREATE INDEX layers_created_at_idx ON layers (created_at);