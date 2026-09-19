import { motion } from 'framer-motion';
import {
  ArrowLeftRight, DollarSign, AlertTriangle, Users,
  CheckCircle, XCircle,
  Inbox, Timer,
} from 'lucide-react';

function fmt(n) {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(n ?? 0);
}

const CARDS = [
  {
    key: 'openRiskCases',
    label: 'Open Review Queue',
    icon: Inbox,
    color: 'var(--warning)',
    bg: 'var(--warning-bg)',
    format: (v) => v ?? 0,
  },
  {
    key: 'oldestOpenCaseMinutes',
    label: 'Oldest Case Age',
    icon: Timer,
    color: 'var(--danger)',
    bg: 'var(--danger-bg)',
    format: (v) => `${v ?? 0} min`,
    accentWhen: (v) => v > 30,
    accentColor: 'var(--danger)',
    accentBg: 'var(--danger-bg)',
  },
  {
    key: 'totalTransactionsToday',
    label: 'Transactions Today',
    icon: ArrowLeftRight,
    color: 'var(--accent)',
    bg: 'var(--accent-glow)',
    format: (v) => v ?? 0,
  },
  {
    key: 'totalVolumeToday',
    label: 'Volume Today',
    icon: DollarSign,
    color: 'var(--info)',
    bg: 'var(--info-bg)',
    format: (v) => fmt(v),
  },
  {
    key: 'flaggedTransactionsToday',
    label: 'Flagged Today',
    icon: AlertTriangle,
    color: 'var(--warning)',
    bg: 'var(--warning-bg)',
    format: (v) => v ?? 0,
    accentWhen: (v) => v > 0,
    accentColor: 'var(--danger)',
    accentBg: 'var(--danger-bg)',
  },
  {
    key: 'activeAccounts',
    label: 'Active Accounts',
    icon: Users,
    color: 'var(--text-secondary)',
    bg: 'var(--bg-tertiary)',
    format: (v) => v ?? 0,
  },
  {
    key: 'completedTransactionsToday',
    label: 'Completed Today',
    icon: CheckCircle,
    color: 'var(--success)',
    bg: 'var(--success-bg)',
    format: (v) => v ?? 0,
  },
  {
    key: 'failedTransactionsToday',
    label: 'Failed Today',
    icon: XCircle,
    color: 'var(--danger)',
    bg: 'var(--danger-bg)',
    format: (v) => v ?? 0,
    accentWhen: (v) => v > 0,
    accentColor: 'var(--danger)',
    accentBg: 'var(--danger-bg)',
  },
];

export default function MetricsGrid({ metrics }) {
  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: 'repeat(3, 1fr)',
      gap: 16,
    }} className="metrics-grid">
      {CARDS.map((card, i) => {
        const val = metrics?.[card.key];
        const shouldAccent = card.accentWhen?.(val);
        const color = shouldAccent ? card.accentColor : card.color;
        const bg = shouldAccent ? card.accentBg : card.bg;
        const Icon = card.icon;

        return (
          <motion.div
            key={card.key}
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: i * 0.05, duration: 0.35 }}
            style={{
              padding: '20px',
              borderRadius: 'var(--radius-xl)',
              background: 'var(--bg-secondary)',
              border: '1px solid var(--border-light)',
              display: 'flex',
              alignItems: 'flex-start',
              gap: 14,
            }}
          >
            <div style={{
              width: 42, height: 42, borderRadius: 12,
              background: bg,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              flexShrink: 0,
            }}>
              <Icon size={18} color={color} strokeWidth={2.2} />
            </div>
            <div>
              <p style={{
                fontFamily: 'var(--font-display)',
                fontSize: 24,
                fontWeight: 700,
                color: 'var(--text-primary)',
                lineHeight: 1.1,
                marginBottom: 4,
              }}>
                {card.format(val)}
              </p>
              <p style={{
                fontSize: 12,
                color: 'var(--text-muted)',
                fontFamily: 'var(--font-body)',
                fontWeight: 500,
              }}>
                {card.label}
              </p>
            </div>
          </motion.div>
        );
      })}

      <style>{`
        @media (max-width: 768px) {
          .metrics-grid { grid-template-columns: repeat(2, 1fr) !important; }
        }
        @media (max-width: 480px) {
          .metrics-grid { grid-template-columns: 1fr !important; }
        }
      `}</style>
    </div>
  );
}
