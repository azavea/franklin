ALTER TABLE collections ADD COLUMN parent text REFERENCES collections(id);
