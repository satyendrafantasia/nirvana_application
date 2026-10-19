import { useQuery } from '@tanstack/react-query';
import client, { errorMessage } from '../../api/client';
export default function SpaInformation({ spaId }: { spaId: string }) {
  const reviews = useQuery({
    queryKey: ['reviews', spaId],
    queryFn: async () =>
      (
        await client.get<{
          page: { content: { id: number; rating: number; title: string; text: string }[] };
        }>(`/api/spas/${spaId}/reviews`)
      ).data.page.content
  });
  const faqs = useQuery({
    queryKey: ['faqs'],
    queryFn: async () =>
      (await client.get<{ faqs: { id: number; question: string; answer: string }[] }>('/api/faqs'))
        .data.faqs
  });
  return (
    <div className="grid md:grid-cols-2 gap-8 mt-10">
      <section>
        <h2 className="text-2xl font-semibold mb-4">Guest reviews</h2>
        {reviews.isLoading && <p>Loading reviews…</p>}
        {reviews.isError && <p role="alert">{errorMessage(reviews.error)}</p>}
        {reviews.data?.length === 0 && <p>No reviews yet.</p>}
        {reviews.data?.map((r) => (
          <article className="card p-4 mb-3" key={r.id}>
            <p>
              {r.rating}/5 · {r.title}
            </p>
            <p>{r.text}</p>
          </article>
        ))}
      </section>
      <section>
        <h2 className="text-2xl font-semibold mb-4">Helpful to know</h2>
        {faqs.isError && <p role="alert">{errorMessage(faqs.error)}</p>}
        {faqs.data?.map((f) => (
          <details className="card p-4 mb-3" key={f.id}>
            <summary>{f.question}</summary>
            <p className="mt-3">{f.answer}</p>
          </details>
        ))}
      </section>
    </div>
  );
}
