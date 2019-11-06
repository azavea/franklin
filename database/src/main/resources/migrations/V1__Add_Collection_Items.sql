CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE collection_items (
  id text primary key,
  extent geometry('POLYGON', 4326) not null,
  geom geometry('GEOMETRY', 4326) not null,
  item jsonb not null
);

CREATE INDEX collection_item_geom_idx ON collection_items USING GIST ( geom );
CREATE INDEX collection_item_extent_idx ON collection_items USING GIST ( geom );
CREATE INDEX collection_item_json_idx ON collection_items USING GIN (item);