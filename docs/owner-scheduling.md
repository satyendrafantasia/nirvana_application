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

## Customer therapist preferences

In Owner dashboard → Therapists, set **Customer therapist choices** and edit each employee's **Public name** and optional **Type** (profile label). The internal name stays available on the owner schedule. Service qualifications and shifts are managed independently of the profile label.

- `GET /api/spas/{spa}/services/{service}/therapists` returns the service directory independently of date, for example `{"named":[{"id":42,"name":"Maya","type":"Thai"}],"labels":["Thai"]}`. A labels-only spa returns an empty named array. The existing dated slots endpoint returns the eligible subset at each start.
- On the slot hold or booking request send `therapistId`, `therapistType`, or omit both for Any. A hold reserves one therapist and room through checkout. The returned `therapistName` is the display text permitted by the spa. A 409 means the client should refresh appointment times.
- `POST /api/bookings/{id}/reschedule` with `{"targetSlotId":123}` retains the original preference. Set `changeTherapistPreference` to true to choose another named therapist, label, or Any.
- Owners can GET or PUT `/api/owner/spas/{spa}/therapist-visibility` using `{"names":true,"labels":false}`. Individual employee updates use the existing versioned therapist endpoint with optional `publicName` and `type`.

Migration V43 leaves existing public names empty and keeps each spa's visibility flags. It records customer preference separately from employee assignment on new reservations. Historical views use the employee's current public name and current publication settings. Withdrawing a name hides it on subsequent customer history requests. Existing bookings with unknown preferences retain their assigned employee on reschedule where one exists. Unassigned historical bookings have no therapist display.
