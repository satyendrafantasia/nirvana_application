import { useQuery } from '@tanstack/react-query';
import client, { errorMessage } from '../../api/client';
/** General booking questions, shown at the foot of a spa's page. */
export default function SpaInformation() {
  const faqs = useQuery({
    queryKey: ['faqs'],
    queryFn: async () =>
      (await client.get<{ faqs: { id: number; question: string; answer: string }[] }>('/api/faqs'))
        .data.faqs
  });
  if (!faqs.isError && !faqs.data?.length) return null;
  return (
    <section className="spa-section" aria-labelledby="faq-heading">
      <h2 id="faq-heading">Helpful to know</h2>
      {faqs.isError && <p role="alert">{errorMessage(faqs.error)}</p>}
      <div>
        {faqs.data?.map((f) => (
          <details className="card p-4 mb-3" key={f.id}>
            <summary>{f.question}</summary>
            <p className="mt-3">{f.answer}</p>
          </details>
        ))}
      </div>
    </section>
  );
}
