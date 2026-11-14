# Room and therapist scheduling

The owner dashboard's **Schedule** tab opens the selected spa's local day. On desktop, click a cell in the room calendar to jump to its editable slot. On mobile choose a room above the list. The Therapists tab manages display names, profile labels and service qualifications. Removing a therapist retires their scheduling profile and keeps their appointments.

## Owner API

All paths below follow `/api/owner/spas/{spaId}/schedule`. Use the existing authenticated owner or manager session.

| Method and path | Operation |
| --- | --- |
| GET /?date=2027-01-18 | Local date, timezone, slots, room IDs, booking references and therapist roster. Omit date for today. |
| GET /rooms | Read active and inactive rooms. |
| POST /rooms, PUT /rooms/{id} | Send `{"name":"Garden room","active":true}` to create or edit a room. |
| POST /slots | Send roomId, serviceId, offset-aware start and end, and positive party capacity. |
| PATCH /slots/{id} | Edit roomId, start, end, capacity, therapistId or closed. Use therapistId 0 to release a fixed therapist assignment. |
| DELETE /slots/{id} | Delete an unused slot. Slots carrying appointment history are retained. |
| GET /slots/{id}/therapists | Eligible therapists for a room slot. |
| GET or PUT /therapists/{id}/week | Weekly shifts, e.g. `[{"weekday":1,"start":"10:00","end":"18:00"}]`. Monday is 1. An empty saved week is off. |
| PUT /therapists/{id}/days/{date} | Replace that date's schedule with WORK, BREAK and TIME_OFF intervals. An empty list is a day off. |
| GET /available-therapists?serviceId=8&slotId=32 | Look up the bookable slot and therapist choices. |

### Generate slots

`POST /generate` with `{"date":"2027-01-18","roomIds":[4,5],"serviceId":8,"start":"10:00","end":"13:00","minutes":60}` produces six slots when both rooms are empty and the service fits. The response is `{"created":6,"skipped":[]}`. Skips identify roomId, start and end. Minutes defaults to 60. A room's original generated start is remembered so a repeat request preserves owner moves, blocks and deletions. Only complete intervals are generated.

### Partial-day leave

```json
{"intervals":[
  {"kind":"WORK","start":"2027-01-18T10:00:00+05:30","end":"2027-01-18T18:00:00+05:30"},
  {"kind":"BREAK","start":"2027-01-18T11:15:00+05:30","end":"2027-01-18T12:15:00+05:30"}
]}
```

Date overrides replace recurring shifts. With no saved weekly roster, globally available therapists inherit the spa hours. Openings, roster intervals and assignments are start-inclusive and end-exclusive. A room slot covers one appointment party for its entire saved interval. Its capacity limits guests within the party. A session must cover at least the service duration.

### Customer reservations

Customer time choices are deduplicated after filtering by therapist. A temporary hold and checkout both allocate a room and an eligible therapist while holding the spa's database scheduling lock. The holder can convert the reservation once. Pending payment, confirmed and arrived bookings occupy resources. Cancellation and hold expiry release both; a conflicting reschedule rolls back. An owner cannot block or move a booked slot. Resolve affected bookings using the existing reschedule or cancellation flow.

### Rollout

Run the normal Spring Boot/Flyway startup with migrations through V42, then deploy the matching frontend. V42 copies known slot rooms to existing bookings. Older bookings remain visible. A live booking or hold with either resource unknown conservatively blocks new reservations for its overlapping interval at that spa. Wait for expiry or resolve the commitment via the existing booking flow. Unassigned legacy slots remain in the owner calendar. Assign an active room to unused slots or generate room inventory before offering those times to customers.
