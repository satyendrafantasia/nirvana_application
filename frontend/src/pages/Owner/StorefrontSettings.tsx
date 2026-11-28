import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { Link } from 'react-router-dom';
import client, { errorMessage } from '../../api/client';
import Button from '../../components/common/Button';
import { ShareStorefront, storefrontPath } from '../../components/spa/StorefrontLinks';
import { PhoneCheck } from './ClaimListing';

interface Settings { name: string; placeId: string; verifiedNumber: string | null; enabled: boolean }

export default function StorefrontSettings({spa}: {spa: string}) {
  const api = '/api/owner/spas/' + spa + '/storefront';
  const key = ['storefront-settings', spa];
  const cache = useQueryClient();
  const [proof, setProof] = useState<string>();
  const settings = useQuery({queryKey: key, queryFn: async () => (await client.get<Settings>(api)).data, retry: false});
  const save = useMutation({mutationFn: async (enabled: boolean) =>
    (await client.put<Settings>(api, {enabled, verificationId: enabled ? proof : undefined})).data,
    onSuccess: data => {
      cache.setQueryData(key, data);
      void cache.invalidateQueries({queryKey: ['public-storefront', spa]});
      setProof(undefined);
    }});
  const s = settings.data;
  if (!s) {
    if (settings.isError && isAxiosError(settings.error) && [403,404].includes(settings.error.response?.status || 0))
      return <p>Storefronts are available to the owner of a published spa with an approved listing claim.</p>;
    return <p role="status">{settings.isError ? errorMessage(settings.error) : 'Loading storefront…'}</p>;
  }
  const path = storefrontPath(spa, s.name);
  return <section className="card space-y-3 p-5">
    <h2 className="text-xl font-semibold">Your storefront</h2>
    <div className="flex flex-wrap items-center gap-3"><Link className="underline" to={path}>Open storefront</Link><ShareStorefront path={path}/></div>
    <h3 className="font-semibold">WhatsApp enquiries</h3>
    <p>{s.enabled ? 'Guests can message ' + s.verifiedNumber + ' from your storefront.' :
      'Let guests ask a question before booking using your verified business number.'}</p>
    {!s.verifiedNumber && <PhoneCheck placeId={s.placeId} hasPhone verifiedId={proof} skipped={false} onVerified={setProof} />}
    <Button disabled={save.isPending || (!s.verifiedNumber && !proof)} onClick={() => save.mutate(!s.enabled)}>
      {s.enabled ? 'Turn off WhatsApp enquiries' : 'Enable WhatsApp enquiries'}
    </Button>
    {save.isError && <p role="alert">{errorMessage(save.error)}</p>}
  </section>;
}
