package dev.junyoung.trading.account.application.exception.balance;

import dev.junyoung.trading.common.exception.NotFoundException;

public class BalanceNotFoundException extends NotFoundException {
    public BalanceNotFoundException(String accountId, String asset) {
        super(BalanceErrorCode.BALANCE_NOT_FOUND, "Balance not found: " + accountId + ", " + asset);
    }
}
