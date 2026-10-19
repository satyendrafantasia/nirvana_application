import { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useAuth } from '../../hooks/useAuth';
import { isStaff } from '../../auth/roles';
import client, { errorMessage } from '../../api/client';
import Button from '../../components/common/Button';
export default function Media() {
  const { user } = useAuth();
  return isStaff(user?.roles) ? (
    <Upload />
  ) : (
    <p className="p-8">Access restricted to authorized spa staff.</p>
  );
}
function Upload() {
  const [spa, setSpa] = useState('');
  const [file, setFile] = useState<File>();
  const [title, setTitle] = useState('');
  const [type, setType] = useState('IMAGE');
  const spas = useQuery({
    queryKey: ['media-spas'],
    queryFn: async () =>
      (await client.get('/api/onboarding/spas')).data as { id: number; name: string }[]
  });
  const upload = useMutation({
    mutationFn: async () => {
      if (!file) throw new Error('Choose a file.');
      const { data } = await client.post('/api/media/presign', {
        spaId: Number(spa),
        fileName: file.name,
        contentType: file.type || 'application/octet-stream',
        mediaType: type
      });
      const response = await fetch(data.uploadUrl, {
        method: 'PUT',
        headers: { 'Content-Type': file.type || 'application/octet-stream' },
        body: file
      });
      if (!response.ok) throw new Error('Upload failed. Please retry.');
      await client.post(`/api/spa/${spa}/media`, {
        objectKey: data.objectKey,
        mediaType: type,
        title
      });
    }
  });
  return (
    <div className="max-w-2xl mx-auto p-8 space-y-5">
      <h1 className="text-3xl font-bold">Spa photos & media</h1>
      <form
        className="rounded-2xl bg-white p-6 space-y-4"
        onSubmit={(e) => {
          e.preventDefault();
          upload.mutate();
        }}
      >
        <label className="block">
          Spa
          <select
            className="block border p-3 w-full rounded-lg"
            required
            value={spa}
            onChange={(e) => setSpa(e.target.value)}
          >
            <option value="">Choose your spa</option>
            {spas.data?.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
        </label>
        <label className="block">
          Title
          <input
            className="block border p-3 w-full rounded-lg"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
        </label>
        <label className="block">
          Media type
          <select
            className="block border p-3 w-full rounded-lg"
            value={type}
            onChange={(e) => setType(e.target.value)}
          >
            <option>IMAGE</option>
            <option>VIDEO</option>
          </select>
        </label>
        <label className="block">
          File
          <input
            className="block"
            type="file"
            accept={type === 'IMAGE' ? 'image/*' : 'video/*'}
            required
            onChange={(e) => setFile(e.target.files?.[0])}
          />
        </label>
        <Button type="submit" disabled={upload.isPending}>
          {upload.isPending ? 'Uploading…' : 'Upload to spa'}
        </Button>
      </form>
      {(upload.isError || spas.isError) && (
        <p role="alert" className="text-rose-700">
          {errorMessage(upload.error || spas.error)}
        </p>
      )}
      {upload.isSuccess && <p role="status">Media added to your spa.</p>}
    </div>
  );
}
