package dev.junyoung.trading.order.adapter.out.persistence.memory;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import dev.junyoung.trading.order.application.port.out.AcceptedSeqGenerator;
import dev.junyoung.trading.order.application.port.out.OrderRepository;

@Component
public class MemoryAcceptedSeqGenerator implements AcceptedSeqGenerator {

	private final AtomicLong acceptedSeq;

	public MemoryAcceptedSeqGenerator(OrderRepository orderRepository) {
		long initValue = orderRepository.findMaxAcceptedSeq().orElse(0L);
		this.acceptedSeq = new AtomicLong(initValue);
	}

	@Override
	public long next() {
		return acceptedSeq.incrementAndGet();
	}
}
