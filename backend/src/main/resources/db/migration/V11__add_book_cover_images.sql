ALTER TABLE image_assets
    ADD COLUMN purpose VARCHAR(30);

UPDATE image_assets
SET purpose = 'BOSS_PHASE';

ALTER TABLE image_assets
    ALTER COLUMN purpose SET NOT NULL;

ALTER TABLE bosses
    ADD COLUMN book_cover_image_asset_id BIGINT REFERENCES image_assets (id);

ALTER TABLE books
    ADD COLUMN cover_image_asset_id BIGINT REFERENCES image_assets (id);

CREATE INDEX idx_bosses_book_cover_image_asset_id ON bosses (book_cover_image_asset_id);
CREATE INDEX idx_books_cover_image_asset_id ON books (cover_image_asset_id);
