import { Slot } from '../../types/booking';
/** Prefer the first eligible room after applying the therapist filter. */
export function uniqueAvailableTimes(slots: Slot[], therapist?: string) {
  const times = new Map<number, Slot>();
  for (const slot of slots) {
    if (
      !slot.available ||
      (therapist &&
        (therapist.startsWith('type:')
          ? !slot.therapistTypes.some((t) => t.type === therapist.slice(5) && t.availableCount > 0)
          : !slot.therapists.some((t) => t.id === therapist && t.available)))
    )
      continue;
    const key = Date.parse(slot.startTime);
    if (!times.has(key)) times.set(key, slot);
  }
  return [...times.values()];
}

/** A reschedule keeps the customer's original choice until they explicitly change it. */
export function replacementTimes(slots: Slot[], choice: string, originalPreference?: string) {
  return uniqueAvailableTimes(slots, choice === 'keep' ? originalPreference : choice);
}
