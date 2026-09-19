import { useState, useEffect, useCallback } from 'react';
import { admin, riskCases } from '../../services/api';
import { AlertTriangle, UserCheck, CheckCircle, XCircle, Clock, Eye, Inbox } from 'lucide-react';

const STATUS_COLORS = {
  OPEN: { bg: 'var(--warning-bg)', color: 'var(--warning)' },
  IN_REVIEW: { bg: 'var(--accent-light)', color: 'var(--accent)' },
  RESOLVED_FRAUD: { bg: 'var(--danger-bg)', color: 'var(--danger)' },
  RESOLVED_LEGITIMATE: { bg: 'var(--success-bg)', color: 'var(--success)' },
  DISMISSED: { bg: 'var(--bg-tertiary)', color: 'var(--text-muted)' },
};

const DECISION_OPTIONS = [
  { value: 'BLOCK', label: 'Block Transaction', color: 'var(--danger)', icon: XCircle },
  { value: 'ALLOW', label: 'Approve Payment', color: 'var(--success)', icon: CheckCircle },
];

function MobileRiskCaseCard({ c, onClick }) {
  const style = STATUS_COLORS[c.status] || STATUS_COLORS.OPEN;
  return (
    <div
      onClick={() => onClick(c)}
      style={{
        padding: '14px 16px',
        borderBottom: '1px solid var(--border-light)',
        cursor: 'pointer',
        display: 'flex',
        flexDirection: 'column',
        gap: 10,
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <span style={{ fontWeight: 600, color: 'var(--text-primary)', fontSize: 13 }}>#{c.id}</span>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <div style={{ width: 40, height: 5, borderRadius: 3, background: 'var(--bg-tertiary)', overflow: 'hidden' }}>
              <div style={{ height: '100%', width: `${Math.min(c.riskScore ?? 0, 100)}%`, borderRadius: 3, background: (c.riskScore ?? 0) > 70 ? 'var(--danger)' : (c.riskScore ?? 0) > 40 ? 'var(--warning)' : 'var(--success)' }} />
            </div>
            <span style={{ fontWeight: 600, color: 'var(--text-primary)', fontSize: 12 }}>{c.riskScore ?? '—'}</span>
          </div>
        </div>
        <span style={{ padding: '3px 10px', borderRadius: 'var(--radius-full)', fontSize: 11, fontWeight: 700, background: style.bg, color: style.color }}>
          {c.status?.replace(/_/g, ' ')}
        </span>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <span style={{ color: 'var(--text-secondary)', fontSize: 12 }}>{c.assignedToName || 'Unassigned'}</span>
        <span style={{ color: 'var(--text-muted)', fontSize: 11 }}>
          {c.createdAt ? new Date(c.createdAt).toLocaleString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—'}
        </span>
      </div>
    </div>
  );
}

export default function RiskCasesTable({ page: controlledPage, totalPages, onPageChange }) {
  const [cases, setCases] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedCase, setSelectedCase] = useState(null);
  const [deciding, setDeciding] = useState(null);
  const [notes, setNotes] = useState('');
  const [caseNote, setCaseNote] = useState('');
  const [actionLoading, setActionLoading] = useState(false);
  const [isMobile, setIsMobile] = useState(false);

  useEffect(() => {
    const check = () => setIsMobile(window.innerWidth <= 768);
    check();
    window.addEventListener('resize', check);
    return () => window.removeEventListener('resize', check);
  }, []);

  const loadCases = useCallback(async (page = 0) => {
    setLoading(true);
    try {
      const data = await riskCases.list(page, 20);
      setCases(data?.content ?? (Array.isArray(data) ? data : []));
    } catch {
      setCases([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadCases(controlledPage ?? 0); }, [controlledPage, loadCases]);

  const handleAssign = async (id) => {
    try {
      await riskCases.assign(id);
      setCases((prev) =>
        prev.map((c) =>
          c.id === id ? { ...c, status: 'IN_REVIEW', assignedToName: 'Current Admin' } : c
        )
      );
    } catch {
      /* silent */
    }
  };

  const handleDecide = async (id, decision) => {
    setActionLoading(true);
    try {
      await riskCases.decide(id, { decision, reviewNotes: notes });
      setDeciding(null);
      setNotes('');
      loadCases(controlledPage ?? 0);
    } catch {
      /* silent */
    } finally {
      setActionLoading(false);
    }
  };

  const handleAddNote = async (id) => {
    if (!caseNote.trim()) return;
    setActionLoading(true);
    try {
      const updated = await riskCases.addNote(id, caseNote);
      setSelectedCase(updated);
      setCaseNote('');
      loadCases(controlledPage ?? 0);
    } finally {
      setActionLoading(false);
    }
  };

  const handleFreezePayer = async () => {
    if (!selectedCase?.fromAccountId) return;
    setActionLoading(true);
    try {
      await admin.freezeAccount(selectedCase.fromAccountId, `Frozen from risk case #${selectedCase.id}`);
      const updated = await riskCases.addNote(selectedCase.id, 'Payer account frozen from this case.');
      setSelectedCase(updated);
      loadCases(controlledPage ?? 0);
    } finally {
      setActionLoading(false);
    }
  };

  const fmt = (amount) =>
    new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(amount);

  const fmtDate = (iso) =>
    iso ? new Date(iso).toLocaleString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—';

  return (
    <div>
      {/* Detail / Decide Modal */}
      {selectedCase && (
        <div style={{ position: 'fixed', inset: 0, zIndex: 200, display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(6px)' }}>
          <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-light)', borderRadius: 'var(--radius-xl)', padding: 28, width: '90%', maxWidth: 520, maxHeight: '80vh', overflow: 'auto' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
              <h3 style={{ fontSize: 16, fontWeight: 700, color: 'var(--text-primary)', fontFamily: 'var(--font-display)', margin: 0 }}>
                Risk Case Detail
              </h3>
              <button onClick={() => { setSelectedCase(null); setDeciding(null); setNotes(''); }}
                style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: 18 }}>×</button>
            </div>

            <div style={{ display: 'grid', gap: 12 }}>
              {[
                ['Case ID', `#${selectedCase.id}`],
                ['Transaction ID', selectedCase.transactionId],
                ['Risk Score', `${selectedCase.riskScore ?? '—'} / 100`],
                ['Risk Level', selectedCase.riskLevel || '—'],
                ['Priority', selectedCase.priority || 'MEDIUM'],
                ['Status', selectedCase.status?.replace(/_/g, ' ')],
                ['Created', fmtDate(selectedCase.createdAt)],
                ['Assigned To', selectedCase.assignedToName || 'Unassigned'],
              ].map(([label, value]) => (
                <div key={label} style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span style={{ fontSize: 12, color: 'var(--text-muted)', fontFamily: 'var(--font-body)' }}>{label}</span>
                  <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-primary)', fontFamily: 'var(--font-body)' }}>{value}</span>
                </div>
              ))}
            {selectedCase.reviewNotes && (
                <div style={{ marginTop: 4 }}>
                  <span style={{ fontSize: 12, color: 'var(--text-muted)', fontFamily: 'var(--font-body)', display: 'block', marginBottom: 4 }}>Notes</span>
                  <p style={{ fontSize: 13, color: 'var(--text-primary)', fontFamily: 'var(--font-body)', background: 'var(--bg-tertiary)', padding: '10px 12px', borderRadius: 'var(--radius-md)', margin: 0 }}>
                    {selectedCase.reviewNotes}
                  </p>
                </div>
              )}
            </div>

            {selectedCase.status === 'OPEN' && (
              <div style={{ marginTop: 20, display: 'flex', gap: 10 }}>
                <button
                  onClick={() => handleAssign(selectedCase.id)}
                  style={{ flex: 1, padding: '10px 0', borderRadius: 'var(--radius-md)', border: '1px solid var(--accent)', background: 'var(--accent-light)', color: 'var(--accent)', fontSize: 13, fontWeight: 700, fontFamily: 'var(--font-body)', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}
                >
                  <UserCheck size={14} /> Assign to Me
                </button>
              </div>
            )}
            {selectedCase.riskReasons && (
              <div style={{ marginTop: 4 }}>
                <span style={{ fontSize: 12, color: 'var(--text-muted)', fontFamily: 'var(--font-body)', display: 'block', marginBottom: 4 }}>Why this payment was held</span>
                <ul style={{ fontSize: 13, color: 'var(--text-primary)', background: 'var(--bg-tertiary)', padding: '10px 12px 10px 28px', borderRadius: 'var(--radius-md)', margin: 0, display: 'grid', gap: 6 }}>
                  {selectedCase.riskReasons.split('||').map((reason) => <li key={reason}>{reason}</li>)}
                </ul>
                {selectedCase.anomalySignal != null && (
                  <p style={{ fontSize: 12, color: 'var(--text-secondary)', margin: '8px 0 0' }}>
                    Anomaly signal: {Math.round(selectedCase.anomalySignal * 100)}% ({selectedCase.modelVersion || 'unknown model'})
                  </p>
                )}
              </div>
            )}
            <div style={{ marginTop: 16 }}>
              <span style={{ fontSize: 12, color: 'var(--text-muted)', display: 'block', marginBottom: 6 }}>Case timeline</span>
              <div style={{ display: 'grid', gap: 8, maxHeight: 160, overflowY: 'auto' }}>
                {(selectedCase.timeline ?? []).map((event, index) => (
                  <div key={`${event.createdAt}-${index}`} style={{ borderLeft: '2px solid var(--accent)', paddingLeft: 10 }}>
                    <p style={{ fontSize: 12, color: 'var(--text-primary)', margin: 0 }}>{event.message}</p>
                    <p style={{ fontSize: 11, color: 'var(--text-muted)', margin: '2px 0 0' }}>{event.actorName || 'System'} · {fmtDate(event.createdAt)}</p>
                  </div>
                ))}
              </div>
              <div style={{ display: 'flex', gap: 8, marginTop: 10 }}>
                <input value={caseNote} onChange={(e) => setCaseNote(e.target.value)} placeholder="Add investigation note"
                  style={{ flex: 1, background: 'var(--bg-tertiary)', border: '1px solid var(--border-light)', borderRadius: 'var(--radius-md)', padding: '8px 10px', fontSize: 12, color: 'var(--text-primary)' }} />
                <button onClick={() => handleAddNote(selectedCase.id)} disabled={actionLoading || !caseNote.trim()}
                  style={{ border: '1px solid var(--accent)', background: 'var(--accent-light)', color: 'var(--accent)', borderRadius: 'var(--radius-md)', padding: '0 10px', fontSize: 12, fontWeight: 700, cursor: 'pointer' }}>Add</button>
              </div>
            </div>
            {selectedCase.fromAccountId && selectedCase.status !== 'APPROVED' && (
              <button onClick={handleFreezePayer} disabled={actionLoading}
                style={{ width: '100%', marginTop: 12, padding: '9px 0', borderRadius: 'var(--radius-md)', border: '1px solid var(--danger)', background: 'var(--danger-bg)', color: 'var(--danger)', fontSize: 12, fontWeight: 700, cursor: 'pointer' }}>
                Freeze payer account
              </button>
            )}

            {selectedCase.status === 'IN_REVIEW' && (
              <div style={{ marginTop: 20 }}>
                {deciding ? (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                    <textarea
                      value={notes}
                      onChange={(e) => setNotes(e.target.value)}
                      placeholder="Add review notes (optional)"
                      rows={3}
                      style={{ background: 'var(--bg-tertiary)', border: '1px solid var(--border-light)', borderRadius: 'var(--radius-md)', padding: '10px 12px', fontSize: 13, color: 'var(--text-primary)', fontFamily: 'var(--font-body)', resize: 'vertical' }}
                    />
                    <div style={{ display: 'flex', gap: 10 }}>
                      {DECISION_OPTIONS.map(({ value, label, color, icon: Icon }) => (
                        <button
                          key={value}
                          onClick={() => handleDecide(selectedCase.id, value)}
                          disabled={actionLoading}
                          style={{
                            flex: 1, padding: '10px 0', borderRadius: 'var(--radius-md)',
                            border: `1px solid ${color}`, background: `${color}15`, color,
                            fontSize: 13, fontWeight: 700, fontFamily: 'var(--font-body)',
                            cursor: actionLoading ? 'not-allowed' : 'pointer', opacity: actionLoading ? 0.6 : 1,
                            display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6,
                          }}
                        >
                          <Icon size={14} /> {label}
                        </button>
                      ))}
                    </div>
                    <button onClick={() => setDeciding(null)} style={{ background: 'none', border: 'none', color: 'var(--text-muted)', fontSize: 12, cursor: 'pointer', fontFamily: 'var(--font-body)' }}>Cancel</button>
                  </div>
                ) : (
                  <button
                    onClick={() => setDeciding(true)}
                    style={{ width: '100%', padding: '10px 0', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-light)', background: 'var(--bg-tertiary)', color: 'var(--text-primary)', fontSize: 13, fontWeight: 700, fontFamily: 'var(--font-body)', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}
                  >
                    <Eye size={14} /> Make Decision
                  </button>
                )}
              </div>
            )}
          </div>
        </div>
      )}

      {/* Table */}
      {isMobile ? (
        <div>
          {loading ? (
            Array.from({ length: 5 }).map((_, i) => (
              <div key={i} style={{ padding: '14px 16px', borderBottom: '1px solid var(--border-light)' }}>
                <div style={{ height: 12, width: '40%', borderRadius: 4, background: 'var(--bg-tertiary)', animation: 'shimmer 1.4s ease-in-out infinite', marginBottom: 8 }} />
                <div style={{ height: 10, width: '60%', borderRadius: 4, background: 'var(--bg-tertiary)', animation: 'shimmer 1.4s ease-in-out infinite' }} />
              </div>
            ))
          ) : cases.length === 0 ? (
            <div style={{ padding: '40px 16px', textAlign: 'center', color: 'var(--text-muted)' }}>
              <AlertTriangle size={24} style={{ margin: '0 auto 10px', opacity: 0.4 }} />
              <p style={{ margin: 0 }}>No risk cases found</p>
            </div>
          ) : (
            cases.map((c) => (
              <MobileRiskCaseCard key={c.id} c={c} onClick={setSelectedCase} />
            ))
          )}
        </div>
      ) : (
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13, fontFamily: 'var(--font-body)' }}>
        <thead>
          <tr style={{ borderBottom: '1px solid var(--border-light)' }}>
            {['#', 'Fraud Score', 'Status', 'Assigned To', 'Created', ''].map((h) => (
              <th key={h} style={{ padding: '12px 16px', textAlign: 'left', fontSize: 11, fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--text-muted)' }}>{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {loading ? (
            Array.from({ length: 5 }).map((_, i) => (
              <tr key={i}>
                {Array.from({ length: 6 }).map((_, j) => (
                  <td key={j} style={{ padding: '14px 16px' }}>
                    <div style={{ height: 14, borderRadius: 4, background: 'var(--bg-tertiary)', animation: 'shimmer 1.4s ease-in-out infinite', width: `${40 + Math.random() * 40}%` }} />
                  </td>
                ))}
              </tr>
            ))
          ) : cases.length === 0 ? (
            <tr>
              <td colSpan={6} style={{ padding: '40px 16px', textAlign: 'center', color: 'var(--text-muted)' }}>
                <AlertTriangle size={24} style={{ margin: '0 auto 10px', opacity: 0.4 }} />
                <p style={{ margin: 0 }}>No risk cases found</p>
              </td>
            </tr>
          ) : cases.map((c) => {
            const style = STATUS_COLORS[c.status] || STATUS_COLORS.OPEN;
            return (
              <tr key={c.id} style={{ borderBottom: '1px solid var(--border-light)', cursor: 'pointer' }}
                onClick={() => setSelectedCase(c)}
                onMouseEnter={(e) => e.currentTarget.style.background = 'var(--bg-tertiary)'}
                onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
              >
                <td style={{ padding: '12px 16px', fontWeight: 600, color: 'var(--text-primary)' }}>#{c.id}</td>
                <td style={{ padding: '12px 16px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <div style={{ width: 60, height: 6, borderRadius: 3, background: 'var(--bg-tertiary)', overflow: 'hidden' }}>
                      <div style={{ height: '100%', width: `${Math.min(c.riskScore ?? 0, 100)}%`, borderRadius: 3, background: (c.riskScore ?? 0) > 70 ? 'var(--danger)' : (c.riskScore ?? 0) > 40 ? 'var(--warning)' : 'var(--success)' }} />
                    </div>
                    <span style={{ fontWeight: 600, color: 'var(--text-primary)', fontSize: 12 }}>{c.riskScore ?? '—'}</span>
                  </div>
                </td>
                <td style={{ padding: '12px 16px' }}>
                  <span style={{ padding: '3px 10px', borderRadius: 'var(--radius-full)', fontSize: 11, fontWeight: 700, background: style.bg, color: style.color }}>
                    {c.status?.replace(/_/g, ' ')}
                  </span>
                </td>
                <td style={{ padding: '12px 16px', color: 'var(--text-secondary)' }}>{c.assignedToName || '—'}</td>
                <td style={{ padding: '12px 16px', color: 'var(--text-muted)', fontSize: 12 }}>{fmtDate(c.createdAt)}</td>
                <td style={{ padding: '12px 16px' }}>
                  <Clock size={14} color="var(--text-muted)" />
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, padding: '14px 16px', borderTop: '1px solid var(--border-light)' }}>
          {Array.from({ length: totalPages }).map((_, i) => (
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

      <style>{`@keyframes shimmer { 0%,100%{opacity:0.5} 50%{opacity:1} }`}</style>
    </div>
  );
}
