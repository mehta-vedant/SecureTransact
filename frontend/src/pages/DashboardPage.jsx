import { useState, useEffect, useCallback } from 'react';
import { Navigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
  LayoutDashboard, Wallet, ArrowLeftRight, Settings,
  Shield, LogOut, Bell, ArrowDownLeft, ArrowUpRight, Menu,
  RefreshCw, AlertCircle, FileText, X, User, Lock, Sun, Moon, Eye, EyeOff, Check, Save,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import { accounts as accountsApi, transactions as txnApi, userProfile as profileApi } from '../services/api';
import ThemeToggle from '../components/common/ThemeToggle';
import BalanceOverview from '../components/dashboard/BalanceOverview';
import AccountCard, { AddAccountCard } from '../components/dashboard/AccountCard';
import TransactionTable from '../components/dashboard/TransactionTable';
import { useToast } from '../components/common/Toast';
import CreateAccountModal from '../components/dashboard/CreateAccountModal';
import NewTransactionModal from '../components/dashboard/NewTransactionModal';
import StatusBadge from '../components/dashboard/StatusBadge';

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
  { key: 'dashboard',    icon: LayoutDashboard, label: 'Dashboard' },
  { key: 'accounts',     icon: Wallet,           label: 'Accounts'  },
  { key: 'transactions', icon: ArrowLeftRight,   label: 'Transactions' },
  { key: 'settings',     icon: Settings,         label: 'Settings'  },
];

