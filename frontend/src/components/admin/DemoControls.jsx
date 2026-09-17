import { useState, useEffect, useCallback } from 'react';
import { demo } from '../../services/api';
import {
  Zap, Activity, BadgeDollarSign, UserPlus, Ban, Radio, RefreshCw,
  CheckCircle2, XCircle, Clock,
} from 'lucide-react';

const SCENARIOS = [
  {
    key: 'triggerRapidTransfer',
    trigger: 'rapid-transfer',
    label: 'Rapid Transfer Burst',
    desc: 'Multiple quick transfers to the same destination',
    icon: Zap,
    color: 'var(--warning)',
  },
  {
    key: 'triggerHighVelocity',
    trigger: 'high-velocity',
    label: 'High-Velocity Burst',
    desc: 'Many small transactions in a short window',
    icon: Activity,
    color: 'var(--accent)',
  },
  {
    key: 'triggerLargeAmount',
    trigger: 'large-amount',
    label: 'Large Amount',
    desc: 'Single transaction far above your normal profile',
    icon: BadgeDollarSign,
    color: 'var(--danger)',
  },
  {
    key: 'triggerNewAccountLargeTxn',
    trigger: 'new-account-large-txn',
    label: 'New Account + Large Txn',
    desc: 'Fresh account suddenly moving large funds',
    icon: UserPlus,
    color: 'var(--violet)',
  },
  {
    key: 'triggerBlacklistTransfer',
    trigger: 'blacklist-transfer',
    label: 'Blacklist Transfer',
    desc: 'Payment to a blacklisted destination',
    icon: Ban,
    color: 'var(--danger)',
  },
];

const STATUS_COLORS = {
  SETTLED: { bg: 'var(--success-bg)', color: 'var(--success)' },
  HELD_FOR_REVIEW: { bg: 'var(--warning-bg)', color: 'var(--warning)' },
  REJECTED: { bg: 'var(--danger-bg)', color: 'var(--danger)' },
  FAILED: { bg: 'var(--bg-tertiary)', color: 'var(--text-muted)' },
};

function statusPill(status) {
  const s = STATUS_COLORS[status] || STATUS_COLORS.FAILED;
  return (
    <span style={{
      padding: '2px 8px', borderRadius: 'var(--radius-full)',
      background: s.bg, color: s.color, fontSize: 11, fontWeight: 700, fontFamily: 'var(--font-body)',
    }}>
      {status?.replace(/_/g, ' ')}
    </span>
  );
}

