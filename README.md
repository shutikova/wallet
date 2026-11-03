# Multicurrency Wallet

REST API and MCP server for a multicurrency account: deposit, withdraw, and get the total in one currency
using Czech National Bank (CNB) rates. Java 21, Spring Boot, in-memory storage.

## Run

Requires Java 21. The app starts on http://localhost:8080.

```bash
./mvnw spring-boot:run   # run (on Windows: .\mvnw.cmd spring-boot:run)
./mvnw test              # test
./mvnw package           # build target/wallet-0.0.1-SNAPSHOT.jar
```

Or with Docker, without local Java:

```bash
docker build -t wallet .
docker run --rm -p 8080:8080 wallet
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI: http://localhost:8080/v3/api-docs
- Health: http://localhost:8080/actuator/health
- MCP: http://localhost:8080/mcp

## API

| Method | Path | Input | Returns (`200`) |
|---|---|---|---|
| POST | `/api/v1/wallet/deposits` | `{"amount": "10", "currency": "USD"}` | `{"currency": "USD", "balance": "10.00"}` |
| POST | `/api/v1/wallet/withdrawals` | `{"amount": "3", "currency": "USD"}` | `{"currency": "USD", "balance": "7.00"}` |
| GET | `/api/v1/wallet/balances` | | `{"balances": [{"currency": "USD", "amount": "7.00"}]}` |
| GET | `/api/v1/wallet/balances/total` | `?currency=USD` | see below |

Total, using CNB's table of 3 November 2026:

```json
{
  "currency": "USD",
  "total": "12.59",
  "rates": { "valuationDate": "2026-11-03", "tableDate": "2026-11-03", "provisional": false, "stale": false },
  "breakdown": [
    { "currency": "EUR", "amount": "5.00", "converted": "5.59" },
    { "currency": "USD", "amount": "7.00", "converted": "7.00" }
  ]
}
```

- Amounts are JSON strings. A JSON number or a repeated key is rejected.
- Currency codes work in any letter case. Balances list non-zero amounts only, sorted by code.
- `rates` is `null` when a total converts nothing: an empty wallet, or one holding only the target currency.

## MCP

AI agents can use the wallet through an MCP server at `/mcp` (stateless Streamable HTTP). It calls the same
service as the REST API, so the wallet and its rules are the same.

| Tool | Arguments | Returns |
|---|---|---|
| `deposit` | `amount`, `currency` | New balance of that currency |
| `withdraw` | `amount`, `currency` | New balance of that currency |
| `balances` | | All non-zero balances |
| `total` | `currency` | Total in that currency |

Results are the REST API's JSON, as text. A rejected call returns `isError: true` with the reason, and an
unexpected error shows no internal details.

To connect Claude Code:

```bash
claude mcp add --transport http wallet http://localhost:8080/mcp
```

Or call a tool directly:

```bash
curl -X POST localhost:8080/mcp -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"deposit","arguments":{"amount":"10","currency":"USD"}}}'
```

## Health and logs

`/actuator/health` returns `"status":"UP"` while the app is running. It does not check CNB, because deposits
and withdrawals work without it. No other Actuator endpoint is exposed.

Logs go to the console as JSON in ECS format, one object per line:

| Event | Level | Message |
|---|---|---|
| Deposit or withdrawal | INFO | `Deposited 10.00 USD, new balance 10.00` |
| Rejected request | INFO | `Request rejected: insufficient-funds: The USD balance is too low for this withdrawal` |
| Rejected MCP tool call | INFO | `Tool call rejected: The USD balance is too low for this withdrawal` |
| New CNB table | INFO | `Loaded CNB rate table of 2026-11-03` |
| CNB failure | WARN | `CNB rate table could not be loaded: Cannot get the rate table from CNB` |
| Unexpected error | ERROR | `Unexpected error`, with the stack trace |

## Design decisions

### 1. Missing rates

The newest published table is used, and `rates.tableDate` tells the client which one.

- Weekend or holiday: the last working day's table, which is the official rate. `provisional: false`.
- Working day before today's table is out (about 14:30): the previous table with `provisional: true`,
  because the total will change.
- Working days are weekdays minus the 11 fixed public holidays. "Today" is the date in `Europe/Prague`.

### 2. Rate units

The parser reads CNB's `Amount` column: at 13.755 CZK per 100 JPY, 1000 JPY = 137.55 CZK. Units must be
powers of 10, which keeps conversion exact, and rates must be plain decimals. A supported currency's row
that breaks either rule rejects the table.

### 3. CNB unavailable and caching

Rates are cached in memory. If CNB is down, totals use the cached table with `stale: true`. With no usable
table, they return `503`. Deposits, withdrawals and totals that convert nothing never need CNB.

- CNB is called only when a request needs rates and the cache is empty or older than the newest table that
  can exist: today's from 13:30 on a working day (an hour before usual publication, to cover an early
  release), otherwise the last working day's.
- After a failure with nothing cached, the next attempt waits 1 minute. Otherwise it waits 10 minutes.
- One request runs at a time, with a 2-second connect and 5-second read timeout.
- A failed, unparseable or future-dated response counts as CNB being down. Only a newer table replaces the
  cached one.
- A table is usable up to and including 7 days after its date, which covers Christmas.

### 4. Money precision

`BigDecimal`, never `double`. Decimal places follow ISO 4217 (2 for USD, 0 for JPY).

- Input is never rounded. Too many decimals is rejected, counted as typed, so `10.000` USD fails.
- An amount is a plain decimal above 0, with at most 15 digits before the decimal point, also counted as
  typed. A balance can grow beyond that.
- Conversion goes through CZK at full precision. Only the total is rounded, once, with `HALF_EVEN`.
  Breakdown values are rounded for display and can differ from it by a cent.

### 5. Concurrency

One lock covers each balance check and update, and the copy of balances used for totals, so simultaneous
withdrawals cannot spend the same money. CNB is never called under the lock. A test runs 100 concurrent
withdrawals of 1 USD from 50 USD, and exactly 50 succeed.

### 6. Errors

RFC 9457 Problem Details (`application/problem+json`):

| Situation | Status | `type` |
|---|---|---|
| Malformed JSON, missing field, repeated key, invalid amount | 400 | `/problems/invalid-request` |
| Not a valid ISO 4217 code | 400 | `/problems/invalid-currency` |
| Valid currency the wallet does not support | 422 | `/problems/unsupported-currency` |
| Withdrawal larger than the balance | 422 | `/problems/insufficient-funds` |
| Total needs a rate CNB does not quote | 422 | `/problems/rate-not-available` |
| No usable CNB rates | 503 | `/problems/rates-unavailable` |

- Other errors, such as 404, 405, 406, 415 and 500, also use Problem Details but have no `type`, which
  RFC 9457 defines as `about:blank`. A 500 shows no internal details.
- A client that does not accept JSON gets `406` before the request runs, so no money moves.
- An unparseable `Accept` header, Spring Boot's `/error` page and web server errors keep their own format.

### 7. CZK and supported currencies

CZK has a fixed rate of 1 and never needs CNB.

- The wallet supports a fixed list, so deposits and withdrawals never need CNB: CZK and CNB's daily table of
  30 October 2026 (AUD, BRL, CAD, CHF, CNY, DKK, EUR, GBP, HKD, HUF, IDR, ILS, INR, ISK, JPY, KRW, MXN, MYR,
  NOK, NZD, PHP, PLN, RON, SEK, SGD, THB, TRY, USD, ZAR).
- XDR is in that table but rejected, because ISO 4217 defines no decimal places for it.
- CNB rows outside the list are ignored, so a new currency cannot break the table.
- If CNB drops a listed currency, totals that need its rate return `rate-not-available`.

### Other decisions

- One wallet, which matches the assignment.
- Daily rates only. CNB's monthly table stays fixed for a month, and the assignment asks for the current day.
- Standard layers in one Maven module, like Wultra's public services.
- Few dependencies: Spring Web, Validation, Actuator, springdoc-openapi, Lombok and Spring AI's MCP server.

## Known limitations

- No authentication or authorisation. Anyone who can reach the API or `/mcp` can move money.
- Logging is basic: console only, with no request IDs, metrics or tracing. 404, 405, 406 and 415 responses
  are not logged. The MCP library logs malformed MCP requests as `ERROR`.
- In-memory only: balances are lost on restart. No transaction history.
- No idempotency keys: a retried deposit is applied twice.
- Skipped: "any currency". Only CNB's daily table is supported, at mid rates without fees.
- Skipped: following CNB's currency list. A change needs a code change.
- Skipped: Good Friday and Easter Monday, whose dates change every year. Totals are wrongly `provisional` on
  those days, and CNB is checked every 10 minutes. The rates stay correct.
- A change to public holidays by law needs a code change.
- Totals wait for a running CNB request, up to about 7 seconds, even with a usable table cached.
- A CNB table with missing rows is accepted. Totals needing a missing currency return `rate-not-available`
  until the next table.
- OpenAPI shows response fields as optional, although they are always present.
- A missing or non-string MCP argument gets the MCP library's message, in the server's language.
