package com.securetransact.service;

import com.securetransact.model.*;
import com.securetransact.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionProcessor {

    private final AccountRepository accountRepository;
    private final LedgerPostingService ledgerPostingService;

    @Transactional
    public TransactionStatus processMoneyMovement(Transaction transaction) {
        Account fromAccount = transaction.getFromAccount();
        Account toAccount = transaction.getToAccount();

        if (!isActive(fromAccount) || !isActive(toAccount)) {
            return TransactionStatus.FAILED;
        }

        if (fromAccount != null && fromAccount.getBalance().compareTo(transaction.getAmount()) < 0) {
            return TransactionStatus.FAILED;
        }

        switch (transaction.getType()) {
            case DEPOSIT -> toAccount.setBalance(toAccount.getBalance().add(transaction.getAmount()));
            case WITHDRAWAL -> fromAccount.setBalance(fromAccount.getBalance().subtract(transaction.getAmount()));
            case TRANSFER -> {
                fromAccount.setBalance(fromAccount.getBalance().subtract(transaction.getAmount()));
                toAccount.setBalance(toAccount.getBalance().add(transaction.getAmount()));
            }
        }

        if (fromAccount != null) accountRepository.save(fromAccount);
        if (toAccount != null) accountRepository.save(toAccount);
        ledgerPostingService.postSettlement(transaction);
        return TransactionStatus.SETTLED;
    }

    @Transactional
    public TransactionStatus reverseMoneyMovement(Transaction transaction) {
        if (transaction.getStatus() != TransactionStatus.SETTLED) {
            return TransactionStatus.FAILED;
        }

        Account fromAccount = transaction.getFromAccount();
        Account toAccount = transaction.getToAccount();
        if (!isActive(fromAccount) || !isActive(toAccount)) return TransactionStatus.FAILED;

        switch (transaction.getType()) {
            case DEPOSIT -> toAccount.setBalance(toAccount.getBalance().subtract(transaction.getAmount()));
            case WITHDRAWAL -> fromAccount.setBalance(fromAccount.getBalance().add(transaction.getAmount()));
            case TRANSFER -> {
                if (toAccount.getBalance().compareTo(transaction.getAmount()) < 0) return TransactionStatus.FAILED;
                fromAccount.setBalance(fromAccount.getBalance().add(transaction.getAmount()));
                toAccount.setBalance(toAccount.getBalance().subtract(transaction.getAmount()));
            }
        }
        if (fromAccount != null) accountRepository.save(fromAccount);
        if (toAccount != null) accountRepository.save(toAccount);
        ledgerPostingService.postReversal(transaction);
        return TransactionStatus.REVERSED;
    }

    private boolean isActive(Account account) {
        return account == null || account.getStatus() == AccountStatus.ACTIVE;
    }
}
