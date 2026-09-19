package com.securetransact.service;

import com.securetransact.dto.AccountLookupResponse;
import com.securetransact.dto.AccountRequest;
import com.securetransact.dto.AccountResponse;
import com.securetransact.dto.TransactionResponse;
import com.securetransact.exception.ForbiddenException;
import com.securetransact.exception.ResourceNotFoundException;
import com.securetransact.model.Account;
import com.securetransact.model.AccountStatus;
import com.securetransact.model.AuditAction;
import com.securetransact.model.Transaction;
import com.securetransact.model.User;
import com.securetransact.repository.AccountRepository;
import com.securetransact.repository.TransactionRepository;
import com.securetransact.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final AuditService auditService;

    @Transactional
    public AccountResponse createAccount(Long userId, AccountRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String accountNumber = generateAccountNumber();

        Account account = Account.builder()
                .user(user)
                .accountNumber(accountNumber)
                .accountType(request.getAccountType())
                .balance(request.getInitialDeposit() != null ? request.getInitialDeposit() : BigDecimal.ZERO)
                .status(AccountStatus.ACTIVE)
                .build();

        account = accountRepository.save(account);
        return AccountResponse.from(account);
    }

    public List<AccountResponse> getAccountsByUser(Long userId) {
        return accountRepository.findByUserId(userId).stream()
                .map(AccountResponse::from)
                .collect(Collectors.toList());
    }

    public AccountResponse getAccountDetails(Long accountId, Long userId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (!account.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have access to this account");
        }

        return AccountResponse.from(account);
    }

    public List<TransactionResponse> getStatement(Long accountId, Long userId,
                                                   LocalDateTime start, LocalDateTime end) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (!account.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have access to this account");
        }

        List<Transaction> transactions = transactionRepository.findByAccountIdAndDateRange(accountId, start, end);
        return transactions.stream()
                .map(TransactionResponse::from)
                .collect(Collectors.toList());
    }

    public AccountLookupResponse lookupByAccountNumber(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        return AccountLookupResponse.from(account);
    }

    @Transactional
    public AccountResponse freezeAccount(Long accountId, Long adminUserId, String reason) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new com.securetransact.exception.ConflictException("A closed account cannot be frozen");
        }
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found"));
        account.setStatus(AccountStatus.FROZEN);
        Account saved = accountRepository.save(account);
        auditService.recordEvent(AuditAction.ACCOUNT_FROZEN, "ACCOUNT", accountId,
                reason == null || reason.isBlank() ? "Frozen from fraud operations workspace" : reason.trim(),
                admin, null, null);
        return AccountResponse.from(saved);
    }

    @Transactional
    public AccountResponse unfreezeAccount(Long accountId, Long adminUserId, String reason) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (account.getStatus() != AccountStatus.FROZEN) {
            throw new com.securetransact.exception.ConflictException("Only frozen accounts can be unfrozen");
        }
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found"));
        account.setStatus(AccountStatus.ACTIVE);
        Account saved = accountRepository.save(account);
        auditService.recordEvent(AuditAction.ACCOUNT_UNFROZEN, "ACCOUNT", accountId,
                reason == null || reason.isBlank() ? "Unfrozen from fraud operations workspace" : reason.trim(),
                admin, null, null);
        return AccountResponse.from(saved);
    }

    private String generateAccountNumber() {
        String accountNumber;
        do {
            accountNumber = "ST" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 10).toUpperCase();
        } while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }
}
