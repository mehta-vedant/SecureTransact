import { useState, useEffect, useCallback } from 'react';
import { auditEvents } from '../../services/api';
import { Activity, Filter, ChevronDown, ChevronUp } from 'lucide-react';

function useIsMobile() {
  const [m, setM] = useState(typeof window !== 'undefined' && window.innerWidth <= 768);
  useEffect(() => {
    const h = () => setM(window.innerWidth <= 768);
    window.addEventListener('resize', h);
    return () => window.removeEventListener('resize', h);
  }, []);
  return m;
}

const ACTION_OPTIONS = [
  { value: '', label: 'All Actions' },
  { value: 'ACCOUNT_CREATED', label: 'Account Created' },
  { value: 'ACCOUNT_STATUS_CHANGED', label: 'Account Status Changed' },
  { value: 'TRANSACTION_CREATED', label: 'Transaction Created' },
  { value: 'TRANSACTION_SETTLED', label: 'Transaction Settled' },
  { value: 'TRANSACTION_HELD', label: 'Transaction Held' },
  { value: 'FRAUD_REVIEWED', label: 'Fraud Reviewed' },
  { value: 'USER_LOGIN', label: 'User Login' },
  { value: 'USER_LOGIN_FAILED', label: 'Login Failed' },
  { value: 'USER_LOGOUT', label: 'User Logout' },
];

const ACTION_COLORS = {
  ACCOUNT_CREATED: { bg: 'var(--success-bg)', color: 'var(--success)' },
  ACCOUNT_STATUS_CHANGED: { bg: 'var(--warning-bg)', color: 'var(--warning)' },
  TRANSACTION_CREATED: { bg: 'var(--accent-light)', color: 'var(--accent)' },
  TRANSACTION_SETTLED: { bg: 'var(--success-bg)', color: 'var(--success)' },
  TRANSACTION_HELD: { bg: 'var(--warning-bg)', color: 'var(--warning)' },
  FRAUD_REVIEWED: { bg: 'var(--danger-bg)', color: 'var(--danger)' },
  USER_LOGIN: { bg: 'var(--bg-tertiary)', color: 'var(--text-secondary)' },
  USER_LOGIN_FAILED: { bg: 'var(--danger-bg)', color: 'var(--danger)' },
  USER_LOGOUT: { bg: 'var(--bg-tertiary)', color: 'var(--text-muted)' },
};

function DetailJson({ details }) {
  if (!details) return <span style={{ color: 'var(--text-muted)', fontStyle: 'italic' }}>No details</span>;

  let parsed = details;
  if (typeof details === 'string') {
    try { parsed = JSON.parse(details); } catch { /* use raw string */ }
  }

  if (typeof parsed === 'string') {
    return <span style={{ color: 'var(--text-secondary)' }}>{parsed}</span>;
  }

  return (
    <div style={{ background: 'var(--bg-tertiary)', padding: '10px 12px', borderRadius: 'var(--radius-md)', fontSize: 11, fontFamily: 'var(--font-mono, monospace)', color: 'var(--text-secondary)', overflowX: 'auto', whiteSpace: 'pre-wrap', lineHeight: 1.6 }}>
      {JSON.stringify(parsed, null, 2)}
    </div>
  );
}

