import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import axios from 'axios';
import { useAuth } from '../../hooks/useAuth';
import { claimReferral, forgetReferral, pendingReferral, rememberReferral } from '../../api/rewardsApi';

/**
 * A referral link (?ref=CODE) is remembered on arrival and claimed once the visitor has signed
 * up or signed in. A code the server refuses (own code, already referred, too late) is dropped.
 */
export default function PendingReferral() {
  const { search } = useLocation();
  const { user } = useAuth();
  const trying = useRef(false);
  useEffect(() => rememberReferral(search), [search]);
  useEffect(() => {
    const code = pendingReferral();
    if (!user || !code || trying.current) return;
    trying.current = true;
    claimReferral(code)
      .then(() => forgetReferral())
      .catch((e) => {
        if (axios.isAxiosError(e) && e.response && e.response.status < 500) forgetReferral();
      })
      .finally(() => {
        trying.current = false;
      });
  }, [user]);
  return null;
}
