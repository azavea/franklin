ALTER TABLE collections ADD COLUMN serial_id SERIAL NOT NULL;
ALTER TABLE collections ADD COLUMN created_at timestamp with time zone NOT NULL DEFAULT now();

CREATE INDEX collections_serial_id_idx ON collections (serial_id);
CREATE INDEX collections_created_at_idx ON collections (created_at);