import { useState, useCallback, useEffect, useRef } from 'react';

/**
 * Custom hook for calling async API functions with loading/error state.
 *
 * Usage:
 *   const { data, loading, error, execute } = useApi(api.accounts.getAll);
 *   useEffect(() => { execute(token); }, []);
 *
 * @param {Function} apiFn - An async function from services/api.js
 * @returns {{ data: any, loading: boolean, error: string|null, execute: Function }}
 */
export default function useApi(apiFn) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const mountedRef = useRef(true);

  // Track unmount to avoid state updates on unmounted component
  useEffect(() => {
    mountedRef.current = true;
    return () => { mountedRef.current = false; };
  }, []);

  const execute = useCallback(async (...args) => {
    setLoading(true);
    setError(null);
    try {
      const result = await apiFn(...args);
      if (mountedRef.current) {
        setData(result);
        setLoading(false);
      }
      return result;
    } catch (err) {
      if (mountedRef.current) {
        setError(err.message || 'Something went wrong');
        setLoading(false);
      }
      throw err;
    }
  }, [apiFn]);

  return { data, loading, error, execute };
}
