const BASE_URL = import.meta.env.VITE_API_URL || '';

// Called when any API request returns 401 — registered by AuthContext
let _onUnauthorized = null;
export const setUnauthorizedHandler = (fn) => { _onUnauthorized = fn; };

let _csrfToken = null;
let _csrfPromise = null;

// Forces the CSRF token into the repository AND captures the token for JS use.
// The backend is a different origin than this app (cross-site), so the XSRF-TOKEN
// cookie is not visible to document.cookie here; the token must come from the
// response body. Send it as the X-XSRF-TOKEN header on all mutations.
export function fetchCsrfToken() {
  if (!_csrfPromise) {
    _csrfPromise = fetch(`${BASE_URL}/api/csrf`, { credentials: 'include' })
      .then(async (res) => {
        const body = await res.json();
        _csrfToken = body?.token || null;
      })
      .catch(() => {
        _csrfToken = null;
      })
      .finally(() => {
        _csrfPromise = null;
      });
  }
  return _csrfPromise;
}

async function ensureCsrfToken() {
  // The backend issues a fresh token per /api/csrf call and clears the
  // XSRF-TOKEN cookie after a mutation, so a cached header can drift out of
  // sync with the cookie and every state-changing call 403s. Always fetch a
  // fresh token so the header matches the cookie set by the same response.
  await fetchCsrfToken();
}

async function apiCall(endpoint, options = {}) {
  const method = (options.method || 'GET').toUpperCase();
  const headers = { 'Content-Type': 'application/json' };

  // CSRF token header on all state-changing requests
  const isMutating = !['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method);
  if (isMutating) {
    await ensureCsrfToken();
    if (_csrfToken) headers['X-XSRF-TOKEN'] = _csrfToken;
  }

  const res = await fetch(`${BASE_URL}${endpoint}`, {
    ...options,
    credentials: 'include',
    headers: { ...headers, ...options.headers },
  });

  // No-content responses
  if (res.status === 204) return null;

  let body;
  const contentType = res.headers.get('content-type');
  if (contentType && contentType.includes('application/json')) {
    body = await res.json();
  } else {
    body = await res.text();
  }

  if (!res.ok) {
    const message =
      typeof body === 'object' ? body.message || body.error || JSON.stringify(body) : body;
    const err = new Error(message || `Request failed (${res.status})`);
    err.status = res.status;
    err.body = body;
    if (res.status === 401) _onUnauthorized?.();
    throw err;
  }

  return body;
}

// ─── Auth (public, no token) ───────────────────────────
export const auth = {
  register: ({ firstName, lastName, email, password }) =>
    apiCall('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({ firstName, lastName, email, password }),
    }),

  login: ({ email, password }) =>
    apiCall('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),

  logout: () =>
    apiCall('/api/auth/logout', {
      method: 'POST',
    }),
};

// ─── Accounts ─────────────────────────────────────────
export const accounts = {
  create: ({ accountType, initialDeposit }) =>
    apiCall('/api/accounts', {
      method: 'POST',
      body: JSON.stringify({ accountType, initialDeposit }),
    }),

  getAll: () =>
    apiCall('/api/accounts'),

  getById: (id) =>
    apiCall(`/api/accounts/${id}`),

  lookupByAccountNumber: (accountNumber) =>
    apiCall(`/api/accounts/lookup?accountNumber=${encodeURIComponent(accountNumber)}`),

  getStatement: (id, start, end) => {
    const params = new URLSearchParams();
    if (start) params.append('start', start);
    if (end) params.append('end', end);
    return apiCall(`/api/accounts/${id}/statement?${params}`);
  },
};

// ─── Transactions ─────────────────────────────────────
export const transactions = {
  create: ({ type, amount, fromAccountId, toAccountId, description }) =>
    apiCall('/api/transactions', {
      method: 'POST',
      body: JSON.stringify({ type, amount, fromAccountId, toAccountId, description }),
    }),

  getById: (id) =>
    apiCall(`/api/transactions/${id}`),

  getHistory: (page = 0, size = 20) => {
    const params = new URLSearchParams({ page, size });
    return apiCall(`/api/transactions/history?${params}`);
  },
};

// ─── User Profile ─────────────────────────────────
export const userProfile = {
  get: () =>
    apiCall('/api/user/profile'),

  update: ({ firstName, lastName }) =>
    apiCall('/api/user/profile', {
      method: 'PUT',
      body: JSON.stringify({ firstName, lastName }),
    }),

  changePassword: ({ currentPassword, newPassword }) =>
    apiCall('/api/user/change-password', {
      method: 'PUT',
      body: JSON.stringify({ currentPassword, newPassword }),
    }),
};

// ─── Admin ──────────────────────────────────────
export const admin = {
  getDashboard: () =>
    apiCall('/api/admin/dashboard'),

  getFlagged: (page = 0, size = 20) => {
    const params = new URLSearchParams({ page, size });
    return apiCall(`/api/admin/fraud/flagged?${params}`);
  },

  reviewTransaction: (id, decision) =>
    apiCall(`/api/admin/fraud/${id}/review`, {
      method: 'PUT',
      body: JSON.stringify({ decision }),
    }),

  getAllAccounts: (page = 0, size = 20) => {
    const params = new URLSearchParams({ page, size });
    return apiCall(`/api/admin/accounts?${params}`);
  },
};

// ─── Risk Cases ─────────────────────────────────
export const riskCases = {
  list: (page = 0, size = 20) => {
    const params = new URLSearchParams({ page, size });
    return apiCall(`/api/v1/admin/risk-cases?${params}`);
  },

  getById: (id) =>
    apiCall(`/api/v1/admin/risk-cases/${id}`),

  assign: (id) =>
    apiCall(`/api/v1/admin/risk-cases/${id}/assign`, { method: 'PATCH' }),

  decide: (id, { decision, reviewNotes }) =>
    apiCall(`/api/v1/admin/risk-cases/${id}/decision`, {
      method: 'PATCH',
      body: JSON.stringify({ decision, reviewNotes }),
    }),
};

// ─── Audit Events ───────────────────────────────
export const auditEvents = {
  list: (page = 0, size = 50) => {
    const params = new URLSearchParams({ page, size });
    return apiCall(`/api/v1/audit-events?${params}`);
  },

  listByAction: (action, page = 0, size = 50) => {
    const params = new URLSearchParams({ page, size });
    return apiCall(`/api/v1/audit-events/by-action/${action}?${params}`);
  },

  listForResource: (resourceType, resourceId, page = 0, size = 50) => {
    const params = new URLSearchParams({ page, size });
    return apiCall(`/api/v1/audit-events/by-resource/${resourceType}/${resourceId}?${params}`);
  },
};

// ─── Live Demo Mode (admin) ─────────────────────
export const demo = {
  status: () =>
    apiCall('/api/v1/demo/health'),

  triggerRapidTransfer: () =>
    apiCall('/api/v1/demo/trigger/rapid-transfer', { method: 'POST' }),

  triggerHighVelocity: () =>
    apiCall('/api/v1/demo/trigger/high-velocity', { method: 'POST' }),

  triggerLargeAmount: () =>
    apiCall('/api/v1/demo/trigger/large-amount', { method: 'POST' }),

  triggerNewAccountLargeTxn: () =>
    apiCall('/api/v1/demo/trigger/new-account-large-txn', { method: 'POST' }),

  triggerBlacklistTransfer: () =>
    apiCall('/api/v1/demo/trigger/blacklist-transfer', { method: 'POST' }),
};
