package com.securetransact.service;

import com.securetransact.exception.ConflictException;
import com.securetransact.model.*;
import com.securetransact.repository.LedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerPostingServiceTest {

    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @InjectMocks private LedgerPostingService ledgerPostingService;

    @Test
    void postsBalancedSettlementEntriesForTransfer() {
        Transaction payment = transfer();
        when(ledgerEntryRepository.existsByTransactionIdAndEntryType(12L, LedgerEntryType.SETTLEMENT)).thenReturn(false);

        ledgerPostingService.postSettlement(payment);

        ArgumentCaptor<List<LedgerEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(ledgerEntryRepository).saveAll(captor.capture());
        List<LedgerEntry> entries = captor.getValue();
        assertEquals(2, entries.size());
        assertEquals(LedgerDirection.DEBIT, entries.get(0).getDirection());
        assertEquals(LedgerDirection.CREDIT, entries.get(1).getDirection());
        assertEquals(entries.get(0).getAmount(), entries.get(1).getAmount());
        assertEquals(LedgerEntryType.SETTLEMENT, entries.get(0).getEntryType());
    }

    @Test
    void preventsDuplicateSettlementPosting() {
        when(ledgerEntryRepository.existsByTransactionIdAndEntryType(12L, LedgerEntryType.SETTLEMENT)).thenReturn(true);

        assertThrows(ConflictException.class, () -> ledgerPostingService.postSettlement(transfer()));
        verify(ledgerEntryRepository, never()).saveAll(any());
    }

    @Test
    void reversalUsesOppositeLedgerDirections() {
        when(ledgerEntryRepository.existsByTransactionIdAndEntryType(12L, LedgerEntryType.SETTLEMENT)).thenReturn(true);
        when(ledgerEntryRepository.existsByTransactionIdAndEntryType(12L, LedgerEntryType.REVERSAL)).thenReturn(false);

        ledgerPostingService.postReversal(transfer());

        ArgumentCaptor<List<LedgerEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(ledgerEntryRepository).saveAll(captor.capture());
        assertEquals(LedgerDirection.CREDIT, captor.getValue().get(0).getDirection());
        assertEquals(LedgerDirection.DEBIT, captor.getValue().get(1).getDirection());
        assertEquals(LedgerEntryType.REVERSAL, captor.getValue().get(0).getEntryType());
    }

    private Transaction transfer() {
        Account from = Account.builder().id(1L).accountNumber("STFROM").status(AccountStatus.ACTIVE)
                .balance(new BigDecimal("100.00")).build();
        Account to = Account.builder().id(2L).accountNumber("STTO").status(AccountStatus.ACTIVE)
                .balance(BigDecimal.ZERO).build();
        return Transaction.builder().id(12L).type(TransactionType.TRANSFER).amount(new BigDecimal("25.00"))
                .fromAccount(from).toAccount(to).status(TransactionStatus.SETTLED).build();
    }
}
