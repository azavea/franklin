CREATE TABLE collections (
  id text primary key,
  collection jsonb not null
);

CREATE INDEX collections_collection_json_idx ON collections USING GIN (collection);