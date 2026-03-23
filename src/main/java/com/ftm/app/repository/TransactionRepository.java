package com.ftm.app.repository;

import com.ftm.app.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByTransactionRef(String transactionRef);

    List<Transaction> findByFromAccountIdOrToAccountId(Long fromAccountId, Long toAccountId);
}
