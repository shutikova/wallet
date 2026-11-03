# Instructions for Claude Code

## The specification

README.md is the specification. Implement its API and design decisions exactly. If the code needs to
differ from the README, or the README is unclear, stop and ask me. Never resolve it silently. When
we agree on a change, update README.md in the same stage.

## Stack

- Java 21, Spring Boot, Maven wrapper. Use the existing project and its base package.
- Allowed dependencies: Spring Web, Validation, Actuator, springdoc-openapi (pick the version that
  matches our Spring Boot version), Lombok, Spring AI's MCP server starter, and the test starter. **Ask
  before adding any other dependency.**
- Do not use a database, JPA, WebFlux, `spring-boot-starter-cache`, or a retry library.

## Packages

One Maven module, layered like Wultra's public services:

- `controller`: REST controllers
- `mcp`: MCP tools, which call the same service as the controllers
- `errorhandling`: the global exception handler
- `configuration`: `Clock`, `RestClient`, Jackson and OpenAPI beans
- `model`: data only, such as value objects (rates, conversion results). A constructor may reject
  invalid values, but business logic goes in `service`
- `model.error`: exceptions
- `model.request`, `model.response`: request/response records
- `repository`: the in-memory storage, which holds the balances and the lock. It offers specific
  operations only and never exposes the map or runs callbacks under the lock
- `service`: the wallet service
- `service.money`: currency validation, amount normalization, conversion
- `service.cnb`: `ExchangeRateProvider` interface, CNB client, CNB parser, caching provider, holiday calendar

## Rules that must never be broken

### Money
- Use `BigDecimal` for every amount. Never `double` or `float`, and never `new BigDecimal(double)`.
- Compare amounts with `compareTo`, never `equals`.
- Never round user input. Reject it if it has more decimals than the currency allows.
- Round only once: the final total, `HALF_EVEN`, to the target currency's decimals. Breakdown values
  are rounded separately for display and never added up.
- Check the 15-integer-digit limit before calling `setScale` or doing any arithmetic.

### Time
- Inject a `java.time.Clock`. Never call `LocalDate.now()` or `Instant.now()` without it.
- The business time zone is `Europe/Prague`, never the system default.

### Concurrency
- All wallet changes, and the copy of balances used for reads, happen under one lock.
- Never do I/O (CNB calls) while holding the wallet lock.
- At most one CNB request runs at a time.

### CNB
- Use the English TXT endpoint:
  `https://www.cnb.cz/en/financial-markets/foreign-exchange-market/central-bank-exchange-rate-fixing/central-bank-exchange-rate-fixing/daily.txt?date=DD.MM.YYYY`
- The first line has the table date (e.g. `30 Oct 2026 #209`). Use that date, not the requested date.
  CNB answers any date with HTTP 200 and the latest table on or before it, so only this line shows
  which table you got.
- Always use the `Amount` column. The decimal separator is `.`.
- CZK is simply 1. Never look up a rate for it or expect one from CNB.
- Test fixtures must be real files downloaded from CNB. Never invent rates or file contents.

### Errors
- Use Spring's `ProblemDetail` with the `type` values and status codes from the README's error table.

## Code style

- Records for DTOs and value objects. Constructor injection with Lombok's `@RequiredArgsConstructor`,
  and `@Slf4j` for logging. Use Lombok only where it removes real boilerplate.
- Small classes, clear names. No comments that repeat the code. A short comment is welcome where a
  non-obvious decision is made; reference the README decision number (e.g. "see README decision 4").
- No interfaces with a single implementation, except `ExchangeRateProvider`.

## Tests

- JUnit 5 and AssertJ. Test packages mirror the main packages. Run `./mvnw test` before saying a stage
  is done. All tests must pass.
- Required tests: amount validation (decimals, limits, JPY, XDR), conversion math including JPY per
  100 units, the 100-thread withdrawal test, the holiday calendar checked against CNB's holiday list,
  the parser against a real CNB file, the caching rules with a fake clock (no CNB request on
  weekends, holidays or before 13:30 when the cache holds the newest possible table; a table missed
  on a quiet day is fetched; checks every 10 minutes from 13:30 until today's table arrives; a 1-minute
  wait after a failure with nothing cached; provisional; stale; too old; CNB down at startup), and
  MockMvc tests for the assignment's workflow and every error type.

## Workflow

- Work in the stages I give you. At the end of each stage, run the tests, summarize what you did in
  a few lines, list anything you were unsure about, and stop for my review.
- Do not commit. I review and commit each stage myself.
