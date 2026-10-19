import { Schema, resolve, label } from './model';
import { useState } from 'react';
export type Choice = { value: string; label: string };
export default function Fields({
  schema,
  value,
  onChange,
  choices = {},
  name = '',
  required = false
}: {
  schema: Schema;
  value: unknown;
  onChange: (v: unknown) => void;
  choices?: Record<string, Choice[]>;
  name?: string;
  required?: boolean;
}) {
  const s = resolve(schema);
  const input = 'w-full rounded-xl border border-slate-300 bg-white px-3 py-2';
  if (s.additionalProperties && !s.properties)
    return (
      <MapFields
        name={name}
        schema={
          typeof s.additionalProperties === 'object' ? s.additionalProperties : { type: 'string' }
        }
        value={(value || {}) as Record<string, unknown>}
        onChange={onChange}
      />
    );
  if (s.type === 'object' || s.properties) {
    const v = (value || {}) as Record<string, unknown>;
    return (
      <div className="grid gap-4 md:grid-cols-2">
        {Object.entries(s.properties || {})
          .filter(([k]) => !['metaJson', 'payloadJson'].includes(k))
          .map(([k, child]) => (
            <Fields
              key={k}
              schema={child}
              name={k}
              required={s.required?.includes(k)}
              value={v[k]}
              onChange={(x) => onChange({ ...v, [k]: x })}
              choices={choices}
            />
          ))}
      </div>
    );
  }
  if (s.type === 'array') {
    const list = (value || []) as unknown[];
    return (
      <fieldset className="rounded-xl border p-3 md:col-span-2">
        <legend>{label(name)}</legend>
        {list.map((v, i) => (
          <div key={i} className="my-3 space-y-2">
            <Fields
              schema={s.items || {}}
              value={v}
              name={name}
              onChange={(x) => onChange(list.map((a, n) => (n === i ? x : a)))}
              choices={choices}
            />
            <button
              type="button"
              className="text-rose-700 text-sm"
              onClick={() => onChange(list.filter((_, n) => n !== i))}
            >
              Remove item
            </button>
          </div>
        ))}
        <button
          type="button"
          className="text-primary-700"
          onClick={() => onChange([...list, resolve(s.items || {}).type === 'object' ? {} : ''])}
        >
          Add {label(name).toLowerCase()}
        </button>
      </fieldset>
    );
  }
  if (s.type === 'boolean')
    return (
      <label className="flex gap-3 items-center">
        <input
          type="checkbox"
          checked={value === true}
          onChange={(e) => onChange(e.target.checked)}
        />
        {label(name)}
      </label>
    );
  const options =
    s.enum?.map((x) => ({ value: x, label: x.replaceAll('_', ' ') })) || choices[name];
  const long = /description|message|reason|text|notes|instructions|content|addressLine/i.test(name);
  return (
    <label className={'block space-y-1 ' + (long ? 'md:col-span-2' : '')}>
      <span className="text-sm font-medium">
        {label(name)}
        {required ? ' *' : ''}
      </span>
      {options?.length ? (
        <select
          className={input}
          required={required}
          value={String(value ?? '')}
          onChange={(e) => onChange(e.target.value)}
        >
          <option value="">Choose {label(name).toLowerCase()}</option>
          {options.map((x) => (
            <option key={x.value} value={x.value}>
              {x.label}
            </option>
          ))}
        </select>
      ) : long ? (
        <textarea
          className={input}
          required={required}
          maxLength={s.maxLength}
          value={String(value ?? '')}
          onChange={(e) => onChange(e.target.value)}
          rows={3}
        />
      ) : (
        <input
          className={input}
          required={required}
          type={
            s.format === 'date'
              ? 'date'
              : s.format === 'date-time'
                ? 'datetime-local'
                : s.format === 'email'
                  ? 'email'
                  : s.type === 'integer' || s.type === 'number'
                    ? 'number'
                    : /password|token/i.test(name)
                      ? 'password'
                      : 'text'
          }
          step={name.endsWith('Cents') ? '0.01' : s.type === 'integer' ? '1' : 'any'}
          min={name.endsWith('Cents') ? (s.minimum ?? 0) / 100 : s.minimum}
          max={s.maximum}
          minLength={s.minLength}
          maxLength={s.maxLength}
          value={String(value ?? '')}
          onChange={(e) => onChange(e.target.value)}
        />
      )}
      {name.endsWith('Cents') && (
        <small className="text-slate-500">
          Enter the full currency amount, for example 120.00.
        </small>
      )}
    </label>
  );
}
function MapFields({
  name,
  schema,
  value,
  onChange
}: {
  name: string;
  schema: Schema;
  value: Record<string, unknown>;
  onChange: (v: unknown) => void;
}) {
  const [key, setKey] = useState('');
  return (
    <fieldset className="border rounded-xl p-4 space-y-3 md:col-span-2">
      <legend>{label(name)}</legend>
      {Object.entries(value).map(([k, v]) => (
        <div key={k} className="flex gap-3 items-center">
          <Fields
            schema={schema}
            name={k}
            value={v}
            onChange={(x) => onChange({ ...value, [k]: x })}
          />
          <button
            type="button"
            className="text-rose-700"
            onClick={() => {
              const next = { ...value };
              delete next[k];
              onChange(next);
            }}
          >
            Remove
          </button>
        </div>
      ))}
      <div className="flex flex-wrap gap-2">
        <input
          aria-label={`${label(name)} field name`}
          className="border rounded-lg p-2"
          value={key}
          onChange={(e) => setKey(e.target.value)}
          placeholder="Field name"
        />
        <button
          type="button"
          className="text-primary-700"
          disabled={!key.trim()}
          onClick={() => {
            onChange({ ...value, [key.trim()]: '' });
            setKey('');
          }}
        >
          Add field
        </button>
      </div>
    </fieldset>
  );
}
