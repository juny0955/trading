package dev.junyoung.trading.order.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.OrderStatus;
import dev.junyoung.trading.order.domain.model.enums.OrderType;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.QuoteQty;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JdbcOrderRepository implements OrderRepository {

	private final JdbcTemplate jdbcTemplate;
	private final RowMapper<Order> orderRowMapper = this::mapOrder;

	@Override
	public void save(Order order) {
		Instant now = Instant.now();
		Timestamp orderedAt = Timestamp.from(order.getOrderedAt());
		Timestamp updatedAt = Timestamp.from(now);

		jdbcTemplate.update(
			OrderQuery.UPSERT_SQL.getSql(),
			order.getOrderId().value(),
			order.getAccountId().value(),
			order.getClientOrderId(),
			order.getSymbol().value(),
			order.getSide().name(),
			order.getOrderType().name(),
			order.getTif().name(),
			order.getStatus().name(),
			order.getPriceValue().orElse(null),
			order.getQuantityValue().orElse(null),
			order.getRemaining().value(),
			order.getQuoteQty() != null ? order.getQuoteQty().value() : null,
			order.getCumBaseQty(),
			order.getCumQuoteQty(),
			null,
			orderedAt,
			orderedAt,
			updatedAt
		);
	}

	@Override
	public Optional<Order> findById(OrderId id) {
		return jdbcTemplate.query(OrderQuery.FIND_BY_ID_SQL.getSql(), orderRowMapper, id.value())
			.stream()
			.findFirst();
	}

	private Order mapOrder(ResultSet rs, int rowNum) throws SQLException {
		return Order.restore(
			new OrderId(rs.getObject("order_id", UUID.class)),
			new AccountId(rs.getObject("account_id", UUID.class)),
			rs.getString("client_order_id"),
			new Symbol(rs.getString("symbol")),
			Side.valueOf(rs.getString("side")),
			OrderType.valueOf(rs.getString("order_type")),
			TimeInForce.valueOf(rs.getString("tif")),
			nullablePrice(rs),
			nullableQuoteQty(rs),
			nullableQuantity(rs),
			new Quantity(rs.getLong("remaining_qty")),
			OrderStatus.valueOf(rs.getString("status")),
			rs.getTimestamp("ordered_at").toInstant(),
			rs.getLong("cum_quote_qty"),
			rs.getLong("cum_base_qty")
		);
	}

	private Price nullablePrice(ResultSet rs) throws SQLException {
		Long value = nullableLong(rs, "price");
		return value == null ? null : new Price(value);
	}

	private QuoteQty nullableQuoteQty(ResultSet rs) throws SQLException {
		Long value = nullableLong(rs, "quote_qty");
		return value == null ? null : new QuoteQty(value);
	}

	private Quantity nullableQuantity(ResultSet rs) throws SQLException {
		Long value = nullableLong(rs, "quantity");
		return value == null ? null : new Quantity(value);
	}

	private Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}
}
