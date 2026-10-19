# Local demo spa catalog

`seed_demo_spas.py` inserts 10 fictional spas into the isolated MariaDB database
`nirvana_spa_test` on `127.0.0.1:3307`. It requires the existing
`owner.preview@example.test` manager account and a locally supplied `MYSQL_PWD`.
It never modifies existing spas; rerunning the same batch exits without duplicates.

```powershell
python scripts/seed_demo_spas.py --client <path-to-mariadb.exe> --start-date 2026-10-20 --report <report.json>
```

Includes 30 services, 20 therapists, 20 rooms, 20 spa packages, 20 image references,
210 daily inventory records, and 1,260 slots over seven days from the selected date.
The listings are published for local booking previews. Names, contact details,
addresses and staff are fictional; coordinates indicate approximate city locations.
No reviews, bookings, customers or payments are fabricated.

Images are external HTTPS references to free Pexels stock photos, checked before
insertion. Source pages and the https://www.pexels.com/license/ license link are
recorded in the generated report and spa metadata. Images are illustrative and do
not depict these fictional businesses. Internet access is required to load photos.
The seed is intentionally local-only, not a production migration.
