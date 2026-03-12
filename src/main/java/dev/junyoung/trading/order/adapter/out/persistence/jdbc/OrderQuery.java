package dev.junyoung.trading.order.adapter.out.persistence.jdbc;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OrderQuery {
	UPSERT_SQL("""
			INSERT INTO orders (
			   order_id,
			   account_id,
			   client_order_id,
			   symbol,
			   side,
			   order_type,
			   tif,
			   status,
			   price,
			   quantity,
			   remaining_qty,
			   quote_qty,
			   cum_base_qty,
			   cum_quote_qty,
			   accepted_seq,
			   ordered_at
			) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			ON CONFLICT (order_id) DO UPDATE SET
			   status = EXCLUDED.status,
			   price = EXCLUDED.price,
			   quantity = EXCLUDED.quantity,
			   remaining_qty = EXCLUDED.remaining_qty,
			   quote_qty = EXCLUDED.quote_qty,
			   cum_base_qty = EXCLUDED.cum_base_qty,
			   cum_quote_qty = EXCLUDED.cum_quote_qty,
			   accepted_seq = EXCLUDED.accepted_seq,
			   ordered_at = EXCLUDED.ordered_at,
			   updated_at = NOW()
		""")
	,

	FIND_BY_ID_SQL("""
		SELECT
		    order_id,
		    account_id,
		    client_order_id,
		    symbol,
		    side,
		    order_type,
		    tif,
		    status,
		    price,
		    quantity,
		    remaining_qty,
		    quote_qty,
		    cum_base_qty,
		    cum_quote_qty,
		    ordered_at
		FROM orders
		WHERE order_id = ?
		""")

	;

	private final String sql;
}
