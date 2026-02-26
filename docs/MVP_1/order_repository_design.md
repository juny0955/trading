# OrderRepository 호출 위치 설계 결정

## 배경

현재 `OrderRepository.save()`가 어디에서도 호출되지 않아, `GET /orders/{orderId}` 조회 시 항상 빈 결과를 반환한다.
이 문서는 **어디서 save()를 호출할지**, **동기 vs 이벤트 기반 처리**를 결정한 근거를 기록한다.

---

## 고민 지점

| 질문 | 선택지 |
|------|--------|
| 어느 계층에서 save()를 호출할까? | Service(OrderCommandService) vs Engine(EngineHandler) |
| 어떻게 호출할까? | 동기 호출 vs 이벤트 발행 |

---

## 결론

### 1. 호출 계층: Engine(EngineHandler) + Service(placeOrder 시 최초 저장)

#### 헥사고날 아키텍처 관점

```
application.engine   (MatchingEngine, EngineHandler)
application.port.out (OrderRepository)               ← 같은 application 계층
adapter.out          (MemoryOrderRepository)          ← Port 구현체
```

> EngineHandler가 `OrderRepository`(Port out)를 주입받아 호출하는 것은
> 헥사고날 아키텍처 위반이 **아님**. application 계층이 Port(out)를 통해
> 인프라를 사용하는 것은 의도된 패턴이다.

#### 기획서 원칙과의 정합성

> "모든 상태 변경은 MatchingEngine 내부에서만 수행"
> — docs/MVP_1/기획서.md

상태 변경 직후 저장까지 Engine 계층이 담당하는 것이 이 원칙과 일치한다.

---

### 2. 호출 방식: 동기 호출

- **이벤트 인프라 미구축**: `ApplicationEventPublisher` 등 이벤트 시스템 없음
- **MVP 범위**: EngineHandler 주석 "추후 이벤트 발행으로 대체" → 현재는 동기로 충분
- **인메모리 저장소**: I/O 비용 없으므로 동기 호출의 부담 없음

---

## 설계 세부 사항

### save() 호출 시점별 책임

| 시점 | 위치 | 이유 |
|------|------|------|
| 주문 최초 생성 (ACCEPTED) | `OrderCommandService.placeOrder()` | Order 객체를 생성하는 곳이 Service이므로, 생성 직후 저장 |
| 주문 취소 (CANCELLED) | `EngineHandler` (CancelOrder 처리) | 취소 상태 변경은 Engine에서만 발생 → 명시적 save 필요 |
| 체결 (FILLED / PARTIALLY_FILLED) | 참조 공유로 자동 반영 (MVP) | 아래 설명 참고 |

### 참조 공유 메커니즘 (in-memory MVP)

`MemoryOrderRepository`는 Order **객체 참조**를 저장한다.
같은 참조가 `OrderBook.index`에도 저장되므로, engine이 `fill()`, `cancel()` 등으로 상태를 변경하면 Repository에서 조회해도 최신 상태가 반영된다.

```
placeOrder() 흐름:
  Service: Order 생성 (ACCEPTED)
  Service: repository.save(order)        ← 참조 저장 (최초 1회)
  Service: engineLoop.submit(PlaceOrder(order))

  [engine-thread]
  order.activate() → NEW
  maker.fill(qty)  → PARTIALLY_FILLED/FILLED  ┐ 같은 객체 직접 수정
  taker.fill(qty)  → PARTIALLY_FILLED/FILLED  ┘ → Repository 자동 반영

cancelOrder() 흐름:
  Service: engineLoop.submit(CancelOrder(orderId))

  [engine-thread]
  Order cancelled = orderBook.remove(orderId)  ← OrderBook에서 꺼냄 (= Repository의 같은 참조)
  cancelled.cancel()  → CANCELLED
  orderRepository.save(cancelled)              ← 명시적 저장 (취소만)
```

> **참조 공유 전략의 한계**: 이 방식은 MemoryRepository 구현 세부사항에 의존한다.
> 추후 JPA/DB로 교체 시, 체결(fill) 상태도 명시적으로 save해야 한다.
> → `PlaceResult(taker, trades, filledMakers)` 패턴으로 리팩토링 필요 (향후 계획)

