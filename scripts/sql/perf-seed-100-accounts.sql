WITH generated_accounts AS (
    SELECT format('00000000-0000-0000-0000-%s', lpad(gs::text, 12, '0'))::uuid AS account_id
    FROM generate_series(1, 100) AS gs
)
INSERT INTO accounts (account_id, created_at)
SELECT account_id, NOW()
FROM generated_accounts;

WITH generated_accounts AS (
    SELECT format('00000000-0000-0000-0000-%s', lpad(gs::text, 12, '0'))::uuid AS account_id
    FROM generate_series(1, 100) AS gs
),
seed_assets AS (
    SELECT 'KRW'::varchar(32) AS asset, 1000000000000::bigint AS available, 0::bigint AS held
    UNION ALL
    SELECT 'BTC'::varchar(32), 1000000::bigint, 0::bigint
    UNION ALL
    SELECT 'ETH'::varchar(32), 10000000::bigint, 0::bigint
    UNION ALL
    SELECT 'TEST'::varchar(32), 1000000000::bigint, 0::bigint
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
CROSS JOIN seed_assets AS assets;
