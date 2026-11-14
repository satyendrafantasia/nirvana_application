import { describe,it,expect } from 'vitest';
import {instant} from './Schedule';

describe('owner schedule local time inputs',()=>{
  it('uses the spa time zone even when it differs from the browser',()=>{
    expect(instant('2027-01-18T10:00','Asia/Kolkata')).toBe('2027-01-18T04:30:00Z');
  });
  it('rejects skipped and ambiguous times on clock change dates',()=>{
    expect(()=>instant('2027-03-28T01:30','Europe/London')).toThrow();
    expect(()=>instant('2027-10-31T01:30','Europe/London')).toThrow();
  });
  it('preserves the offset of a saved repeated hour when editing capacity',()=>{
    const original='2027-10-31T01:30:00+01:00';
    expect(instant('2027-10-31T01:30','Europe/London',original)).toBe(original);
  });
});
