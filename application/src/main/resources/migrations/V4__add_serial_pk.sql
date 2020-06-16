-- I confirmed that NOT NULL on a SERIAL column fills in values appropriately
ALTER TABLE collection_items ADD COLUMN serial_id SERIAL NOT NULL;
ALTER TABLE collection_items ADD COLUMN created_at timestamp with time zone NOT NULL DEFAULT now();

CREATE INDEX collection_items_serial_id_idx ON collection_items (serial_id);
CREATE INDEX collection_items_created_at_idx ON collection_items (created_at);