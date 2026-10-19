import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import client, { errorMessage } from '../../api/client';
import Button from '../../components/common/Button';
export default function Files() {
  const [key, setKey] = useState(''),
    [confirmed, setConfirmed] = useState(false),
    [url, setUrl] = useState('');
  const action = useMutation({
    mutationFn: async (mode: 'download' | 'url' | 'delete') => {
      if (!key.trim()) throw new Error('Enter the file key from its upload record.');
      const params = { key: key.trim() };
      if (mode === 'url') {
        const { data } = await client.get<string>('/api/s3/object/url', { params });
        if (!/^https?:\/\//i.test(data)) throw new Error('The file URL is unavailable.');
        setUrl(data);
      }
      if (mode === 'download') {
        const { data } = await client.get('/api/s3/object/download', {
          params,
          responseType: 'blob'
        });
        const objectUrl = URL.createObjectURL(data);
        const a = document.createElement('a');
        a.href = objectUrl;
        a.download = key.split('/').pop() || 'download';
        a.click();
        setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
      }
      if (mode === 'delete') {
        await client.delete('/api/s3/object', { params });
        setUrl('');
        setConfirmed(false);
      }
      return mode;
    }
  });
  return (
    <div className="max-w-2xl mx-auto p-8 space-y-5">
      <h1 className="text-3xl font-bold">File Management</h1>
      <p>
        Use a stored file key from an upload record. Deleting a file permanently removes its
        content.
      </p>
      <label className="block">
        File key
        <input
          className="block border rounded-xl p-3 w-full"
          value={key}
          onChange={(e) => {
            setKey(e.target.value);
            setUrl('');
            setConfirmed(false);
            action.reset();
          }}
        />
      </label>
      <div className="flex gap-3">
        <Button disabled={action.isPending} onClick={() => action.mutate('download')}>
          Download file
        </Button>
        <Button disabled={action.isPending} onClick={() => action.mutate('url')}>
          Get file link
        </Button>
      </div>
      {url && (
        <a href={url} target="_blank" rel="noreferrer" className="underline">
          Open file
        </a>
      )}
      <label className="block">
        <input
          type="checkbox"
          checked={confirmed}
          onChange={(e) => setConfirmed(e.target.checked)}
        />{' '}
        Permanently delete this file
      </label>
      <Button disabled={!confirmed || action.isPending} onClick={() => action.mutate('delete')}>
        Delete file
      </Button>
      {action.isSuccess && (
        <p role="status">
          {action.data === 'delete'
            ? 'File deleted.'
            : action.data === 'download'
              ? 'Download started.'
              : 'File link ready.'}
        </p>
      )}
      {action.isError && (
        <p role="alert" className="text-rose-700">
          {errorMessage(action.error)}
        </p>
      )}
    </div>
  );
}
