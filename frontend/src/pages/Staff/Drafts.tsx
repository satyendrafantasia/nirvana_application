import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import client, { errorMessage } from '../../api/client';
import { useAuth } from '../../hooks/useAuth';
import Fields from '../../features/Fields';
import Results from '../../features/Results';
import { clean } from '../../features/model';
import Button from '../../components/common/Button';
type Draft = {
  resumeToken: string;
  spaId?: number;
  version: number;
  step: string;
  payload: string;
  expiresAt: string;
};
const steps: Record<string, { title: string; schema: string; path?: string }> = {
  REGISTER_SPA: { title: 'Register a spa', schema: 'SpaOnboardingStartRequest' },
  DETAILS: { title: 'Business details', schema: 'SpaOnboardingDetailsRequest', path: 'details' },
  ADDRESS: { title: 'Address', schema: 'AddressDTO', path: 'address' },
  KYC: { title: 'Verification documents', schema: 'SpaKycRequest', path: 'kyc' }
};
export default function Drafts() {
  const { user } = useAuth();
  const qc = useQueryClient();
  const [step, setStep] = useState('REGISTER_SPA');
  const [spaId, setSpaId] = useState('');
  const spas = useQuery({
    queryKey: ['draft-spas', user?.id],
    queryFn: async () =>
      (await client.get<{ id: number; name: string }[]>('/api/onboarding/spas')).data
  });
  const [current, setCurrent] = useState<Draft>();
  const [value, setValue] = useState<Record<string, unknown>>({});
  const [remove, setRemove] = useState(false);
  const [created, setCreated] = useState<number>();
  const key = ['onboarding-drafts', user?.id];
  const drafts = useQuery({
    queryKey: key,
    queryFn: async () => (await client.get<Draft[]>('/api/onboarding/spas/drafts')).data
  });
  const schema = {
    $ref: '#/components/schemas/' + (steps[step]?.schema || 'SpaOnboardingStartRequest')
  };
  const action = useMutation({
    mutationFn: async ({ mode, token }: { mode: string; token?: string }) => {
      const path = '/api/onboarding/spas/drafts';
      if (mode === 'resume') {
        const { data } = await client.get<Draft>(`${path}/${encodeURIComponent(token!)}`);
        setStep(data.step);
        setSpaId(data.spaId ? String(data.spaId) : '');
        setValue(JSON.parse(data.payload));
        setCurrent(data);
        setCreated(undefined);
        setRemove(false);
      }
      if (mode === 'save') {
        const { data } = await client.post<Draft>(path, {
          step,
          spaId: spaId ? Number(spaId) : undefined,
          payload: JSON.stringify(value),
          resumeToken: current?.resumeToken,
          version: current?.version,
          clientEventAt: new Date().toISOString(),
          clientRequestId: crypto.randomUUID()
        });
        setCurrent(data);
      }
      if (mode === 'extend') {
        const { data } = await client.post<Draft>(
          `${path}/${encodeURIComponent(current!.resumeToken)}/heartbeat`
        );
        setCurrent(data);
      }
      if (mode === 'delete') {
        await client.delete(`${path}/${encodeURIComponent(current!.resumeToken)}`);
        setCurrent(undefined);
        setValue({});
        setRemove(false);
      }
      if (mode === 'submit') {
        if (!steps[step])
          throw new Error('This saved draft uses an older form. Continue in Spa Management.');
        if (step !== 'REGISTER_SPA' && !spaId) throw new Error('Choose a spa.');
        const { data } = await client.request({
          method: step === 'REGISTER_SPA' ? 'POST' : 'PUT',
          url:
            step === 'REGISTER_SPA'
              ? '/api/onboarding/spas'
              : `/api/onboarding/spas/${spaId}/${steps[step].path}`,
          data: clean(value, schema)
        });
        setCreated(data.id || data.spaId);
        if (current) {
          await client.delete(`${path}/${encodeURIComponent(current.resumeToken)}`);
          setCurrent(undefined);
        }
      }
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: key });
    }
  });
  return (
    <div className="max-w-4xl mx-auto p-8 space-y-5">
      <h1 className="text-3xl font-bold">Onboarding Drafts</h1>
      <p>
        Save unfinished registration details, then resume and submit when ready. Saved drafts expire
        after 48 hours unless extended.
      </p>
      <div className="flex flex-wrap gap-3">
        {drafts.data?.map((d) => (
          <Button
            key={d.resumeToken}
            variant="secondary"
            disabled={action.isPending}
            onClick={() => action.mutate({ mode: 'resume', token: d.resumeToken })}
          >
            {steps[d.step]?.title || d.step} · expires {new Date(d.expiresAt).toLocaleDateString()}
          </Button>
        ))}
      </div>
      <Button
        variant="secondary"
        onClick={() => {
          setCurrent(undefined);
          setValue({});
          setStep('REGISTER_SPA');
          setSpaId('');
          setCreated(undefined);
          setRemove(false);
          action.reset();
        }}
      >
        New draft
      </Button>
      <label className="block">
        Onboarding step
        <select
          className="block border rounded-xl p-3 w-full"
          disabled={!!current || action.isPending}
          value={step}
          onChange={(e) => {
            setStep(e.target.value);
            setValue({});
            setCreated(undefined);
          }}
        >
          {!steps[step] && <option value={step}>{step} (legacy draft)</option>}
          {Object.entries(steps).map(([key, v]) => (
            <option key={key} value={key}>
              {v.title}
            </option>
          ))}
        </select>
      </label>
      {step !== 'REGISTER_SPA' && (
        <label className="block">
          Spa
          <select
            className="block border rounded-xl p-3 w-full"
            value={spaId}
            onChange={(e) => setSpaId(e.target.value)}
          >
            <option value="">Choose spa</option>
            {spas.data?.map((v) => (
              <option key={v.id} value={v.id}>
                {v.name}
              </option>
            ))}
          </select>
        </label>
      )}
      {steps[step] ? (
        <form
          className="bg-white rounded-xl p-6 space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            action.mutate({ mode: 'submit' });
          }}
        >
          <Fields
            schema={schema}
            value={value}
            onChange={(v) => setValue(v as Record<string, unknown>)}
          />
          <div className="flex gap-3">
            <Button
              type="button"
              disabled={action.isPending || !!created}
              onClick={() => action.mutate({ mode: 'save' })}
            >
              Save draft
            </Button>
            <Button type="submit" disabled={action.isPending || !!created}>
              Submit onboarding step
            </Button>
          </div>
        </form>
      ) : (
        <section>
          <p>This older draft can be reviewed here. Continue editing through Spa Management.</p>
          <Results data={value} />
        </section>
      )}
      {current && (
        <section className="space-y-3">
          <p>Draft expires {new Date(current.expiresAt).toLocaleString()}</p>
          <Button disabled={action.isPending} onClick={() => action.mutate({ mode: 'extend' })}>
            Keep draft for another 48 hours
          </Button>
          <label className="block">
            <input type="checkbox" checked={remove} onChange={(e) => setRemove(e.target.checked)} />{' '}
            Delete this saved draft permanently
          </label>
          <Button
            disabled={!remove || action.isPending}
            onClick={() => action.mutate({ mode: 'delete' })}
          >
            Delete draft
          </Button>
        </section>
      )}
      {created && (
        <p role="status">
          Onboarding step saved for spa #{created}.{' '}
          <Link className="underline" to="/features/spa-management">
            Continue business details and verification
          </Link>
          . The completed draft is cleared after submission.
        </p>
      )}
      {action.isSuccess && !created && <p role="status">Draft updated.</p>}
      {(action.isError || drafts.isError) && (
        <p role="alert" className="text-rose-700">
          {errorMessage(action.error || drafts.error)}
        </p>
      )}
    </div>
  );
}
