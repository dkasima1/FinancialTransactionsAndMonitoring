package com.ftm.app.controller;

import com.ftm.app.dto.CreateAccountRequest;
import com.ftm.app.model.Account;
import com.ftm.app.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Account createAccount(@Valid @RequestBody CreateAccountRequest request) {
        return accountService.createAccount(request);
    }

    @GetMapping("/{accountNumber}")
    public Account getAccount(@PathVariable String accountNumber) {
        return accountService.getAccount(accountNumber);
    }

    @GetMapping
    public List<Account> getAllAccounts() {
        return accountService.getAllAccounts();
    }
}