export default function AuditLogViewer({ page: controlledPage, totalPages: controlledTotalPages, onPageChange }) {
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [filterAction, setFilterAction] = useState('');
  const [expandedId, setExpandedId] = useState(null);
  const isMobile = useIsMobile();

  const loadEvents = useCallback(async (page = 0) => {
    setLoading(true);
    try {
      const data = filterAction
        ? await auditEvents.listByAction(filterAction, page, 30)
        : await auditEvents.list(page, 30);
      setEvents(data?.content ?? (Array.isArray(data) ? data : []));
    } catch {
      setEvents([]);
    } finally {
      setLoading(false);
    }
  }, [filterAction]);

  useEffect(() => { loadEvents(controlledPage ?? 0); }, [controlledPage, loadEvents]);

  const fmtDate = (iso) =>
    iso ? new Date(iso).toLocaleString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit' }) : '—';

  const fmt = (amount) =>
    typeof amount === 'number'
      ? new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(amount)
      : amount;

  return (
    <div>
      {/* Filter Bar */}
      <div style={{ padding: '14px 16px', display: 'flex', alignItems: 'center', gap: 10, borderBottom: '1px solid var(--border-light)' }}>
        <Filter size={14} color="var(--text-muted)" />
        <div style={{ position: 'relative' }}>
          <select
            value={filterAction}
            onChange={(e) => { setFilterAction(e.target.value); }}
            style={{
              appearance: 'none',
              background: 'var(--bg-tertiary)',
              border: '1px solid var(--border-light)',
              borderRadius: 'var(--radius-md)',
              padding: '7px 30px 7px 12px',
              fontSize: 12,
              fontWeight: 600,
              color: 'var(--text-primary)',
              fontFamily: 'var(--font-body)',
              cursor: 'pointer',
            }}
          >
            {ACTION_OPTIONS.map(({ value, label }) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
          <ChevronDown size={12} style={{ position: 'absolute', right: 10, top: '50%', transform: 'translateY(-50%)', pointerEvents: 'none', color: 'var(--text-muted)' }} />
        </div>
        <span style={{ fontSize: 11, color: 'var(--text-muted)', fontFamily: 'var(--font-body)' }}>
          {events.length} event{events.length !== 1 ? 's' : ''}
        </span>
      </div>

      {/* Log entries */}
      <div style={{ maxHeight: 500, overflow: 'auto' }}>
        {loading ? (
          Array.from({ length: 6 }).map((_, i) => (
            <div key={i} style={{ padding: '12px 16px', borderBottom: '1px solid var(--border-light)' }}>
              <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
                <div style={{ width: 80, height: 14, borderRadius: 4, background: 'var(--bg-tertiary)' }} />
                <div style={{ width: 140, height: 14, borderRadius: 4, background: 'var(--bg-tertiary)' }} />
                <div style={{ flex: 1, height: 14, borderRadius: 4, background: 'var(--bg-tertiary)' }} />
              </div>
            </div>
          ))
        ) : events.length === 0 ? (
          <div style={{ padding: '40px 16px', textAlign: 'center', color: 'var(--text-muted)', fontSize: 13 }}>
            <Activity size={24} style={{ margin: '0 auto 10px', opacity: 0.4 }} />
            <p style={{ margin: 0 }}>No audit events found</p>
          </div>
        ) : events.map((ev) => {
          const isExpanded = expandedId === ev.id;
          const style = ACTION_COLORS[ev.action] || { bg: 'var(--bg-tertiary)', color: 'var(--text-muted)' };
          return (
            <div key={ev.id} style={{ borderBottom: '1px solid var(--border-light)' }}>
              <div
                style={{ padding: isMobile ? '12px 16px' : '12px 16px', cursor: 'pointer', display: 'flex', flexDirection: isMobile ? 'column' : 'row', alignItems: isMobile ? 'stretch' : 'center', gap: isMobile ? 6 : 12, transition: 'background 0.1s' }}
                onClick={() => setExpandedId(isExpanded ? null : ev.id)}
                onMouseEnter={(e) => e.currentTarget.style.background = 'var(--bg-tertiary)'}
                onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
              >
                {isMobile ? (
                  <>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
                      <span style={{ padding: '3px 8px', borderRadius: 'var(--radius-full)', fontSize: 10, fontWeight: 700, background: style.bg, color: style.color, whiteSpace: 'nowrap' }}>
                        {ev.action?.replace(/_/g, ' ')}
                      </span>
                      <span style={{ fontSize: 10, color: 'var(--text-muted)', fontFamily: 'var(--font-mono, monospace)' }}>
                        {fmtDate(ev.createdAt)}
                      </span>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
                      <span style={{ fontSize: 12, color: 'var(--text-secondary)', fontFamily: 'var(--font-body)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', flex: 1 }}>
                        {ev.description || ev.resourceType ? `${ev.resourceType || ''} ${ev.resourceId ? `#${ev.resourceId}` : ''}`.trim() : '—'}
                      </span>
                      {isExpanded ? <ChevronUp size={14} color="var(--text-muted)" /> : <ChevronDown size={14} color="var(--text-muted)" />}
                    </div>
                  </>
                ) : (
                  <>
                    <span style={{ fontSize: 11, color: 'var(--text-muted)', minWidth: 100, fontFamily: 'var(--font-mono, monospace)' }}>
                      {fmtDate(ev.createdAt)}
                    </span>
                    <span style={{ padding: '3px 8px', borderRadius: 'var(--radius-full)', fontSize: 10, fontWeight: 700, background: style.bg, color: style.color, minWidth: 110, textAlign: 'center', whiteSpace: 'nowrap' }}>
                      {ev.action?.replace(/_/g, ' ')}
                    </span>
                    <span style={{ flex: 1, fontSize: 12, color: 'var(--text-secondary)', fontFamily: 'var(--font-body)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {ev.description || ev.resourceType ? `${ev.resourceType || ''} ${ev.resourceId ? `#${ev.resourceId}` : ''}`.trim() : '—'}
                    </span>
                    <span style={{ fontSize: 11, color: 'var(--text-muted)', fontFamily: 'var(--font-body)' }}>
                      {ev.performedBy || 'system'}
                    </span>
                    {isExpanded ? <ChevronUp size={14} color="var(--text-muted)" /> : <ChevronDown size={14} color="var(--text-muted)" />}
                  </>
                )}
              </div>
              {isExpanded && (
                <div style={{ padding: isMobile ? '0 16px 14px' : '0 16px 14px 128px' }}>
                  {ev.description && (
                    <p style={{ fontSize: 12, color: 'var(--text-secondary)', margin: '0 0 8px', fontFamily: 'var(--font-body)' }}>
                      {ev.description}
                    </p>
                  )}
                  <DetailJson details={ev.details} />
                  {ev.ipAddress && (
                    <p style={{ fontSize: 11, color: 'var(--text-muted)', margin: '8px 0 0', fontFamily: 'var(--font-body)' }}>
                      IP: {ev.ipAddress}
                    </p>
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* Pagination */}
      {controlledTotalPages > 1 && (
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, padding: '14px 16px', borderTop: '1px solid var(--border-light)' }}>
          {Array.from({ length: Math.min(controlledTotalPages, 7) }).map((_, i) => (
            <button
              key={i}
              onClick={() => onPageChange(i)}
              style={{
                width: 32, height: 32, borderRadius: 'var(--radius-md)',
                border: '1px solid', borderColor: (controlledPage ?? 0) === i ? 'var(--accent)' : 'var(--border-light)',
                background: (controlledPage ?? 0) === i ? 'var(--accent)' : 'transparent',
                color: (controlledPage ?? 0) === i ? '#fff' : 'var(--text-secondary)',
                fontSize: 12, fontWeight: 600, cursor: 'pointer', fontFamily: 'var(--font-body)',
              }}
            >
              {i + 1}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
