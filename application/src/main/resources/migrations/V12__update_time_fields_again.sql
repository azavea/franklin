UPDATE
    collection_items
SET
    datetime = (item -> 'properties' ->> 'datetime') :: timestamp with time zone
WHERE
    item -> 'properties' ? 'datetime'
    AND datetime is null;

UPDATE
    collection_items
SET
    start_datetime = (item -> 'properties' ->> 'start_datetime') :: timestamp with time zone,
    end_datetime = (item -> 'properties' ->> 'end_datetime') :: timestamp with time zone
WHERE
    item -> 'properties' ? 'start_datetime'
    AND start_datetime is null;