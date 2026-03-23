package com.ftm.app.service;

import com.ftm.app.dto.CreateAccountRequest;
import com.ftm.app.model.Account;
import com.ftm.app.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public Account createAccount(CreateAccountRequest request) {
        String accountNumber = "ACC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Account account = new Account(
            accountNumber,
            request.ownerName(),
            request.initialBalance(),
            request.currency()
        );
        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public Account getAccount(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountNumber));
    }

    @Transactional(readOnly = true)
    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }
}