export default function DemoControls({ onEvent }) {
  const [ready, setReady] = useState(false);
  const [busy, setBusy] = useState(null);
  const [results, setResults] = useState([]);
  const [statusMsg, setStatusMsg] = useState('');

  const loadStatus = useCallback(async () => {
    try {
      const data = await demo.status();
      setReady(!!data?.demoMode);
    } catch {
      setReady(false);
    }
  }, []);

  useEffect(() => { loadStatus(); }, [loadStatus]);

  const runScenario = async (scenario) => {
    setBusy(scenario.key);
    setStatusMsg(`Triggering ${scenario.label}…`);
    try {
      const fn = demo[scenario.key];
      const txns = (await fn()) ?? [];
      const held = txns.filter((t) => t.status === 'HELD_FOR_REVIEW').length;
      const rejected = txns.filter((t) => t.status === 'REJECTED').length;
      const settled = txns.filter((t) => t.status === 'SETTLED').length;

      setResults((prev) => [
        {
          id: Date.now(),
          label: scenario.label,
          total: txns.length,
          held,
          rejected,
          settled,
          txns,
          color: scenario.color,
        },
        ...prev,
      ]);
      setStatusMsg(`Done: ${settled} settled, ${held} held for review, ${rejected} rejected.`);
      onEvent?.('refresh');
    } catch (err) {
      setStatusMsg(`Failed: ${err.message || 'unknown error'}`);
    } finally {
      setBusy(null);
    }
  };

  const fmt = (amount) =>
    new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(amount ?? 0);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* Readiness banner */}
      <div style={{
        display: 'flex', alignItems: 'center', gap: 12,
        padding: '10px 14px', borderRadius: 'var(--radius-md)',
        background: ready ? 'var(--success-bg)' : 'var(--danger-bg)',
        border: `1px solid ${ready ? 'var(--success)' : 'var(--danger)'}`,
        fontSize: 12, fontWeight: 600, color: ready ? 'var(--success)' : 'var(--danger)',
        fontFamily: 'var(--font-body)',
      }}>
        <Radio size={14} />
        {ready
          ? 'Demo mode is live — ambient traffic streams to the audit log; fire a scenario to see the engine flag it.'
          : 'Demo mode is off (APP_DEMO_ENABLED=false). Enable it on the backend to use the live simulator.'}
        <button
          onClick={loadStatus}
          title="Re-check"
          style={{ marginLeft: 'auto', background: 'none', border: 'none', color: 'inherit', cursor: 'pointer', display: 'flex' }}
        >
          <RefreshCw size={12} />
        </button>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: 12 }}>
        {SCENARIOS.map((s) => {
          const Icon = s.icon;
          const loading = busy === s.key;
          return (
            <button
              key={s.key}
              onClick={() => runScenario(s)}
              disabled={!!busy || !ready}
              style={{
                display: 'flex', alignItems: 'flex-start', gap: 10,
                padding: '14px 16px', borderRadius: 'var(--radius-md)',
                background: 'var(--bg-tertiary)', border: `1px solid ${s.color}45`,
                textAlign: 'left', cursor: busy || !ready ? 'not-allowed' : 'pointer',
                opacity: busy || !ready ? 0.6 : 1, transition: 'all 0.15s',
                fontFamily: 'var(--font-body)',
              }}
              onMouseEnter={(e) => { if (!busy && ready) e.currentTarget.style.background = 'var(--accent-light)'; }}
              onMouseLeave={(e) => { e.currentTarget.style.background = 'var(--bg-tertiary)'; }}
            >
              <div style={{
                width: 30, height: 30, borderRadius: 8, flexShrink: 0,
                background: `${s.color}18`, color: s.color,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                {loading ? <RefreshCw size={14} className="demo-spin" /> : <Icon size={14} />}
              </div>
              <div style={{ minWidth: 0 }}>
                <p style={{ margin: 0, fontSize: 13, fontWeight: 700, color: 'var(--text-primary)' }}>{s.label}</p>
                <p style={{ margin: '2px 0 0', fontSize: 11, color: 'var(--text-muted)' }}>{s.desc}</p>
              </div>
            </button>
          );
        })}
      </div>

      {statusMsg && (
        <p style={{ margin: 0, fontSize: 12, color: 'var(--text-secondary)', fontFamily: 'var(--font-body)' }}>
          {statusMsg}
        </p>
      )}

      {/* Results feed */}
      {results.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {results.map((r) => (
            <div key={r.id} style={{
              border: '1px solid var(--border-light)', borderRadius: 'var(--radius-md)',
              background: 'var(--bg-secondary)', overflow: 'hidden',
            }}>
              <div style={{
                display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                padding: '10px 14px', borderBottom: '1px solid var(--border-light)',
              }}>
                <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--text-primary)', fontFamily: 'var(--font-body)' }}>
                  {r.label}
                </span>
                <span style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 11, color: 'var(--text-muted)', fontFamily: 'var(--font-body)' }}>
                  {r.settled > 0 && <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}><CheckCircle2 size={12} color="var(--success)" />{r.settled}</span>}
                  {r.held > 0 && <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}><Clock size={12} color="var(--warning)" />{r.held}</span>}
                  {r.rejected > 0 && <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}><XCircle size={12} color="var(--danger)" />{r.rejected}</span>}
                </span>
              </div>
              <div style={{ maxHeight: 180, overflow: 'auto' }}>
                {r.txns.map((t, i) => (
                  <div key={i} style={{
                    display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                    padding: '8px 14px', borderBottom: '1px solid var(--border-light)',
                    fontSize: 12, fontFamily: 'var(--font-body)',
                  }}>
                    <span style={{ display: 'flex', alignItems: 'center', gap: 8, color: 'var(--text-secondary)' }}>
                      <span style={{ fontWeight: 600, color: 'var(--text-primary)' }}>${fmt(t.amount)}</span>
                      <span style={{ color: 'var(--text-muted)' }}>
                        score {t.riskScore ?? '—'} · {t.riskDecision ?? '—'}
                      </span>
                    </span>
                    {statusPill(t.status)}
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}

      <style>{`
        @keyframes demo-spin { to { transform: rotate(360deg); } }
        .demo-spin { animation: demo-spin 0.8s linear infinite; }
      `}</style>
    </div>
  );
}