import { useState } from 'react';
import Button from '../common/Button';

export function storefrontPath(id: string, name: string) {
  const slug = name.normalize('NFKD').toLowerCase().replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 80) || 'spa';
  return '/s/' + encodeURIComponent(id) + '/' + slug;
}

export function enquiryLink(phone: string | null, name: string) {
  if (!phone || !/^\+[1-9]\d{7,14}$/.test(phone)) return undefined;
  return 'https://wa.me/' + phone.slice(1) + '?text=' + encodeURIComponent('Hello ' + name + ', I have a question about booking a treatment.');
}

export function ShareStorefront({path}: {path: string}) {
  const [copied, setCopied] = useState(false);
  const [manual, setManual] = useState(false);
  const url = new URL(path, window.location.origin).href;
  async function copy() {
    try { await navigator.clipboard.writeText(url); setCopied(true); }
    catch { setManual(true); }
  }
  return <><Button variant="secondary" onClick={() => void copy()}>Copy storefront link</Button>
    {copied && <span role="status">Link copied</span>}
    {manual && <input aria-label="Storefront link to copy" readOnly value={url} onFocus={e => e.target.select()} />}
  </>;
}
