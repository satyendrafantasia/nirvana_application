import { Link } from 'react-router-dom';
import { label } from './model';
const hidden = /password|secret|token|fingerprint|version|metaJson|payload|roles|authorities/i;
export default function Results({ data, depth = 0 }: { data: unknown; depth?: number }) {
  if (data === undefined) return null;
  if (data === null) return <span className="text-slate-500">Not provided</span>;
  if (Array.isArray(data))
    return data.length ? (
      <div className="space-y-3">
        {data.map((item, i) => (
          <div key={i} className="rounded-xl border border-slate-100 bg-white p-4">
            <Results data={item} depth={depth + 1} />
          </div>
        ))}
      </div>
    ) : (
      <p className="text-slate-500">No records to show yet.</p>
    );
  if (typeof data === 'object') {
    if (depth > 6) return <span>Additional details available from the team.</span>;
    const obj = data as Record<string, unknown>;
    return (
      <div className="space-y-2">
        {Object.entries(obj)
          .filter(
            ([k]) =>
              !hidden.test(k) &&
              ![
                'pageable',
                'sort',
                'empty',
                'totalElements',
                'totalPages',
                'size',
                'number',
                'numberOfElements',
                'first',
                'last'
              ].includes(k) &&
              !(k === 'page' && typeof obj[k] === 'number')
          )
          .map(([k, v]) => (
            <div key={k} className={v && typeof v === 'object' ? 'mt-3' : 'flex flex-wrap gap-x-3'}>
              {!['page', 'content'].includes(k) && (
                <span className="font-medium text-slate-600">
                  {label(k.replace(/Cents$/, ''))}:
                </span>
              )}
              <Results
                data={
                  typeof v === 'number' && /Cents$/.test(k)
                    ? `${(v / 100).toFixed(2)}${obj.currency ? ' ' + obj.currency : ''}`
                    : v
                }
                depth={depth + 1}
              />
            </div>
          ))}
        {typeof obj.spaId === 'number' && (
          <Link className="inline-block text-primary-700 underline" to={`/spas/${obj.spaId}`}>
            View spa & book
          </Link>
        )}
      </div>
    );
  }
  if (typeof data === 'boolean') return <span>{data ? 'Yes' : 'No'}</span>;
  const value = String(data);
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(value) && !Number.isNaN(Date.parse(value)))
    return <span>{new Date(value).toLocaleString()}</span>;
  if (/^https?:\/\//i.test(value))
    return (
      <a
        href={value}
        target="_blank"
        rel="noreferrer"
        className="text-primary-700 underline break-all"
      >
        Open document or link
      </a>
    );
  return <span className="whitespace-pre-wrap break-words">{value}</span>;
}
