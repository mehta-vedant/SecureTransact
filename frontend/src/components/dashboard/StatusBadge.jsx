import { Check, Clock, Loader2, X, AlertTriangle, CircleDot } from 'lucide-react';

const keyframes = `
@keyframes status-spin { to { transform: rotate(360deg) } }
@keyframes status-pulse { 0%, 100% { box-shadow: 0 0 0 0 rgba(214,59,74,0.3); } 50% { box-shadow: 0 0 0 4px rgba(214,59,74,0.08); } }
`;

const statusConfig = {
  ACTIVE: {
    color: 'var(--success)',
    bg: 'var(--success-bg)',
    icon: CircleDot,
    label: 'Active',
  },
  COMPLETED: {
    color: 'var(--success)',
    bg: 'var(--success-bg)',
    icon: Check,
    label: 'Completed',
  },
  SETTLED: {
    color: 'var(--success)',
    bg: 'var(--success-bg)',
    icon: Check,
    label: 'Settled',
  },
  HELD_FOR_REVIEW: {
    color: 'var(--warning)',
    bg: 'var(--warning-bg)',
    icon: Clock,
    label: 'Under review',
  },
  REJECTED: {
    color: 'var(--danger)',
    bg: 'var(--danger-bg)',
    icon: X,
    label: 'Rejected',
  },
  REVERSED: {
    color: 'var(--info)',
    bg: 'var(--info-bg)',
    icon: AlertTriangle,
    label: 'Reversed',
  },
  CREATED: {
    color: 'var(--info)',
    bg: 'var(--info-bg)',
    icon: Loader2,
    label: 'Processing',
    spin: true,
  },
  RISK_EVALUATED: {
    color: 'var(--info)',
    bg: 'var(--info-bg)',
    icon: Loader2,
    label: 'Processing',
    spin: true,
  },
  APPROVED: {
    color: 'var(--info)',
    bg: 'var(--info-bg)',
    icon: Loader2,
    label: 'Processing',
    spin: true,
  },
  PENDING: {
    color: 'var(--warning)',
    bg: 'var(--warning-bg)',
    icon: Clock,
    label: 'Pending',
  },
  PROCESSING: {
    color: 'var(--info)',
    bg: 'var(--info-bg)',
    icon: Loader2,
    label: 'Processing',
    spin: true,
  },
  FAILED: {
    color: 'var(--danger)',
    bg: 'var(--danger-bg)',
    icon: X,
    label: 'Failed',
  },
  FLAGGED: {
    color: 'var(--danger)',
    bg: 'var(--danger-bg)',
    icon: AlertTriangle,
    label: 'Flagged',
    pulse: true,
  },
};

export default function StatusBadge({ status }) {
  const config = statusConfig[status] || statusConfig.PENDING;
  const Icon = config.icon;

  return (
    <>
      <style>{keyframes}</style>
      <span style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 5,
        padding: '4px 10px',
        fontSize: 11,
        fontFamily: 'var(--font-body)',
        fontWeight: 600,
        letterSpacing: '0.03em',
        color: config.color,
        background: config.bg,
        borderRadius: 'var(--radius-full)',
        lineHeight: 1,
        whiteSpace: 'nowrap',
        animation: config.pulse ? 'status-pulse 2s ease-in-out infinite' : 'none',
        border: config.pulse ? `1px solid ${config.color}` : '1px solid transparent',
      }}>
        <Icon
          size={12}
          strokeWidth={2.5}
          style={config.spin ? { animation: 'status-spin 1s linear infinite' } : {}}
        />
        {config.label}
      </span>
    </>
  );
}
