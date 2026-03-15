package dev.junyoung.trading.account.application.exception.account;

import dev.junyoung.trading.common.exception.NotFoundException;

public class AccountNotFoundException extends NotFoundException {
    public AccountNotFoundException(String accountId) {
        super(AccountErrorCode.ACCOUNT_NOT_FOUND, "Account not found: " + accountId);
    }
}
