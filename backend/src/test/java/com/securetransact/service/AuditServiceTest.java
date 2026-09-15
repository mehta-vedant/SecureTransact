package com.securetransact.service;

import com.securetransact.dto.AuditEventResponse;
import com.securetransact.dto.PaginatedResponse;
import com.securetransact.model.AuditAction;
import com.securetransact.model.AuditEvent;
import com.securetransact.model.Role;
import com.securetransact.model.User;
import com.securetransact.repository.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock private AuditEventRepository auditEventRepository;

    @InjectMocks
    private AuditService auditService;

    private User buildAdminUser() {
        return User.builder()
                .id(10L)
                .firstName("Admin")
                .lastName("User")
                .email("admin@test.com")
                .role(Role.ADMIN)
                .build();
    }

    @Test
    void shouldRecordAuditEvent() {
        when(auditEventRepository.save(any(AuditEvent.class))).thenAnswer(inv -> {
            AuditEvent ev = inv.getArgument(0);
            ev.setId(1L);
            return ev;
        });

        User admin = buildAdminUser();
        auditService.recordEvent(AuditAction.TRANSACTION_REJECTED, "TRANSACTION", 100L,
                "{\"score\":72}", admin, "127.0.0.1", "curl/8.0");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());
        AuditEvent saved = captor.getValue();
        assertEquals(admin, saved.getActor());
        assertEquals(AuditAction.TRANSACTION_REJECTED, saved.getAction());
        assertEquals("TRANSACTION", saved.getResourceType());
        assertEquals(100L, saved.getResourceId());
        assertEquals("127.0.0.1", saved.getIpAddress());
        assertEquals("curl/8.0", saved.getUserAgent());
    }

    @Test
    void shouldRecordEventWithoutResource() {
        when(auditEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        auditService.recordEvent(AuditAction.USER_LOGIN, null, null, "Login successful", null, null, null);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());
        AuditEvent saved = captor.getValue();
        assertNull(saved.getResourceType());
        assertNull(saved.getResourceId());
        assertEquals(AuditAction.USER_LOGIN, saved.getAction());
    }

    @Test
    void shouldQueryAuditEvents() {
        Page<AuditEvent> mockPage = new PageImpl<>(
                List.of(AuditEvent.builder().id(1L).action(AuditAction.USER_LOGIN)
                        .resourceType("SYSTEM").build()),
                PageRequest.of(0, 10),
                1
        );
        when(auditEventRepository.findAll(any(Pageable.class))).thenReturn(mockPage);

        PaginatedResponse<AuditEventResponse> result = auditService.getAuditEvents(0, 10);

        assertEquals(1, result.getContent().size());
        assertEquals(AuditAction.USER_LOGIN, result.getContent().get(0).getAction());
        assertEquals(1, result.getTotalElements());
        assertEquals(0, result.getPage());
    }

    @Test
    void shouldQueryEventsByAction() {
        Page<AuditEvent> mockPage = new PageImpl<>(
                List.of(
                        AuditEvent.builder().id(1L).action(AuditAction.TRANSACTION_REJECTED).resourceType("TRANSACTION").build(),
                        AuditEvent.builder().id(2L).action(AuditAction.TRANSACTION_REJECTED).resourceType("TRANSACTION").build()
                ),
                PageRequest.of(0, 10),
                2
        );
        when(auditEventRepository.findByAction(eq(AuditAction.TRANSACTION_REJECTED), any(Pageable.class)))
                .thenReturn(mockPage);

        PaginatedResponse<AuditEventResponse> result = auditService.getAuditEventsByAction(AuditAction.TRANSACTION_REJECTED, 0, 10);

        assertEquals(2, result.getContent().size());
        assertEquals(AuditAction.TRANSACTION_REJECTED, result.getContent().get(0).getAction());
    }

    @Test
    void shouldQueryEventsByResource() {
        Page<AuditEvent> mockPage = new PageImpl<>(
                List.of(AuditEvent.builder().id(1L).resourceType("TRANSACTION").resourceId(100L).build()),
                PageRequest.of(0, 10),
                1
        );
        when(auditEventRepository.findByResourceTypeAndResourceId(
                eq("TRANSACTION"), eq(100L), any(Pageable.class))).thenReturn(mockPage);

        PaginatedResponse<AuditEventResponse> result = auditService.getAuditEventsForResource("TRANSACTION", 100L, 0, 10);

        assertEquals(1, result.getContent().size());
        assertEquals(100L, result.getContent().get(0).getResourceId());
    }
}