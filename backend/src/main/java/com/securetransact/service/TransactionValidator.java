package com.securetransact.service;

import com.securetransact.dto.TransactionRequest;
import com.securetransact.exception.ConflictException;
import com.securetransact.exception.ForbiddenException;
import com.securetransact.exception.InsufficientBalanceException;
import com.securetransact.exception.ResourceNotFoundException;
import com.securetransact.model.*;
import com.securetransact.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionValidator {

    private final AccountRepository accountRepository;

    public Account validateAndGetFromAccount(Long accountId, Long userId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Source account not found"));

        if (!account.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have access to this account");
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ConflictException("Source account is not active");
        }
        return account;
    }

    public Account validateAndGetToAccount(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Destination account not found"));
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ConflictException("Destination account is not active");
        }
        return account;
    }

    public void validateSufficientBalance(Account account, BigDecimal amount) {
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    String.format("Insufficient balance. Available: %s, Required: %s",
                            account.getBalance(), amount));
        }
    }

    public void validateDifferentAccounts(Account from, Account to) {
        if (from.getId().equals(to.getId())) {
            throw new ConflictException("Source and destination accounts must be different");
        }
    }
}
