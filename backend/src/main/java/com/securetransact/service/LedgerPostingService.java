package com.securetransact.service;

import com.securetransact.exception.ConflictException;
import com.securetransact.model.*;
import com.securetransact.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** Writes immutable postings. It never mutates or deletes historic entries. */
@Service
@RequiredArgsConstructor
public class LedgerPostingService {

    private final LedgerEntryRepository ledgerEntryRepository;

    public void postSettlement(Transaction transaction) {
        if (ledgerEntryRepository.existsByTransactionIdAndEntryType(transaction.getId(), LedgerEntryType.SETTLEMENT)) {
            throw new ConflictException("Settlement has already been posted for this payment");
        }
        ledgerEntryRepository.saveAll(buildEntries(transaction, LedgerEntryType.SETTLEMENT));
    }

    public void postReversal(Transaction transaction) {
        if (!ledgerEntryRepository.existsByTransactionIdAndEntryType(transaction.getId(), LedgerEntryType.SETTLEMENT)) {
            throw new ConflictException("Only a settled payment can be reversed");
        }
        if (ledgerEntryRepository.existsByTransactionIdAndEntryType(transaction.getId(), LedgerEntryType.REVERSAL)) {
            throw new ConflictException("Reversal has already been posted for this payment");
        }
        ledgerEntryRepository.saveAll(buildEntries(transaction, LedgerEntryType.REVERSAL));
    }

    private List<LedgerEntry> buildEntries(Transaction transaction, LedgerEntryType type) {
        LedgerDirection sourceDirection = type == LedgerEntryType.SETTLEMENT ? LedgerDirection.DEBIT : LedgerDirection.CREDIT;
        LedgerDirection destinationDirection = type == LedgerEntryType.SETTLEMENT ? LedgerDirection.CREDIT : LedgerDirection.DEBIT;

        return switch (transaction.getType()) {
            case TRANSFER -> List.of(
                    entry(transaction, transaction.getFromAccount(), sourceDirection, type),
                    entry(transaction, transaction.getToAccount(), destinationDirection, type));
            case DEPOSIT -> List.of(entry(transaction, transaction.getToAccount(), destinationDirection, type));
            case WITHDRAWAL -> List.of(entry(transaction, transaction.getFromAccount(), sourceDirection, type));
        };
    }

    private LedgerEntry entry(Transaction transaction, Account account, LedgerDirection direction, LedgerEntryType type) {
        return LedgerEntry.builder().transaction(transaction).account(account).direction(direction)
                .entryType(type).amount(transaction.getAmount())
                .currency(transaction.getCurrency() == null ? "USD" : transaction.getCurrency()).build();
    }
}