---

## 필요한 코드 변경

### 1. `Order.java` — volatile 필드 추가

engine-thread 쓰기 → HTTP-thread 읽기 간 가시성 보장:

```java
// before
private Quantity remaining;
private OrderStatus status;

// after
private volatile Quantity remaining;
private volatile OrderStatus status;
```

### 2. `MatchingEngine.java` — cancelOrder 반환 타입 변경

EngineHandler에서 취소된 Order를 받아 저장할 수 있도록:

```java
// before
public void cancelOrder(OrderId orderId) {
    Order order = orderBook.remove(orderId)
        .orElseThrow(() -> new IllegalStateException("Already Processed or Cancelled Order"));
    order.cancel();
}

// after
public Order cancelOrder(OrderId orderId) {
    Order order = orderBook.remove(orderId)
        .orElseThrow(() -> new IllegalStateException("Already Processed or Cancelled Order"));
    order.cancel();
    return order;
}
```

### 3. `OrderCommandService.java` — 최초 save 추가

```java
@Service
@RequiredArgsConstructor
public class OrderCommandService implements PlaceOrderUseCase, CancelOrderUseCase {

    private final EngineLoop engineLoop;
    private final OrderRepository orderRepository;  // 추가

    @Override
    public String placeOrder(String side, long price, long quantity) {
        Order order = new Order(Side.valueOf(side), new Price(price), new Quantity(quantity));
        orderRepository.save(order);                // 추가 (ACCEPTED 상태로 저장)
        engineLoop.submit(new EngineCommand.PlaceOrder(order));
        return order.getOrderId().toString();
    }

    @Override
    public void cancelOrder(String orderId) {
        engineLoop.submit(new EngineCommand.CancelOrder(OrderId.from(orderId)));
        // 변경 없음. 취소 저장은 EngineHandler가 담당.
    }
}
```

### 4. `EngineHandler.java` — CancelOrder 처리 시 명시적 save

```java
@Component
@RequiredArgsConstructor
public class EngineHandler {

    private final MatchingEngine engine;
    private final OrderBook orderBook;
    private final OrderBookCache orderBookCache;
    private final OrderRepository orderRepository;  // 추가

    public void handle(EngineCommand command) {
        switch (command) {
            case EngineCommand.PlaceOrder c -> {
                List<Trade> trades = engine.placeLimitOrder(c.order());
                if (!trades.isEmpty()) log.info("Trades executed: {}", trades);
                orderBookCache.update(orderBook);
                // taker 상태는 참조 공유로 자동 반영 (MVP)
            }
            case EngineCommand.CancelOrder c -> {
                Order cancelled = engine.cancelOrder(c.orderId());  // Order 반환받음
                orderRepository.save(cancelled);                    // 명시적 저장
                orderBookCache.update(orderBook);
            }
            case EngineCommand.Shutdown _ ->
                log.warn("Shutdown command reached EngineHandler; this should not happen.");
        }
    }
}
```

---

## 검증 시나리오

| 시나리오 | 검증 항목 |
|---------|---------|
| `POST /orders` 후 `GET /orders/{id}` | ACCEPTED 또는 NEW 상태 반환 |
| 체결 후 `GET /orders/{takerId}` | FILLED 또는 PARTIALLY_FILLED |
| 체결 후 `GET /orders/{makerId}` | FILLED (참조 공유로 반영) |
| 취소 후 `GET /orders/{id}` | CANCELLED |
| 이미 체결된 주문 취소 시도 | 예외 발생 (IllegalStateException) |

---

## 향후 계획

- **MVP 이후**: `placeLimitOrder()`가 `PlaceResult(taker, trades, filledMakers)` 반환 → EngineHandler에서 maker fill 상태도 명시적 save
- **DB 교체 시**: 참조 공유 전략 폐기, 모든 상태변경 후 명시적 save로 전환
- **이벤트 기반**: EngineHandler에서 TradeEvent 발행 → 통계, WebSocket 실시간 업데이트