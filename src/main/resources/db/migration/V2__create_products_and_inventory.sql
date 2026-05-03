CREATE TYPE product_status AS ENUM ('ACTIVE', 'INACTIVE', 'DELETED');

CREATE TABLE products (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_id       UUID            NOT NULL REFERENCES users(id),
    name            VARCHAR(500)    NOT NULL,
    description     TEXT,
    price           NUMERIC(12, 2)  NOT NULL CHECK (price > 0),
    category        VARCHAR(100)    NOT NULL,
    status          product_status  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- Most common query: browse active products by category, sorted by newest
CREATE INDEX idx_products_category_status
    ON products(category, status, created_at DESC)
    WHERE status = 'ACTIVE';

-- Seller dashboard: "show me my products"
CREATE INDEX idx_products_seller
    ON products(seller_id, created_at DESC);

-- Keyset pagination anchor — every paginated query uses this
CREATE INDEX idx_products_id_created
    ON products(id, created_at DESC);


-- Inventory is a separate table, not columns on products.
-- Reason: products and inventory have very different access patterns.
-- Products are read-heavy with long cache TTLs.
-- Inventory is write-heavy, cache-sensitive, and lock-contested.
-- Mixing them would force cache invalidation on every stock update.

CREATE TABLE inventory (
    product_id      UUID        PRIMARY KEY REFERENCES products(id),
    quantity        INT         NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    reserved        INT         NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    version         BIGINT      NOT NULL DEFAULT 0,  -- optimistic locking
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- available = quantity - reserved, enforced at DB level
    CONSTRAINT chk_reserved_lte_quantity CHECK (reserved <= quantity)
);