/* ── sidebar ─────────────────────────────────────── */
function Sidebar({ activeTab, onTabChange, onLogout, user, mobileOpen, onCloseMobile }) {
  return (
    <>
      {/* Mobile backdrop */}
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
        className="sidebar"
      >
        {/* Logo */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 32, paddingLeft: 4 }}>
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
                  borderLeft: `3px solid ${isActive ? 'var(--accent)' : 'transparent'}`,
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

        {/* Bottom: theme + user + logout */}
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

/* ── settings panel ──────────────────────────────── */
function SettingsPanel({ user, onProfileUpdate }) {
  const { theme, toggle: toggleTheme } = useTheme();

  // Profile
  const [firstName, setFirstName] = useState(user?.firstName || '');
  const [lastName, setLastName]   = useState(user?.lastName || '');
  const [profileLoading, setProfileLoading] = useState(false);
  const [profileMsg, setProfileMsg] = useState('');
  const [profileErr, setProfileErr] = useState('');

  // Password
  const [curPwd, setCurPwd]     = useState('');
  const [newPwd, setNewPwd]     = useState('');
  const [confirmPwd, setConfirm] = useState('');
  const [showCur, setShowCur]   = useState(false);
  const [showNew, setShowNew]   = useState(false);
  const [pwdLoading, setPwdLoading] = useState(false);
  const [pwdMsg, setPwdMsg]     = useState('');
  const [pwdErr, setPwdErr]     = useState('');

  // Notifications (local prefs)
  const [notifTxn, setNotifTxn]     = useState(() => localStorage.getItem('st-notif-txn') !== 'false');
  const [notifFraud, setNotifFraud] = useState(() => localStorage.getItem('st-notif-fraud') !== 'false');

  useEffect(() => { localStorage.setItem('st-notif-txn', notifTxn); }, [notifTxn]);
  useEffect(() => { localStorage.setItem('st-notif-fraud', notifFraud); }, [notifFraud]);

  const handleProfileSave = async () => {
    setProfileLoading(true); setProfileMsg(''); setProfileErr('');
    try {
      const res = await profileApi.update({ firstName, lastName });
      setProfileMsg('Profile updated');
      onProfileUpdate?.({ firstName: res.firstName, lastName: res.lastName });
    } catch (e) { setProfileErr(e.message || 'Failed to update profile.'); }
    finally { setProfileLoading(false); }
  };

  const handlePasswordChange = async () => {
    setPwdMsg(''); setPwdErr('');
    if (newPwd.length < 8) { setPwdErr('Password must be at least 8 characters.'); return; }
    if (newPwd !== confirmPwd) { setPwdErr('Passwords do not match.'); return; }
    setPwdLoading(true);
    try {
      await profileApi.changePassword({ currentPassword: curPwd, newPassword: newPwd });
      setPwdMsg('Password changed successfully');
      setCurPwd(''); setNewPwd(''); setConfirm('');
    } catch (e) { setPwdErr(e.message || 'Failed to change password.'); }
    finally { setPwdLoading(false); }
  };

  const inputStyle = {
    width: '100%', padding: '11px 14px',
    background: 'var(--bg-secondary)', border: '1px solid var(--border-medium)',
    borderRadius: 'var(--radius-md)', color: 'var(--text-primary)',
    fontSize: 13, fontFamily: 'var(--font-body)', outline: 'none',
    boxSizing: 'border-box', transition: 'border-color 0.2s',
  };

  const labelStyle = {
    display: 'block', fontSize: 11, fontWeight: 600, color: 'var(--text-muted)',
    textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6, fontFamily: 'var(--font-body)',
  };

  const cardStyle = {
    background: 'var(--bg-secondary)', border: '1px solid var(--border-light)',
    borderRadius: 'var(--radius-xl)', padding: '24px', marginBottom: 20,
  };

  const sectionTitle = (icon, label) => (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 20 }}>
      <div style={{ width: 34, height: 34, borderRadius: 10, background: 'var(--accent-light)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        {icon}
      </div>
      <h3 style={{ fontSize: 15, fontWeight: 700, color: 'var(--text-primary)', fontFamily: 'var(--font-display)' }}>{label}</h3>
    </div>
  );

  const StatusMsg = ({ msg, err }) => (
    <>
      {msg && <p style={{ fontSize: 12, color: 'var(--success)', marginTop: 10, fontFamily: 'var(--font-body)', display: 'flex', alignItems: 'center', gap: 6 }}><Check size={13} />{msg}</p>}
      {err && <p style={{ fontSize: 12, color: 'var(--danger)', marginTop: 10, fontFamily: 'var(--font-body)' }}>{err}</p>}
    </>
  );

  const ToggleSwitch = ({ checked, onChange }) => (
    <button
      type="button" onClick={() => onChange(!checked)}
      style={{
        width: 44, height: 24, borderRadius: 12, padding: 2,
        background: checked ? 'var(--accent)' : 'var(--border-medium)',
        border: 'none', cursor: 'pointer', transition: 'background 0.2s',
        display: 'flex', alignItems: 'center',
        justifyContent: checked ? 'flex-end' : 'flex-start',
      }}
    >
      <motion.div
        layout
        style={{ width: 20, height: 20, borderRadius: '50%', background: '#fff', boxShadow: '0 1px 3px rgba(0,0,0,0.2)' }}
        transition={{ type: 'spring', stiffness: 500, damping: 30 }}
      />
    </button>
  );

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} style={{ maxWidth: 560 }}>
      <h2 style={{ fontSize: 20, fontWeight: 700, color: 'var(--text-primary)', fontFamily: 'var(--font-display)', marginBottom: 24 }}>
        Settings
      </h2>

      {/* ─── Profile ─── */}
      <div style={cardStyle}>
        {sectionTitle(<User size={16} color="var(--accent)" />, 'Profile')}
        <div style={{ marginBottom: 14 }}>
          <label style={labelStyle}>Email</label>
          <input type="text" value={user?.email || ''} disabled style={{ ...inputStyle, opacity: 0.6, cursor: 'not-allowed' }} />
        </div>
        <div style={{ display: 'flex', gap: 12, marginBottom: 14 }}>
          <div style={{ flex: 1 }}>
            <label style={labelStyle}>First Name</label>
            <input type="text" value={firstName} onChange={(e) => setFirstName(e.target.value)} style={inputStyle}
              onFocus={(e) => e.target.style.borderColor = 'var(--accent)'} onBlur={(e) => e.target.style.borderColor = 'var(--border-medium)'} />
          </div>
          <div style={{ flex: 1 }}>
            <label style={labelStyle}>Last Name</label>
            <input type="text" value={lastName} onChange={(e) => setLastName(e.target.value)} style={inputStyle}
              onFocus={(e) => e.target.style.borderColor = 'var(--accent)'} onBlur={(e) => e.target.style.borderColor = 'var(--border-medium)'} />
          </div>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <StatusMsg msg={profileMsg} err={profileErr} />
          <motion.button
            whileHover={{ scale: 1.03 }} whileTap={{ scale: 0.97 }}
            onClick={handleProfileSave} disabled={profileLoading}
            style={{
              padding: '9px 20px', borderRadius: 'var(--radius-full)',
              background: 'linear-gradient(135deg, var(--accent), var(--accent-hover))',
              border: 'none', color: '#fff', fontSize: 13, fontWeight: 600,
              fontFamily: 'var(--font-body)', cursor: profileLoading ? 'not-allowed' : 'pointer',
              display: 'flex', alignItems: 'center', gap: 6, opacity: profileLoading ? 0.7 : 1,
            }}
          >
            <Save size={13} /> {profileLoading ? 'Saving...' : 'Save'}
          </motion.button>
        </div>
      </div>

      {/* ─── Change Password ─── */}
      <div style={cardStyle}>
        {sectionTitle(<Lock size={16} color="var(--accent)" />, 'Change Password')}
        <div style={{ marginBottom: 14 }}>
          <label style={labelStyle}>Current Password</label>
          <div style={{ position: 'relative' }}>
            <input type={showCur ? 'text' : 'password'} value={curPwd} onChange={(e) => setCurPwd(e.target.value)}
              placeholder="Enter current password" style={{ ...inputStyle, paddingRight: 42 }}
              onFocus={(e) => e.target.style.borderColor = 'var(--accent)'} onBlur={(e) => e.target.style.borderColor = 'var(--border-medium)'} />
            <button type="button" onClick={() => setShowCur(!showCur)} style={{ position: 'absolute', right: 12, top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', cursor: 'pointer', color: 'var(--text-muted)', display: 'flex' }}>
              {showCur ? <EyeOff size={15} /> : <Eye size={15} />}
            </button>
          </div>
        </div>
        <div style={{ display: 'flex', gap: 12, marginBottom: 14 }}>
          <div style={{ flex: 1 }}>
            <label style={labelStyle}>New Password</label>
            <div style={{ position: 'relative' }}>
              <input type={showNew ? 'text' : 'password'} value={newPwd} onChange={(e) => setNewPwd(e.target.value)}
                placeholder="Min 8 characters" style={{ ...inputStyle, paddingRight: 42 }}
                onFocus={(e) => e.target.style.borderColor = 'var(--accent)'} onBlur={(e) => e.target.style.borderColor = 'var(--border-medium)'} />
              <button type="button" onClick={() => setShowNew(!showNew)} style={{ position: 'absolute', right: 12, top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', cursor: 'pointer', color: 'var(--text-muted)', display: 'flex' }}>
                {showNew ? <EyeOff size={15} /> : <Eye size={15} />}
              </button>
            </div>
          </div>
          <div style={{ flex: 1 }}>
            <label style={labelStyle}>Confirm Password</label>
            <input type="password" value={confirmPwd} onChange={(e) => setConfirm(e.target.value)}
              placeholder="Re-enter new password" style={inputStyle}
              onFocus={(e) => e.target.style.borderColor = 'var(--accent)'} onBlur={(e) => e.target.style.borderColor = 'var(--border-medium)'} />
          </div>
        </div>
        {newPwd && confirmPwd && newPwd !== confirmPwd && (
          <p style={{ fontSize: 11, color: 'var(--danger)', marginBottom: 10, fontFamily: 'var(--font-body)' }}>Passwords do not match</p>
        )}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <StatusMsg msg={pwdMsg} err={pwdErr} />
          <motion.button
            whileHover={{ scale: 1.03 }} whileTap={{ scale: 0.97 }}
            onClick={handlePasswordChange} disabled={pwdLoading || !curPwd || !newPwd || !confirmPwd}
            style={{
              padding: '9px 20px', borderRadius: 'var(--radius-full)',
              background: 'linear-gradient(135deg, var(--accent), var(--accent-hover))',
              border: 'none', color: '#fff', fontSize: 13, fontWeight: 600,
              fontFamily: 'var(--font-body)', cursor: (pwdLoading || !curPwd || !newPwd || !confirmPwd) ? 'not-allowed' : 'pointer',
              display: 'flex', alignItems: 'center', gap: 6,
              opacity: (pwdLoading || !curPwd || !newPwd || !confirmPwd) ? 0.5 : 1,
            }}
          >
            <Lock size={13} /> {pwdLoading ? 'Changing...' : 'Change Password'}
          </motion.button>
        </div>
      </div>

      {/* ─── Appearance ─── */}
      <div style={cardStyle}>
        {sectionTitle(theme === 'dark' ? <Moon size={16} color="var(--accent)" /> : <Sun size={16} color="var(--accent)" />, 'Appearance')}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div>
            <p style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-primary)', fontFamily: 'var(--font-body)', marginBottom: 2 }}>Theme</p>
            <p style={{ fontSize: 12, color: 'var(--text-muted)', fontFamily: 'var(--font-body)' }}>
              Currently using <strong>{theme}</strong> mode
            </p>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <Sun size={14} color={theme === 'light' ? 'var(--accent)' : 'var(--text-muted)'} />
            <ToggleSwitch checked={theme === 'dark'} onChange={toggleTheme} />
            <Moon size={14} color={theme === 'dark' ? 'var(--accent)' : 'var(--text-muted)'} />
          </div>
        </div>
      </div>

      {/* ─── Notifications ─── */}
      <div style={cardStyle}>
        {sectionTitle(<Bell size={16} color="var(--accent)" />, 'Notifications')}
        {[
          { label: 'Transaction Alerts', desc: 'Get notified for deposits, withdrawals, and transfers', checked: notifTxn, onChange: (v) => { if (v && typeof Notification !== 'undefined' && Notification.permission === 'default') Notification.requestPermission(); setNotifTxn(v); } },
          { label: 'Fraud Alerts', desc: 'Get notified when a transaction is flagged by the risk engine', checked: notifFraud, onChange: (v) => { if (v && typeof Notification !== 'undefined' && Notification.permission === 'default') Notification.requestPermission(); setNotifFraud(v); } },
        ].map(({ label, desc, checked, onChange }) => (
          <div key={label} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '12px 0', borderBottom: '1px solid var(--border-light)' }}>
            <div>
              <p style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-primary)', fontFamily: 'var(--font-body)', marginBottom: 2 }}>{label}</p>
              <p style={{ fontSize: 12, color: 'var(--text-muted)', fontFamily: 'var(--font-body)' }}>{desc}</p>
            </div>
            <ToggleSwitch checked={checked} onChange={onChange} />
          </div>
        ))}
      </div>

      {/* ─── Account Info ─── */}
      <div style={{ ...cardStyle, background: 'var(--bg-tertiary)', borderColor: 'var(--border-light)' }}>
        <p style={{ fontSize: 11, color: 'var(--text-muted)', fontFamily: 'var(--font-body)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 8, fontWeight: 600 }}>Account</p>
        <p style={{ fontSize: 13, color: 'var(--text-secondary)', fontFamily: 'var(--font-body)' }}>
          Role: <strong style={{ color: 'var(--text-primary)' }}>{user?.role}</strong>
        </p>
      </div>
    </motion.div>
  );
}

/* ── main page ───────────────────────────────────── */
export default function DashboardPage() {
  const { user, isAuthenticated, loading: authLoading, logout, login } = useAuth();
  const showToast = useToast();

  const [accts, setAccts]         = useState([]);
  const [txns, setTxns]           = useState([]);
  const [txPage, setTxPage]       = useState(0);
  const [txTotalPages, setTxTotal] = useState(1);
  const [dataLoading, setDL]      = useState(true);
  const [txLoading, setTxLoading] = useState(false);
  const [error, setError]         = useState('');

  const [showCreate, setShowCreate]   = useState(false);
  const [showTxn, setShowTxn]         = useState(false);
  const [txnType, setTxnType]         = useState('DEPOSIT');
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [activeTab, setActiveTab]     = useState('dashboard');

  const [stmtAccount, setStmtAccount]   = useState(null);
  const [stmtStart, setStmtStart]       = useState('');
  const [stmtEnd, setStmtEnd]           = useState('');
  const [stmtTxns, setStmtTxns]         = useState([]);
  const [stmtLoading, setStmtLoading]   = useState(false);
  const [stmtError, setStmtError]       = useState('');

  const openStatement = async (account) => {
    const now = new Date();
    const firstOfMonth = new Date(now.getFullYear(), now.getMonth(), 1).toISOString().slice(0, 16);
    const nowStr = now.toISOString().slice(0, 16);
    setStmtAccount(account);
    setStmtStart(firstOfMonth);
    setStmtEnd(nowStr);
    setStmtTxns([]);
    setStmtError('');
    // Auto-fetch for this month
    setStmtLoading(true);
    try {
      const fmtDate = (d) => d.length === 16 ? d + ':00' : d;
      const data = await accountsApi.getStatement(account.id, fmtDate(firstOfMonth), fmtDate(nowStr));
      setStmtTxns(Array.isArray(data) ? data : []);
    } catch (err) {
      setStmtError(err.message || 'Failed to load statement.');
    } finally {
      setStmtLoading(false);
    }
  };

  const fetchStatement = useCallback(async () => {
    if (!stmtAccount || !stmtStart || !stmtEnd) return;
    setStmtLoading(true);
    setStmtError('');
    try {
      // Ensure ISO format: 2026-03-17T10:30:00
      const fmtDate = (d) => d.length === 16 ? d + ':00' : d;
      const data = await accountsApi.getStatement(stmtAccount.id, fmtDate(stmtStart), fmtDate(stmtEnd));
      setStmtTxns(Array.isArray(data) ? data : []);
    } catch (err) {
      setStmtError(err.message || 'Failed to load statement.');
    } finally {
      setStmtLoading(false);
    }
  }, [stmtAccount, stmtStart, stmtEnd]);

  const loadTxPage = useCallback(async (page = 0) => {
    setTxLoading(true);
    try {
      const t = await txnApi.getHistory(page, 10);
      const txnArr = Array.isArray(t) ? t : (t?.content ?? []);
      setTxns(txnArr);
      setTxTotal(t?.totalPages ?? 1);
      setTxPage(page);
    } catch (err) {
      setError(err.message || 'Failed to load transactions.');
    } finally {
      setTxLoading(false);
    }
  }, []);

  const load = useCallback(async () => {
    setDL(true);
    setError('');
    try {
      const [a] = await Promise.all([
        accountsApi.getAll(),
        loadTxPage(0),
      ]);
      setAccts(Array.isArray(a) ? a : []);
    } catch (err) {
      setError(err.message || 'Failed to load data.');
    } finally {
      setDL(false);
    }
  }, [loadTxPage]);

  useEffect(() => { load(); }, [load]);

  if (authLoading) return null;
  if (!isAuthenticated) return <Navigate to="/login" replace />;

  const openTxn = (type) => { setTxnType(type); setShowTxn(true); };

  const sendNotification = useCallback((title, body, type = 'info') => {
    // In-app toast
    showToast(body, type);
    // Browser notification if permission granted
    if (Notification.permission === 'granted') {
      new Notification(title, { body, icon: '/favicon.ico' });
    }
  }, [showToast]);

  const handleTxnSuccess = useCallback((txn) => {
    load();
    const notifTxn = localStorage.getItem('st-notif-txn') !== 'false';
    const notifFraud = localStorage.getItem('st-notif-fraud') !== 'false';

    if (txn?.status === 'FLAGGED' && notifFraud) {
      sendNotification('Transaction Flagged', `Your ${txn.type?.toLowerCase()} of $${txn.amount?.toLocaleString()} was flagged for review.`, 'warning');
    } else if (notifTxn) {
      const label = txn?.type === 'DEPOSIT' ? 'Deposit' : txn?.type === 'WITHDRAWAL' ? 'Withdrawal' : 'Transfer';
      sendNotification('Transaction Complete', `${label} of $${txn?.amount?.toLocaleString()} completed successfully.`, 'success');
    }
  }, [load, sendNotification]);

  return (
    <div style={{ display: 'flex', minHeight: '100vh', background: 'var(--bg-primary)', fontFamily: 'var(--font-body)' }}>
      {/* Sidebar */}
      <Sidebar
        user={user}
        onLogout={logout}
        activeTab={activeTab}
        onTabChange={setActiveTab}
        mobileOpen={sidebarOpen}
        onCloseMobile={() => setSidebarOpen(false)}
      />

      {/* Main content */}
      <div id="main-content" style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
        {/* Top bar */}
        <header style={{
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          padding: '12px 20px',
          borderBottom: '1px solid var(--border-light)',
          background: 'var(--bg-primary)',
          position: 'sticky', top: 0, zIndex: 50,
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
            {/* Mobile hamburger */}
            <button
              className="sidebar-toggle"
              onClick={() => setSidebarOpen(true)}
              aria-label="Open navigation menu"
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
              onClick={load}
              title="Refresh"
              aria-label="Refresh dashboard data"
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
              onClick={() => setActiveTab('settings')}
              title="Notifications"
              style={{
                position: 'relative',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                width: 36, height: 36, borderRadius: 'var(--radius-md)',
                background: 'var(--bg-secondary)', border: '1px solid var(--border-light)',
                color: 'var(--text-muted)', cursor: 'pointer', transition: 'color 0.15s',
              }}
              onMouseEnter={(e) => e.currentTarget.style.color = 'var(--accent)'}
              onMouseLeave={(e) => e.currentTarget.style.color = 'var(--text-muted)'}
            >
              <Bell size={15} />
              <span style={{
                position: 'absolute', top: 7, right: 7,
                width: 7, height: 7, borderRadius: '50%',
                background: 'var(--accent)',
                border: '1.5px solid var(--bg-primary)',
              }} />
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
                <button onClick={load} style={{ fontSize: 12, fontWeight: 600, color: 'var(--danger)', background: 'none', border: 'none', cursor: 'pointer', textDecoration: 'underline' }}>
                  Retry
                </button>
              </motion.div>
            )}
          </AnimatePresence>

          {/* ═══ DASHBOARD TAB ═══ */}
          {activeTab === 'dashboard' && (
            <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
              {/* Balance overview */}
              {!dataLoading && <BalanceOverview accounts={accts} />}
              {dataLoading && (
                <div style={{ height: 120, borderRadius: 'var(--radius-xl)', background: 'var(--bg-secondary)', animation: 'shimmer 1.4s ease-in-out infinite' }} />
              )}

              {/* Account cards */}
              <section>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
                  <h2 style={{ fontSize: 15, fontWeight: 700, color: 'var(--text-primary)', fontFamily: 'var(--font-display)' }}>
                    Your Accounts
                  </h2>
                  <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>{accts.length} account{accts.length !== 1 ? 's' : ''}</span>
                </div>
                <div style={{
                  display: 'flex', gap: 16, overflowX: 'auto', paddingBottom: 4,
                  scrollbarWidth: 'none',
                }}>
                  {dataLoading
                    ? Array.from({ length: 3 }).map((_, i) => (
                        <div key={i} style={{ minWidth: 240, height: 180, borderRadius: 'var(--radius-xl)', background: 'var(--bg-secondary)', flexShrink: 0, animation: 'shimmer 1.4s ease-in-out infinite' }} />
                      ))
                    : accts.map((a, i) => (
                        <motion.div
                          key={a.id}
                          initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }}
                          transition={{ delay: i * 0.07, duration: 0.35 }}
                          style={{ flexShrink: 0 }}
                        >
                          <AccountCard account={a} onStatement={() => openStatement(a)} />
                        </motion.div>
                      ))}
                  <div style={{ flexShrink: 0 }}>
                    <AddAccountCard onClick={() => setShowCreate(true)} />
                  </div>
                </div>
              </section>

              {/* Quick actions */}
              <section>
                <h2 style={{ fontSize: 15, fontWeight: 700, color: 'var(--text-primary)', fontFamily: 'var(--font-display)', marginBottom: 14 }}>
                  Quick Actions
                </h2>
                <div style={{ display: 'flex', gap: 12 }} className="quick-actions">
                  {[
                    { label: 'Deposit',  icon: ArrowDownLeft,  type: 'DEPOSIT',    color: 'var(--success)' },
                    { label: 'Withdraw', icon: ArrowUpRight,   type: 'WITHDRAWAL', color: 'var(--danger)'  },
                    { label: 'Transfer', icon: ArrowLeftRight, type: 'TRANSFER',   color: 'var(--info)'    },
                  ].map(({ label, icon: Icon, type, color }) => (
                    <motion.button
                      key={type}
                      onClick={() => openTxn(type)}
                      whileHover={{ scale: 1.03 }}
                      whileTap={{ scale: 0.97 }}
                      style={{
                        flex: 1, padding: '16px 12px',
                        borderRadius: 'var(--radius-lg)',
                        background: 'var(--bg-secondary)',
                        border: '1px solid var(--border-light)',
                        cursor: 'pointer',
                        display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8,
                        transition: 'border-color 0.2s',
                      }}
                      onMouseEnter={(e) => { e.currentTarget.style.borderColor = color; }}
                      onMouseLeave={(e) => { e.currentTarget.style.borderColor = 'var(--border-light)'; }}
                    >
                      <div style={{
                        width: 40, height: 40, borderRadius: 12,
                        background: `${color}18`,
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                      }}>
                        <Icon size={18} color={color} strokeWidth={2.5} />
                      </div>
                      <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-primary)', fontFamily: 'var(--font-body)' }}>
                        {label}
                      </span>
                    </motion.button>
                  ))}
                </div>
              </section>

              {/* Recent transactions */}
              <section>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
                  <h2 style={{ fontSize: 15, fontWeight: 700, color: 'var(--text-primary)', fontFamily: 'var(--font-display)' }}>
                    Recent Transactions
                  </h2>
                  <button
                    onClick={() => setActiveTab('transactions')}
                    style={{ fontSize: 13, fontWeight: 600, color: 'var(--accent)', background: 'none', border: 'none', cursor: 'pointer', fontFamily: 'var(--font-body)' }}
                  >
                    View All →
                  </button>
                </div>
                <div style={{
                  background: 'var(--bg-secondary)',
                  border: '1px solid var(--border-light)',
                  borderRadius: 'var(--radius-xl)',
                  overflow: 'hidden',
                }}>
                  <TransactionTable
                    transactions={txns}
                    loading={dataLoading}
                    userAccountIds={accts.map(a => a.id)}
                  />
                </div>
              </section>
            </motion.div>
          )}

          {/* ═══ ACCOUNTS TAB ═══ */}
          {activeTab === 'accounts' && (
            <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <h2 style={{ fontSize: 20, fontWeight: 700, color: 'var(--text-primary)', fontFamily: 'var(--font-display)' }}>
                  All Accounts
                </h2>
                <motion.button
                  whileHover={{ scale: 1.03 }} whileTap={{ scale: 0.97 }}
                  onClick={() => setShowCreate(true)}
                  style={{
                    padding: '8px 18px', borderRadius: 'var(--radius-full)',
                    background: 'linear-gradient(135deg, var(--accent), var(--accent-hover))',
                    border: 'none', color: '#fff', fontSize: 13, fontWeight: 600,
                    fontFamily: 'var(--font-body)', cursor: 'pointer',
                    boxShadow: 'var(--shadow-accent)',
                  }}
                >
                  + New Account
                </motion.button>
              </div>
              <BalanceOverview accounts={accts} />
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(250px, 1fr))', gap: 16 }}>
                {accts.map((a, i) => (
                  <motion.div key={a.id} initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.07 }}>
                    <AccountCard account={a} onStatement={() => openStatement(a)} />
                  </motion.div>
                ))}
                <AddAccountCard onClick={() => setShowCreate(true)} />
              </div>
            </motion.div>
          )}

          {/* ═══ TRANSACTIONS TAB ═══ */}
          {activeTab === 'transactions' && (
            <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <h2 style={{ fontSize: 20, fontWeight: 700, color: 'var(--text-primary)', fontFamily: 'var(--font-display)' }}>
                  Transaction History
                </h2>
                <motion.button
                  whileHover={{ scale: 1.03 }} whileTap={{ scale: 0.97 }}
                  onClick={() => setShowTxn(true)}
                  style={{
                    padding: '8px 18px', borderRadius: 'var(--radius-full)',
                    background: 'linear-gradient(135deg, var(--accent), var(--accent-hover))',
                    border: 'none', color: '#fff', fontSize: 13, fontWeight: 600,
                    fontFamily: 'var(--font-body)', cursor: 'pointer',
                    boxShadow: 'var(--shadow-accent)',
                  }}
                >
                  + New Transaction
                </motion.button>
              </div>
              <div style={{
                background: 'var(--bg-secondary)',
                border: '1px solid var(--border-light)',
                borderRadius: 'var(--radius-xl)',
                overflow: 'hidden',
              }}>
                <TransactionTable
                  transactions={txns}
                  loading={txLoading}
                  showPagination
                  page={txPage}
                  totalPages={txTotalPages}
                  onPageChange={loadTxPage}
                  userAccountIds={accts.map(a => a.id)}
                />
              </div>
            </motion.div>
          )}

          {/* ═══ SETTINGS TAB ═══ */}
          {activeTab === 'settings' && <SettingsPanel user={user} onProfileUpdate={(u) => login({ ...user, ...u })} />}
        </main>
      </div>

      {/* Modals */}
      <CreateAccountModal
        isOpen={showCreate}
        onClose={() => setShowCreate(false)}
        onSuccess={() => { load(); }}
      />
      <NewTransactionModal
        isOpen={showTxn}
        onClose={() => setShowTxn(false)}
        accounts={accts}
        initialType={txnType}
        onSuccess={handleTxnSuccess}
      />

      {/* Statement Modal */}
      <AnimatePresence>
        {stmtAccount && (
          <motion.div
            initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            style={{ position: 'fixed', inset: 0, zIndex: 200, background: 'rgba(0,0,0,0.6)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16 }}
            onClick={() => setStmtAccount(null)}
          >
            <motion.div
              initial={{ scale: 0.95, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} exit={{ scale: 0.95, opacity: 0 }}
              onClick={(e) => e.stopPropagation()}
              style={{ background: 'var(--bg-primary)', border: '1px solid var(--border-light)', borderRadius: 'var(--radius-xl)', width: '100%', maxWidth: 620, maxHeight: '85vh', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}
            >
              {/* Header */}
              <div style={{ padding: '20px 24px', borderBottom: '1px solid var(--border-light)', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <FileText size={18} color="var(--accent)" />
                  <div>
                    <p style={{ fontSize: 15, fontWeight: 700, color: 'var(--text-primary)', fontFamily: 'var(--font-display)' }}>Account Statement</p>
                    <p style={{ fontSize: 11, color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>{stmtAccount.accountNumber}</p>
                  </div>
                </div>
                <button onClick={() => setStmtAccount(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--text-muted)', display: 'flex' }}>
                  <X size={18} />
                </button>
              </div>

              {/* Date range + fetch */}
              <div style={{ padding: '16px 24px', borderBottom: '1px solid var(--border-light)', display: 'flex', gap: 12, alignItems: 'flex-end', flexWrap: 'wrap' }}>
                {[['From', stmtStart, setStmtStart], ['To', stmtEnd, setStmtEnd]].map(([label, val, setter]) => (
                  <div key={label} style={{ flex: 1, minWidth: 140 }}>
                    <label style={{ display: 'block', fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6, fontFamily: 'var(--font-body)' }}>{label}</label>
                    <input
                      type="datetime-local" value={val} onChange={(e) => setter(e.target.value)}
                      style={{ width: '100%', padding: '9px 12px', background: 'var(--bg-secondary)', border: '1px solid var(--border-medium)', borderRadius: 'var(--radius-md)', color: 'var(--text-primary)', fontSize: 12, fontFamily: 'var(--font-body)', outline: 'none', boxSizing: 'border-box' }}
                    />
                  </div>
                ))}
                <motion.button
                  whileHover={{ scale: 1.03 }} whileTap={{ scale: 0.97 }}
                  onClick={fetchStatement} disabled={stmtLoading}
                  style={{ padding: '9px 20px', borderRadius: 'var(--radius-md)', background: 'var(--accent)', border: 'none', color: '#fff', fontSize: 13, fontWeight: 600, fontFamily: 'var(--font-body)', cursor: stmtLoading ? 'not-allowed' : 'pointer', opacity: stmtLoading ? 0.7 : 1, whiteSpace: 'nowrap' }}
                >
                  {stmtLoading ? 'Loading…' : 'Fetch'}
                </motion.button>
              </div>

              {/* Results */}
              <div style={{ flex: 1, overflowY: 'auto', padding: '8px 0' }}>
                {stmtError && (
                  <p style={{ padding: '16px 24px', color: 'var(--danger)', fontSize: 13, fontFamily: 'var(--font-body)' }}>{stmtError}</p>
                )}
                {!stmtError && stmtTxns.length === 0 && !stmtLoading && (
                  <p style={{ padding: '32px 24px', textAlign: 'center', color: 'var(--text-muted)', fontSize: 13, fontFamily: 'var(--font-body)' }}>No transactions in this period.</p>
                )}
                {stmtTxns.length > 0 && (
                  <table style={{ width: '100%', borderCollapse: 'collapse', fontFamily: 'var(--font-body)' }}>
                    <thead>
                      <tr style={{ borderBottom: '1px solid var(--border-light)' }}>
                        {['Date', 'Type', 'Description', 'Amount', 'Status'].map((h) => (
                          <th key={h} style={{ padding: '8px 16px', textAlign: 'left', fontSize: 10, fontWeight: 600, letterSpacing: '0.06em', textTransform: 'uppercase', color: 'var(--text-muted)' }}>{h}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {stmtTxns.map((t) => {
                        const isPos = t.type === 'DEPOSIT';
                        return (
                          <tr key={t.id} style={{ borderBottom: '1px solid var(--border-light)' }}>
                            <td style={{ padding: '10px 16px', fontSize: 11, color: 'var(--text-secondary)', whiteSpace: 'nowrap' }}>
                              {new Date(t.createdAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}
                            </td>
                            <td style={{ padding: '10px 16px', fontSize: 11, fontWeight: 600, color: 'var(--text-primary)' }}>{t.type}</td>
                            <td style={{ padding: '10px 16px', fontSize: 11, color: 'var(--text-secondary)', maxWidth: 160, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{t.description || '—'}</td>
                            <td style={{ padding: '10px 16px', fontSize: 12, fontWeight: 700, color: isPos ? 'var(--success)' : 'var(--danger)', fontFamily: 'var(--font-mono)', whiteSpace: 'nowrap' }}>
                              {isPos ? '+' : '-'}${Math.abs(t.amount).toLocaleString('en-US', { minimumFractionDigits: 2 })}
                            </td>
                            <td style={{ padding: '10px 16px' }}><StatusBadge status={t.status} /></td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                )}
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      <style>{`
        @keyframes shimmer { 0%,100%{opacity:0.5} 50%{opacity:1} }
        @media (max-width: 768px) {
          .sidebar { position: fixed !important; left: 0; top: 0; transform: translateX(-100%); }
          .sidebar-toggle { display: flex !important; }
          .quick-actions { flex-direction: column !important; }
          .mobile-hide { display: none !important; }
          .mobile-stack { flex-direction: column !important; }
          .mobile-full { width: 100% !important; }
        }
      `}</style>
    </div>
  );
}
