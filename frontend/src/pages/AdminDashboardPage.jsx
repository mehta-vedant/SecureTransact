import { useState, useEffect, useCallback, useRef } from 'react';
import { Navigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
  LayoutDashboard, AlertTriangle, Users, Settings,
  Shield, LogOut, Bell, Menu, RefreshCw, AlertCircle,
  FileText, Activity, PlayCircle,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { admin as adminApi } from '../services/api';
import ThemeToggle from '../components/common/ThemeToggle';
import MetricsGrid from '../components/admin/MetricsGrid';
import FlaggedTable from '../components/admin/FlaggedTable';
import AllAccountsTable from '../components/admin/AllAccountsTable';
import RiskCasesTable from '../components/admin/RiskCasesTable';
import AuditLogViewer from '../components/admin/AuditLogViewer';
import DemoControls from '../components/admin/DemoControls';

/* ── helpers ─────────────────────────────────────── */
function greeting() {
  const h = new Date().getHours();
  if (h < 12) return 'Good morning';
  if (h < 17) return 'Good afternoon';
  return 'Good evening';
}

function fmtDate() {
  return new Date().toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' });
}

/* ── nav items ─────────────────────────────────────── */
const NAV = [
  { key: 'dashboard', icon: LayoutDashboard, label: 'Dashboard' },
  { key: 'flagged',   icon: AlertTriangle,   label: 'Flagged' },
  { key: 'riskcases', icon: FileText,        label: 'Risk Cases' },
  { key: 'demo',      icon: PlayCircle,      label: 'Live Demo' },
  { key: 'audit',     icon: Activity,        label: 'Audit Log' },
  { key: 'accounts',  icon: Users,           label: 'All Accounts' },
  { key: 'settings',  icon: Settings,        label: 'Settings' },
];

/* ── sidebar ─────────────────────────────────────── */
function Sidebar({ activeTab, onTabChange, onLogout, user, mobileOpen, onCloseMobile }) {
  return (
    <>
      <AnimatePresence>
        {mobileOpen && (
          <motion.div
            initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            onClick={onCloseMobile}
            style={{ position: 'fixed', inset: 0, zIndex: 90, background: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)' }}
          />
        )}
      </AnimatePresence>

      <motion.aside
        initial={false}
        animate={mobileOpen ? { x: 0 } : {}}
        style={{
          width: 240, flexShrink: 0,
          height: '100vh', position: 'sticky', top: 0,
          background: 'var(--bg-secondary)',
          borderRight: '1px solid var(--border-light)',
          display: 'flex', flexDirection: 'column',
          padding: '24px 16px',
          zIndex: 100,
        }}
        className="admin-sidebar"
      >
        {/* Logo */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 6, paddingLeft: 4 }}>
          <div style={{
            width: 32, height: 32, borderRadius: 9,
            background: 'linear-gradient(135deg, var(--accent), var(--accent-hover))',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            boxShadow: 'var(--shadow-accent)',
          }}>
            <Shield size={16} color="#fff" strokeWidth={2.2} />
          </div>
          <span style={{ fontFamily: 'var(--font-display)', fontStyle: 'italic', fontSize: 16, fontWeight: 600, color: 'var(--text-primary)' }}>
            SecureTransact
          </span>
        </div>
        <span style={{
          fontSize: 10, fontWeight: 700, letterSpacing: '0.08em',
          textTransform: 'uppercase', color: 'var(--accent)',
          paddingLeft: 4, marginBottom: 28,
          fontFamily: 'var(--font-body)',
        }}>
          Admin Panel
        </span>

        {/* Nav */}
        <nav style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 2 }}>
          {NAV.map(({ key, icon: Icon, label }) => {
            const isActive = activeTab === key;
            return (
              <button
                key={key}
                onClick={() => { onTabChange(key); onCloseMobile?.(); }}
                style={{
                  display: 'flex', alignItems: 'center', gap: 10,
                  padding: '10px 14px', borderRadius: 'var(--radius-md)',
                  fontSize: 13, fontWeight: 600, fontFamily: 'var(--font-body)',
                  color: isActive ? 'var(--accent)' : 'var(--text-secondary)',
                  background: isActive ? 'var(--accent-light)' : 'transparent',
                  border: 'none',
                  borderLeftStyle: 'solid',
                  borderLeftWidth: 3,
                  borderLeftColor: isActive ? 'var(--accent)' : 'transparent',
                  cursor: 'pointer',
                  transition: 'all 0.15s',
                  width: '100%',
                  textAlign: 'left',
                }}
                onMouseEnter={(e) => { if (!isActive) e.currentTarget.style.background = 'var(--bg-tertiary)'; }}
                onMouseLeave={(e) => { if (!isActive) e.currentTarget.style.background = 'transparent'; }}
              >
                <Icon size={16} strokeWidth={isActive ? 2.3 : 2} />
                {label}
              </button>
            );
          })}
        </nav>

        {/* Bottom */}
        <div style={{ borderTop: '1px solid var(--border-light)', paddingTop: 16, display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', paddingLeft: 4 }}>
            <span style={{ fontSize: 12, color: 'var(--text-muted)', fontFamily: 'var(--font-body)' }}>Appearance</span>
            <ThemeToggle />
          </div>

          <div style={{ padding: '10px 12px', borderRadius: 'var(--radius-md)', background: 'var(--bg-tertiary)' }}>
            <p style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-primary)', fontFamily: 'var(--font-body)', marginBottom: 2 }}>
              {user?.firstName} {user?.lastName}
            </p>
            <p style={{ fontSize: 11, color: 'var(--text-muted)', fontFamily: 'var(--font-body)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {user?.email}
            </p>
          </div>

          <button
            onClick={onLogout}
            style={{
              display: 'flex', alignItems: 'center', gap: 8,
              padding: '9px 14px', borderRadius: 'var(--radius-md)',
              background: 'none', border: 'none', cursor: 'pointer',
              color: 'var(--text-muted)', fontSize: 13, fontWeight: 600, fontFamily: 'var(--font-body)',
              width: '100%', transition: 'color 0.15s, background 0.15s',
            }}
            onMouseEnter={(e) => { e.currentTarget.style.color = 'var(--danger)'; e.currentTarget.style.background = 'var(--danger-bg)'; }}
            onMouseLeave={(e) => { e.currentTarget.style.color = 'var(--text-muted)'; e.currentTarget.style.background = 'none'; }}
          >
            <LogOut size={15} />
            Sign out
          </button>
        </div>
      </motion.aside>
    </>
  );
}

/* ── main page ───────────────────────────────────── */
export default function AdminDashboardPage() {
  const { user, isAuthenticated, isAdmin, loading: authLoading, logout } = useAuth();

  const [activeTab, setActiveTab]     = useState('dashboard');
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const [metrics, setMetrics]         = useState(null);
  const [flagged, setFlagged]         = useState([]);
  const [flaggedPage, setFlaggedPage] = useState(0);
  const [flaggedTotal, setFlaggedTotal] = useState(1);
  const [allAccts, setAllAccts]       = useState([]);
  const [acctsPage, setAcctsPage]     = useState(0);
  const [acctsTotal, setAcctsTotal]   = useState(1);

  const [riskPage, setRiskPage]     = useState(0);
  const [riskTotal, setRiskTotal]   = useState(1);
  const [auditPage, setAuditPage]   = useState(0);
  const [auditTotal, setAuditTotal] = useState(1);

  const [loading, setLoading]         = useState(true);
  const [error, setError]             = useState('');

  const intervalRef = useRef(null);

  /* ── data fetchers ── */
  const loadMetrics = useCallback(async () => {
    try {
      const data = await adminApi.getDashboard();
      setMetrics(data);
    } catch (err) {
      // silent for auto-refresh
    }
  }, []);

  const loadFlagged = useCallback(async (page = 0) => {
    try {
      const data = await adminApi.getFlagged(page, 10);
      const items = Array.isArray(data) ? data : (data?.content ?? []);
      setFlagged(items);
      setFlaggedTotal(data?.totalPages ?? 1);
      setFlaggedPage(page);
    } catch (err) {
      setError(err.message || 'Failed to load flagged transactions.');
    }
  }, []);

  const loadAccounts = useCallback(async (page = 0) => {
    try {
      const data = await adminApi.getAllAccounts(page, 20);
      const items = Array.isArray(data) ? data : (data?.content ?? []);
      setAllAccts(items);
      setAcctsTotal(data?.totalPages ?? 1);
      setAcctsPage(page);
    } catch (err) {
      setError(err.message || 'Failed to load accounts.');
    }
  }, []);

  const loadAll = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      await Promise.all([
        loadMetrics(),
        loadFlagged(0),
        loadAccounts(0),
      ]);
    } catch (err) {
      setError(err.message || 'Failed to load data.');
    } finally {
      setLoading(false);
    }
  }, [loadMetrics, loadFlagged, loadAccounts]);

  useEffect(() => { loadAll(); }, [loadAll]);

  // Auto-refresh metrics every 10s
  useEffect(() => {
    intervalRef.current = setInterval(loadMetrics, 10000);
    return () => clearInterval(intervalRef.current);
  }, [loadMetrics]);

  const handleReview = async (txnId, decision) => {
    try {
      await adminApi.reviewTransaction(txnId, decision);
      // Remove from list & refresh metrics
      setFlagged((prev) => prev.filter((t) => t.id !== txnId));
      loadMetrics();
    } catch (err) {
      setError(err.message || 'Review action failed.');
    }
  };

  if (authLoading) return null;
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (!isAdmin) return <Navigate to="/dashboard" replace />;

  return (
    <div style={{ display: 'flex', minHeight: '100vh', background: 'var(--bg-primary)', fontFamily: 'var(--font-body)' }}>
      <Sidebar
        user={user}
        onLogout={logout}
        activeTab={activeTab}
        onTabChange={setActiveTab}
        mobileOpen={sidebarOpen}
        onCloseMobile={() => setSidebarOpen(false)}
      />

      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
        {/* Top bar */}
        <header style={{
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          padding: '12px 20px',
          borderBottom: '1px solid var(--border-light)',
          background: 'var(--bg-primary)',
          position: 'sticky', top: 0, zIndex: 50,
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
            <button
              className="admin-sidebar-toggle"
              onClick={() => setSidebarOpen(true)}
              style={{ display: 'none', background: 'none', border: 'none', cursor: 'pointer', color: 'var(--text-muted)', padding: 4 }}
            >
              <Menu size={20} />
            </button>
            <div>
              <p style={{ fontSize: 16, fontWeight: 700, color: 'var(--text-primary)', fontFamily: 'var(--font-display)', marginBottom: 2 }}>
                {greeting()}, {user?.firstName}
              </p>
              <p style={{ fontSize: 11, color: 'var(--text-muted)' }}>{fmtDate()}</p>
            </div>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <button
              onClick={loadAll}
              title="Refresh"
              style={{
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                width: 36, height: 36, borderRadius: 'var(--radius-md)',
                background: 'var(--bg-secondary)', border: '1px solid var(--border-light)',
                color: 'var(--text-muted)', cursor: 'pointer', transition: 'color 0.15s',
              }}
              onMouseEnter={(e) => e.currentTarget.style.color = 'var(--accent)'}
              onMouseLeave={(e) => e.currentTarget.style.color = 'var(--text-muted)'}
            >
              <RefreshCw size={15} />
            </button>
            <button
              style={{
                position: 'relative',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                width: 36, height: 36, borderRadius: 'var(--radius-md)',
                background: 'var(--bg-secondary)', border: '1px solid var(--border-light)',
                color: 'var(--text-muted)', cursor: 'pointer',
              }}
            >
              <Bell size={15} />
              {(metrics?.flaggedTransactionsToday ?? 0) > 0 && (
                <span style={{
                  position: 'absolute', top: 6, right: 6,
                  width: 8, height: 8, borderRadius: '50%',
                  background: 'var(--danger)',
                  border: '1.5px solid var(--bg-primary)',
                }} />
              )}
            </button>
          </div>
        </header>

        {/* Page body */}
        <main style={{ flex: 1, padding: '20px', display: 'flex', flexDirection: 'column', gap: 24 }}>
          {/* Error banner */}
          <AnimatePresence>
            {error && (
              <motion.div
                initial={{ opacity: 0, y: -8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -8 }}
                style={{
                  display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                  gap: 10, padding: '12px 16px',
                  background: 'var(--danger-bg)', border: '1px solid var(--danger)',
                  borderRadius: 'var(--radius-md)', fontSize: 13, color: 'var(--danger)',
                }}
              >
                <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <AlertCircle size={14} /> {error}
                </span>
                <button onClick={loadAll} style={{ fontSize: 12, fontWeight: 600, color: 'var(--danger)', background: 'none', border: 'none', cursor: 'pointer', textDecoration: 'underline' }}>
                  Retry
                </button>
              </motion.div>
            )}
          </AnimatePresence>

          {/* Dashboard view */}
          {activeTab === 'dashboard' && (
            <AnimatePresence mode="wait">
              <motion.div key="dash" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
                style={{ display: 'flex', flexDirection: 'column', gap: 24 }}
              >
                {/* Metrics */}
                {loading ? (
                  <div style={{
                    display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 16,
                  }} className="metrics-grid">
                    {Array.from({ length: 6 }).map((_, i) => (
                      <div key={i} style={{
                        height: 90, borderRadius: 'var(--radius-xl)',
                        background: 'var(--bg-secondary)',
                        animation: 'shimmer 1.4s ease-in-out infinite',
                      }} />
                    ))}
                  </div>
                ) : (
                  <MetricsGrid metrics={metrics} />
                )}

                {/* Flagged table */}
                <section>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 14 }}>
                    <h2 style={{ fontSize: 15, fontWeight: 700, color: 'var(--text-primary)', fontFamily: 'var(--font-display)' }}>
                      Flagged Transactions
                    </h2>
                    <AlertTriangle size={14} color="var(--warning)" />
                    {flagged.length > 0 && (
                      <span style={{
                        padding: '2px 8px', borderRadius: 'var(--radius-full)',
                        background: 'var(--danger-bg)', color: 'var(--danger)',
                        fontSize: 11, fontWeight: 700, fontFamily: 'var(--font-body)',
                      }}>
                        {flagged.length}
                      </span>
                    )}
                  </div>
                  <div style={{
                    background: 'var(--bg-secondary)',
                    border: '1px solid var(--border-light)',
                    borderRadius: 'var(--radius-xl)',
                    overflow: 'hidden',
                  }}>
                    <FlaggedTable
                      transactions={flagged}
                      loading={loading}
                      page={flaggedPage}
                      totalPages={flaggedTotal}
                      onPageChange={loadFlagged}
                      onReview={handleReview}
                    />
                  </div>
                </section>
              </motion.div>
            </AnimatePresence>
          )}

          {/* Flagged tab (same view) */}
          {activeTab === 'flagged' && (
            <AnimatePresence mode="wait">
              <motion.div key="flagged" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                <section>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 14 }}>
                    <h2 style={{ fontSize: 15, fontWeight: 700, color: 'var(--text-primary)', fontFamily: 'var(--font-display)' }}>
                      Flagged Transactions
                    </h2>
                    <AlertTriangle size={14} color="var(--warning)" />
                    {flagged.length > 0 && (
                      <span style={{
                        padding: '2px 8px', borderRadius: 'var(--radius-full)',
                        background: 'var(--danger-bg)', color: 'var(--danger)',
                        fontSize: 11, fontWeight: 700, fontFamily: 'var(--font-body)',
                      }}>
                        {flagged.length}
                      </span>
                    )}
                  </div>
                  <div style={{
                    background: 'var(--bg-secondary)',
                    border: '1px solid var(--border-light)',
                    borderRadius: 'var(--radius-xl)',
                    overflow: 'hidden',
                  }}>
                    <FlaggedTable
                      transactions={flagged}
                      loading={loading}
                      page={flaggedPage}
                      totalPages={flaggedTotal}
                      onPageChange={loadFlagged}
                      onReview={handleReview}
                    />
                  </div>
                </section>
              </motion.div>
            </AnimatePresence>
          )}

          {/* Accounts tab */}
          {activeTab === 'accounts' && (
            <AnimatePresence mode="wait">
              <motion.div key="accounts" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                <section>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 14 }}>
                    <h2 style={{ fontSize: 15, fontWeight: 700, color: 'var(--text-primary)', fontFamily: 'var(--font-display)' }}>
                      All Accounts
                    </h2>
                    <Users size={14} color="var(--text-muted)" />
                  </div>
                  <div style={{
                    background: 'var(--bg-secondary)',
                    border: '1px solid var(--border-light)',
                    borderRadius: 'var(--radius-xl)',
                    overflow: 'hidden',
                  }}>
                    <AllAccountsTable
                      accounts={allAccts}
                      loading={loading}
                      page={acctsPage}
                      totalPages={acctsTotal}
                      onPageChange={loadAccounts}
                    />
                  </div>
                </section>
              </motion.div>
            </AnimatePresence>
          )}

          {/* Risk Cases tab */}
          {activeTab === 'riskcases' && (
            <AnimatePresence mode="wait">
              <motion.div key="riskcases" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                <section>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 14 }}>
                    <h2 style={{ fontSize: 15, fontWeight: 700, color: 'var(--text-primary)', fontFamily: 'var(--font-display)' }}>
                      Risk Cases
                    </h2>
                    <FileText size={14} color="var(--text-muted)" />
                  </div>
                  <div style={{
                    background: 'var(--bg-secondary)',
                    border: '1px solid var(--border-light)',
                    borderRadius: 'var(--radius-xl)',
                    overflow: 'hidden',
                  }}>
                    <RiskCasesTable
                      page={riskPage}
                      totalPages={riskTotal}
                      onPageChange={setRiskPage}
                    />
                  </div>
                </section>
              </motion.div>
            </AnimatePresence>
          )}

          {/* Live Demo tab */}
          {activeTab === 'demo' && (
            <AnimatePresence mode="wait">
              <motion.div key="demo" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                <section>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 14 }}>
                    <h2 style={{ fontSize: 15, fontWeight: 700, color: 'var(--text-primary)', fontFamily: 'var(--font-display)' }}>
                      Live Fraud Demo
                    </h2>
                    <PlayCircle size={14} color="var(--accent)" />
                  </div>
                  <div style={{
                    background: 'var(--bg-secondary)',
                    border: '1px solid var(--border-light)',
                    borderRadius: 'var(--radius-xl)',
                    padding: 20,
                  }}>
                    <DemoControls onEvent={() => { loadMetrics(); loadFlagged(0); }} />
                  </div>
                </section>
              </motion.div>
            </AnimatePresence>
          )}

          {/* Audit Log tab */}
          {activeTab === 'audit' && (
            <AnimatePresence mode="wait">
              <motion.div key="audit" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                <section>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 14 }}>
                    <h2 style={{ fontSize: 15, fontWeight: 700, color: 'var(--text-primary)', fontFamily: 'var(--font-display)' }}>
                      Audit Log
                    </h2>
                    <Activity size={14} color="var(--text-muted)" />
                  </div>
                  <div style={{
                    background: 'var(--bg-secondary)',
                    border: '1px solid var(--border-light)',
                    borderRadius: 'var(--radius-xl)',
                    overflow: 'hidden',
                  }}>
                    <AuditLogViewer
                      page={auditPage}
                      totalPages={auditTotal}
                      onPageChange={setAuditPage}
                    />
                  </div>
                </section>
              </motion.div>
            </AnimatePresence>
          )}
          {/* Settings tab placeholder */}
          {activeTab === 'settings' && (
            <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }}
              style={{
                padding: 40, textAlign: 'center',
                color: 'var(--text-muted)', fontSize: 14,
                fontFamily: 'var(--font-body)',
              }}
            >
              <Settings size={28} color="var(--text-muted)" style={{ margin: '0 auto 12px' }} />
              <p>Settings coming soon</p>
            </motion.div>
          )}
        </main>
      </div>

      <style>{`
        @keyframes shimmer { 0%,100%{opacity:0.5} 50%{opacity:1} }
        @media (max-width: 768px) {
          .admin-sidebar { position: fixed !important; left: 0; top: 0; transform: translateX(-100%); }
          .admin-sidebar-toggle { display: flex !important; }
          .metrics-grid { grid-template-columns: repeat(2, 1fr) !important; }
        }
      `}</style>
    </div>
  );
}
