UPDATE
    collections
SET
    collection = collection || '{"stac_version": "1.0.0-rc.2"}';

UPDATE
    collection_items
SET
    item = item || '{"stac_version": "1.0.0-rc.2"}';