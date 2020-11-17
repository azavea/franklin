ALTER TABLE collection_items ADD COLUMN collection text;

CREATE TEMP TABLE collection_items_tmp AS (
    SELECT id, geom, item, serial_id, created_at, item ->> 'collection' FROM collection_items
);

TRUNCATE TABLE collection_items;

CREATE INDEX IF NOT EXISTS collection_items_collection_idx ON collection_items (collection);

INSERT INTO collection_items (select * from collection_items_tmp);