import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import client, { errorMessage } from '../../api/client';
import Button from '../../components/common/Button';
import { MAX_GROUP } from '../../api/groupApi';

/**
 * "Couples room / side by side": guests of one group taking this service can share a room, up to the
 * chosen number at once. Off means each guest needs a room of their own.
 */
export default function SideBySideSetting({ spaId, serviceId }: { spaId: string; serviceId: string }) {
  const qc = useQueryClient();
  const current = useQuery({
    queryKey: ['owner-side-by-side', spaId],
    queryFn: async () => (await client.get<Record<string, number>>(`/api/owner/spas/${spaId}/side-by-side-services`)).data
  });
  const [capacity, setCapacity] = useState(0);
  useEffect(() => setCapacity(current.data?.[serviceId] ?? 0), [current.data, serviceId]);
  const save = useMutation({
    mutationFn: async () =>
      (await client.put(`/api/owner/spas/${spaId}/services/${serviceId}/side-by-side`, { capacity: capacity || null })).data,
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['owner-side-by-side', spaId] })
  });
  return (
    <details>
      <summary className="text-sm text-primary-700">Couples room / side by side</summary>
      <div className="flex flex-wrap items-center gap-2 pt-2">
        <label className="text-sm">
          Guests who can share one room for this service
          <select className="ml-2 rounded-xl border p-2" value={capacity} onChange={(e) => setCapacity(Number(e.target.value))}>
            <option value={0}>Off: one guest per room</option>
            {Array.from({ length: MAX_GROUP - 1 }, (_, i) => i + 2).map((n) => (
              <option key={n} value={n}>
                Up to {n} side by side
              </option>
            ))}
          </select>
        </label>
        <Button variant="secondary" disabled={save.isPending} onClick={() => save.mutate()}>
          Save
        </Button>
        {save.isError && <p role="alert">{errorMessage(save.error)}</p>}
        {save.isSuccess && <p role="status">Saved.</p>}
      </div>
    </details>
  );
}
