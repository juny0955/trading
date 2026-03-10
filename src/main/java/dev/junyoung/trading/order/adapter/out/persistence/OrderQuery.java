package dev.junyoung.trading.order.adapter.out.persistence;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OrderQuery {
	UPSERT_SQL("""
		MERGE INTO orders (
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
		    ordered_at,
		    created_at,
		    updated_at
		) KEY (order_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
