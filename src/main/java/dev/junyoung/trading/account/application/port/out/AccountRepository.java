package dev.junyoung.trading.account.application.port.out;

import java.util.Optional;

import dev.junyoung.trading.account.domain.model.entity.Account;
import dev.junyoung.trading.account.domain.model.value.AccountId;

public interface AccountRepository {
    void save(Account account);
    Optional<Account> findById(AccountId accountId);
}
