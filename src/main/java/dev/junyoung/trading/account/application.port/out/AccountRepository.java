package dev.junyoung.trading.account.application.port.out;

import dev.junyoung.trading.account.domain.model.entity.Account;

public interface AccountRepository {
    void save(Account account);
}
