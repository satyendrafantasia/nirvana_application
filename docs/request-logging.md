# Investigating a Nirvana request

In the browser Network panel, select the API call and copy the response header
`X-Trace-ID` to investigate one request, or `X-Correlation-ID` to follow the
requests made by the same loaded page. The shared web client creates a new
random correlation ID on page load. A reload or a redirect can start a new
group. The API creates a correlation ID when a caller omits it or supplies an
invalid UUID. These identifiers are diagnostic metadata, never proof of identity.

On the EC2 host, substitute the actual identifier in either command:

```bash
sudo journalctl -u nirvana-api --since '1 hour ago' -o cat | grep -F 'traceId=TRACE_ID'
sudo journalctl -u nirvana-api --since '1 hour ago' -o cat | grep -F 'correlationId=CORRELATION_ID'
```

Request completion includes the controller operation (or unmapped for requests
rejected before routing), HTTP status and elapsed milliseconds. The numeric
userId comes from the authenticated backend principal. Request start is logged
before authentication. Booking and payment steps indicate how far an attempt
progressed. A persist step alone does not prove that a transaction committed.

New request logs omit bodies, raw URLs, search terms, headers, contact details,
OTP codes and payment credentials. Keep log access restricted to operators.
The trace headers are visible in browser developer tools. Provider callbacks
and background work can start separate traces.
