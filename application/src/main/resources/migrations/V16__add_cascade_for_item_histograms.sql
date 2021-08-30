ALTER TABLE
    item_asset_histograms DROP CONSTRAINT item_asset_histograms_item_id_fkey;

ALTER TABLE
    item_asset_histograms
ADD
    CONSTRAINT item_asset_histograms_item_id_fkey FOREIGN KEY (item_id) REFERENCES collection_items (id) ON DELETE CASCADE;