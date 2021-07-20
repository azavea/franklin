CREATE TABLE item_asset_histograms (
    item_id text not null references collection_items (id),
    asset_name text not null,
    histograms jsonb not null
);

CREATE INDEX item_asset_histograms_idx ON item_asset_histograms (item_id, asset_name);