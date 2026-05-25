-- V7__add_product_search_index.sql
CREATE INDEX idx_products_search ON products(status, category, price, created_at, id);