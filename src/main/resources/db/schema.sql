CREATE TABLE accounts (
    account_id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE balances (
    account_id UUID NOT NULL,
    asset VARCHAR(32) NOT NULL,
    available BIGINT NOT NULL,
    held BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (account_id, asset),
    CONSTRAINT fk_balances_account
        FOREIGN KEY (account_id) REFERENCES accounts (account_id)
);

CREATE TABLE orders (
    order_id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    client_order_id VARCHAR(64) NOT NULL,
    accepted_seq BIGINT NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    side VARCHAR(8) NOT NULL,
    order_type VARCHAR(16) NOT NULL,
    tif VARCHAR(8) NOT NULL,
    status VARCHAR(32) NOT NULL,
    price BIGINT,
    quantity BIGINT,
    remaining_qty BIGINT NOT NULL,
    quote_qty BIGINT,
    cum_base_qty BIGINT NOT NULL DEFAULT 0,
    cum_quote_qty BIGINT NOT NULL DEFAULT 0,
    ordered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_orders_account
        FOREIGN KEY (account_id) REFERENCES accounts (account_id),
    CONSTRAINT fk_orders_symbol
        FOREIGN KEY (symbol) REFERENCES symbols (symbol),
    CONSTRAINT uq_orders_account_client_order_id
        UNIQUE (account_id, client_order_id)
);

CREATE TABLE trades (
    trade_id UUID PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL,
    buy_order_id UUID NOT NULL,
    sell_order_id UUID NOT NULL,
    price BIGINT NOT NULL,
    quantity BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_trades_buy_order
        FOREIGN KEY (buy_order_id) REFERENCES orders (order_id),
    CONSTRAINT fk_trades_sell_order
        FOREIGN KEY (sell_order_id) REFERENCES orders (order_id),
    CONSTRAINT fk_trades_symbol
        FOREIGN KEY (symbol) REFERENCES symbols (symbol)
);

CREATE TABLE idempotency_keys (
    account_id UUID NOT NULL,
    client_order_id VARCHAR(64) NOT NULL,
    order_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (account_id, client_order_id),
    CONSTRAINT fk_idempotency_account
        FOREIGN KEY (account_id) REFERENCES accounts (account_id)
);

CREATE TABLE symbols (
    symbol VARCHAR(32) PRIMARY KEY,
    base_asset VARCHAR(32) NOT NULL,
    quote_asset VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE symbol_states (
    symbol VARCHAR(32) PRIMARY KEY,
    last_event_sequence BIGINT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_symbol_states_symbol
        FOREIGN KEY (symbol) REFERENCES symbols (symbol)
);

CREATE TABLE assets (
    asset_code VARCHAR(20) PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE tick_size_rules (
    rule_id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL,
    min_price BIGINT NOT NULL,
    max_price BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_tick_size_rules_symbol
        FOREIGN KEY (symbol) REFERENCES symbols (symbol)
);

CREATE TABLE outbox_events (
    event_id UUID PRIMARY KEY,
    aggregate_id VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(32) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    event_version INT NOT NULL,
    sequence BIGINT NOT NULL,
    payload JSONB NOT NULL,
    statue VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL,
    last_error TEXT,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    published_at TIMESTAMP WITH TIME ZONE,
    trace_id VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_outbox_events_symbol
        FOREIGN KEY (symbol) REFERENCES symbols (symbol)
);
