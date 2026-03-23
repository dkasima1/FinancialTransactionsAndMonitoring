package com.ftm.app.service;

import com.ftm.app.dto.TransferRequest;
import com.ftm.app.model.Account;
import com.ftm.app.model.Transaction;
import com.ftm.app.model.TransactionStatus;
import com.ftm.app.repository.AccountRepository;
import com.ftm.app.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    public TransactionService(TransactionRepository transactionRepository,
                              AccountRepository accountRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Transfers money between two accounts within a single ACID transaction.
     *
     * Key learning point: This uses SELECT ... FOR UPDATE (pessimistic locking)
     * to prevent race conditions. Both accounts are locked in a consistent order
     * (by account number) to avoid deadlocks.
     */
    @Transactional
    public Transaction transfer(TransferRequest request) {
        if (request.fromAccountNumber().equals(request.toAccountNumber())) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }

        // Lock accounts in consistent order to prevent deadlocks
        String first = request.fromAccountNumber().compareTo(request.toAccountNumber()) < 0
            ? request.fromAccountNumber() : request.toAccountNumber();
        String second = first.equals(request.fromAccountNumber())
            ? request.toAccountNumber() : request.fromAccountNumber();

        Account firstAccount = accountRepository.findByAccountNumberForUpdate(first)
            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + first));
        Account secondAccount = accountRepository.findByAccountNumberForUpdate(second)
            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + second));

        Account fromAccount = firstAccount.getAccountNumber().equals(request.fromAccountNumber())
            ? firstAccount : secondAccount;
        Account toAccount = firstAccount.getAccountNumber().equals(request.toAccountNumber())
            ? firstAccount : secondAccount;

        // Check sufficient funds
        if (fromAccount.getBalance().compareTo(request.amount()) < 0) {
            Transaction failed = new Transaction(
                "TXN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(),
                fromAccount, toAccount, request.amount(),
                fromAccount.getCurrency(), TransactionStatus.FAILED,
                "Insufficient funds"
            );
            return transactionRepository.save(failed);
        }

        // Debit and credit
        fromAccount.setBalance(fromAccount.getBalance().subtract(request.amount()));
        toAccount.setBalance(toAccount.getBalance().add(request.amount()));

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        Transaction transaction = new Transaction(
            "TXN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(),
            fromAccount, toAccount, request.amount(),
            fromAccount.getCurrency(), TransactionStatus.COMPLETED,
            request.description()
        );

        return transactionRepository.save(transaction);
    }

    @Transactional(readOnly = true)
    public Transaction getTransaction(String transactionRef) {
        return transactionRepository.findByTransactionRef(transactionRef)
            .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionRef));
    }

    @Transactional(readOnly = true)
    public List<Transaction> getTransactionsForAccount(Long accountId) {
        return transactionRepository.findByFromAccountIdOrToAccountId(accountId, accountId);
    }
}
