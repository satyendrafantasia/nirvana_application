import { useEffect, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { errorMessage } from '../../api/client';
import { getHealthQuestions, getHealthTerms, saveHealthQuestions, saveHealthTerms } from '../../api/healthFormApi';
import Button from '../../components/common/Button';

const MAX = 5;

/** Extra health-form questions a spa asks for one service, shown to guests after they book it. */
export default function HealthQuestions({ spaId, serviceId }: { spaId: string; serviceId: string }) {
  const [open, setOpen] = useState(false);
  const [prompts, setPrompts] = useState<string[]>([]);
  const questions = useQuery({
    queryKey: ['owner-catalog', spaId, 'health-questions', serviceId],
    enabled: open,
    queryFn: () => getHealthQuestions(spaId, serviceId)
  });
  useEffect(() => {
    if (questions.data) setPrompts(questions.data.map((q) => q.prompt));
  }, [questions.data]);
  const save = useMutation({
    mutationFn: () => saveHealthQuestions(spaId, serviceId, prompts),
    onSuccess: (data) => setPrompts(data.map((q) => q.prompt))
  });
  return (
    <details onToggle={(e) => setOpen(e.currentTarget.open)}>
      <summary className="text-sm text-primary-700">Health form questions</summary>
      <div className="space-y-2 pt-2">
        <p className="text-sm">
          Every guest answers the standard health questions. Add up to {MAX} more for this service, for example
          “Any sunburn in the last week?”.
        </p>
        {questions.isError && <p role="alert">{errorMessage(questions.error)}</p>}
        {prompts.map((p, i) => (
          <div className="flex gap-2" key={i}>
            <input
              aria-label={'Question ' + (i + 1)}
              className="border rounded-xl p-2 flex-1"
              maxLength={300}
              value={p}
              onChange={(e) => setPrompts(prompts.map((x, j) => (j === i ? e.target.value : x)))}
            />
            <Button type="button" variant="ghost" onClick={() => setPrompts(prompts.filter((_, j) => j !== i))}>
              Remove
            </Button>
          </div>
        ))}
        <div className="flex gap-2">
          {prompts.length < MAX && (
            <Button type="button" variant="secondary" onClick={() => setPrompts([...prompts, ''])}>
              Add question
            </Button>
          )}
          <Button type="button" disabled={save.isPending || questions.isLoading} onClick={() => save.mutate()}>
            Save questions
          </Button>
        </div>
        {save.isSuccess && <p role="status">Questions saved.</p>}
        {save.isError && <p role="alert">{errorMessage(save.error)}</p>}
      </div>
    </details>
  );
}

/** The consent terms guests agree to on the health form. Empty restores the standard wording. */
export function HealthTerms({ spaId }: { spaId: string }) {
  const [terms, setTerms] = useState('');
  const current = useQuery({ queryKey: ['owner-catalog', spaId, 'health-terms'], queryFn: () => getHealthTerms(spaId) });
  useEffect(() => {
    if (current.data !== undefined) setTerms(current.data);
  }, [current.data]);
  const save = useMutation({ mutationFn: () => saveHealthTerms(spaId, terms), onSuccess: setTerms });
  return (
    <details className="card p-4">
      <summary className="font-semibold">Health form consent terms</summary>
      <label className="grid gap-1 pt-2">
        Terms guests agree to before their treatment (leave empty for the standard wording)
        <textarea className="border rounded-xl p-2 min-h-32" maxLength={2000} value={terms} onChange={(e) => setTerms(e.target.value)} />
      </label>
      {current.isError && <p role="alert">{errorMessage(current.error)}</p>}
      {save.isError && <p role="alert">{errorMessage(save.error)}</p>}
      {save.isSuccess && <p role="status">Terms saved.</p>}
      <Button type="button" disabled={save.isPending} onClick={() => save.mutate()}>
        Save terms
      </Button>
    </details>
  );
}
