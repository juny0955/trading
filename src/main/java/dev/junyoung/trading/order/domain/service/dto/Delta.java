package dev.junyoung.trading.order.domain.service.dto;

import lombok.Getter;

@Getter
public class Delta {
    private long availableDelta;
    private long heldDelta;

    public void addAvailable(long execute) {
        this.availableDelta = Math.addExact(availableDelta, execute);
    }

    public void subHeld(long execute) {
        this.heldDelta = Math.subtractExact(heldDelta, execute);
    }

    public boolean isZero() {
        return availableDelta == 0L && heldDelta == 0L;
    }
}
