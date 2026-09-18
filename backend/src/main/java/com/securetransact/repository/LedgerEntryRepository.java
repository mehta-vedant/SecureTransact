package com.securetransact.repository;

import com.securetransact.model.LedgerEntry;
import com.securetransact.model.LedgerEntryType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    boolean existsByTransactionIdAndEntryType(Long transactionId, LedgerEntryType entryType);
}
