-- this index was referring to the top-level item object, which doesn't
-- help with searches into the item's `properties` field
DROP INDEX IF EXISTS collection_item_json_idx;

ALTER TABLE
    collection_items
ADD
    COLUMN start_datetime timestamp with time zone;

ALTER TABLE
    collection_items
ADD
    COLUMN end_datetime timestamp with time zone;

ALTER TABLE
    collection_items
ADD
    COLUMN datetime timestamp with time zone;

CREATE TEMP TABLE collection_items_tmp AS (
    SELECT
        id,
        geom,
        item,
        serial_id,
        created_at,
        collection,
        (
            (item -> 'properties' ->> 'start_datetime') :: timestamp with time zone
        ) start_datetime,
        (
            (item -> 'properties' ->> 'end_datetime') :: timestamp with time zone
        ) end_datetime,
        (
            (item -> 'properties' ->> 'datetime') :: timestamp with time zone
        ) datetime
    FROM
        collection_items
);

TRUNCATE TABLE collection_items;

-- this index replaces the former collection_item_json_idx but indexing into properties instead of
-- indexing the top-level of the item
CREATE INDEX IF NOT EXISTS collection_items_properties_idx ON collection_items USING gin ((item -> 'properties'));

CREATE INDEX IF NOT EXISTS collection_items_start_datetime_idx ON collection_items (start_datetime);

CREATE INDEX IF NOT EXISTS collection_items_end_datetime_idx ON collection_items (end_datetime);

CREATE INDEX IF NOT EXISTS collection_items_datetime_idx ON collection_items (datetime);

INSERT INTO
    collection_items (
        select
            *
        from
            collection_items_tmp
    );