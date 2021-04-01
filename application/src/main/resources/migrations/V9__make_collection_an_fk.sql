ALTER TABLE
    collection_items
ADD
    CONSTRAINT collection_collections_fk FOREIGN KEY (collection) REFERENCES collections(id) ON DELETE CASCADE;