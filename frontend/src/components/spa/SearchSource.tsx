export type SpaSource = 'nirvana' | 'google';

export default function SearchSource({ value, onChange }: {
  value: SpaSource;
  onChange: (source: SpaSource) => void;
}) {
  return (
    <div className="spa-source" role="group" aria-label="Search provider">
      {(['nirvana', 'google'] as const).map((source) => (
        <button key={source} type="button" aria-pressed={value === source}
          onClick={() => onChange(source)}>
          {source === 'nirvana' ? 'Bookable on Nirvana' : 'Google Places'}
        </button>
      ))}
    </div>
  );
}
