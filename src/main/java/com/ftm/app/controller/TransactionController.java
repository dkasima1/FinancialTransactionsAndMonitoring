package com.ftm.app.controller;

import com.ftm.app.dto.TransferRequest;
import com.ftm.app.model.Transaction;
import com.ftm.app.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.CREATED)
    public Transaction transfer(@Valid @RequestBody TransferRequest request) {
        return transactionService.transfer(request);
    }

    @GetMapping("/{transactionRef}")
    public Transaction getTransaction(@PathVariable String transactionRef) {
        return transactionService.getTransaction(transactionRef);
    }
}
