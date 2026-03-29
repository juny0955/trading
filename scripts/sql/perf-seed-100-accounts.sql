INSERT INTO assets (asset_code, status, created_at, updated_at)
VALUES
    ('KRW', 'ACTIVE', NOW(), NOW()),
    ('BTC', 'ACTIVE', NOW(), NOW()),
    ('ETH', 'ACTIVE', NOW(), NOW()),
    ('TEST', 'ACTIVE', NOW(), NOW())
ON CONFLICT (asset_code) DO NOTHING;

INSERT INTO symbols (symbol, base_asset, quote_asset, step_size, status, created_at, updated_at)
VALUES
    ('BTC', 'BTC', 'KRW', 1, 'ACTIVE', NOW(), NOW()),
    ('ETH', 'ETH', 'KRW', 1, 'ACTIVE', NOW(), NOW()),
    ('TEST', 'TEST', 'KRW', 1, 'ACTIVE', NOW(), NOW())
ON CONFLICT (symbol) DO NOTHING;

INSERT INTO tick_size_rules (symbol, min_price, max_price, tick_size, created_at, updated_at)
SELECT symbol, min_price, max_price, tick_size, NOW(), NOW()
FROM (
    VALUES
        ('BTC', 0::bigint, NULL::bigint, 1::bigint),
        ('ETH', 0::bigint, NULL::bigint, 1::bigint),
        ('TEST', 0::bigint, NULL::bigint, 1::bigint)
) AS seed_rules(symbol, min_price, max_price, tick_size)
WHERE NOT EXISTS (
    SELECT 1
    FROM tick_size_rules existing
    WHERE existing.symbol = seed_rules.symbol
      AND existing.min_price = seed_rules.min_price
      AND existing.max_price IS NOT DISTINCT FROM seed_rules.max_price
      AND existing.tick_size = seed_rules.tick_size
);

WITH generated_accounts AS (
    SELECT format('00000000-0000-0000-0000-%s', lpad(gs::text, 12, '0'))::uuid AS account_id
    FROM generate_series(1, 100) AS gs
)
INSERT INTO accounts (account_id, created_at)
SELECT account_id, NOW()
FROM generated_accounts
ON CONFLICT (account_id) DO NOTHING;

WITH generated_accounts AS (
    SELECT format('00000000-0000-0000-0000-%s', lpad(gs::text, 12, '0'))::uuid AS account_id
    FROM generate_series(1, 100) AS gs
),
seed_assets AS (
    SELECT 'KRW'::varchar(32) AS asset, 100000000000000000::bigint AS available, 0::bigint AS held
    UNION ALL
    SELECT 'BTC'::varchar(32), 100000000000::bigint, 0::bigint
    UNION ALL
    SELECT 'ETH'::varchar(32), 100000000000::bigint, 0::bigint
    UNION ALL
    SELECT 'TEST'::varchar(32), 100000000000::bigint, 0::bigint
)
INSERT INTO balances (account_id, asset, available, held, created_at, updated_at)
SELECT
    accounts.account_id,
    assets.asset,
    assets.available,
    assets.held,
    NOW(),
    NOW()
FROM generated_accounts AS accounts
CROSS JOIN seed_assets AS assets
ON CONFLICT (account_id, asset) DO NOTHING;
