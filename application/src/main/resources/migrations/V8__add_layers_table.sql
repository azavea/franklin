CREATE TABLE layers (
    id text primary key,
    extent geometry('POLYGON', 4326) not null,
    geom geometry ('MULTIPOLYGON', 4326) not null,
    properties jsonb not null,
    links jsonb not null
);

CREATE INDEX IF NOT EXISTS layers_start_datetime_idx ON layers (start_datetime);

CREATE INDEX IF NOT EXISTS layers_end_datetime_idx ON layers (end_datetime);

CREATE INDEX IF NOT EXISTS layers_geometry_idx ON layers USING gist(geom);

CREATE INDEX IF NOT EXISTS layers_properties_idx ON layers USING gin(properties);