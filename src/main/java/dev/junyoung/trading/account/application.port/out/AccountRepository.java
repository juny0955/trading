package dev.junyoung.trading.account.application.port.out;

import dev.junyoung.trading.account.domain.model.entity.Account;
import dev.junyoung.trading.account.domain.model.value.AccountId;

import java.util.Optional;

public interface AccountRepository {
    void save(Account account);
    Optional<Account> findById(AccountId accountId);
}
