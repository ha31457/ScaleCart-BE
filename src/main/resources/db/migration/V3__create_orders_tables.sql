CREATE TYPE order_status AS ENUM (
    'PENDING',
    'CONFIRMED',
    'PROCESSING',
    'SHIPPED',
    'DELIVERED',
    'CANCELLED',
    'FAILED'
);

CREATE TABLE orders (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id         UUID            NOT NULL REFERENCES users(id),
    idempotency_key     VARCHAR(255)    NOT NULL,
    status              order_status    NOT NULL DEFAULT 'PENDING',
    total_amount        NUMERIC(12, 2)  NOT NULL CHECK (total_amount > 0),
    shipping_address    TEXT            NOT NULL,
    failure_reason      TEXT,           -- populated when status = FAILED
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- Idempotency check — this is the FIRST thing order service does on every request
CREATE UNIQUE INDEX idx_orders_idempotency_key
    ON orders(idempotency_key);

-- Customer order history (paginated, newest first)
CREATE INDEX idx_orders_customer_created
    ON orders(customer_id, created_at DESC);

-- Background job polling: "give me all PENDING orders older than 5 minutes"
-- Partial index: only indexes rows where work is still needed
CREATE INDEX idx_orders_pending
    ON orders(created_at)
    WHERE status = 'PENDING';


CREATE TABLE order_items (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID            NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id  UUID            NOT NULL REFERENCES products(id),
    quantity    INT             NOT NULL CHECK (quantity > 0),
    unit_price  NUMERIC(12, 2)  NOT NULL CHECK (unit_price > 0),
    subtotal    NUMERIC(12, 2)  GENERATED ALWAYS AS (quantity * unit_price) STORED
);

-- Most common query: "give me all items for order X"
CREATE INDEX idx_order_items_order
    ON order_items(order_id);