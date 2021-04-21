UPDATE
    collections
SET
    collection = collection || '{"assets": {}}'
WHERE
    not collection ? 'assets';

UPDATE
    collections
SET
    collection = collection || '{"type": "Collection"}';