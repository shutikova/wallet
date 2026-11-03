# AI usage

## What I used

I used Claude and Gemini in the browser before there was any code, to talk through the approach, pick the Spring
Initializr dependencies, and draft the first README from my answers to the design questions.

I then used Claude Code to create majority of the code and tests. I read each
stage before committing it, questioned its design and asked it to explain the details of its implementation.
Mine are decisions, the review and the choices about what to fix and what to leave.

## How I steered it

I answered each open question from the assignment before any code existed, and that became the
specification. `.claude/CLAUDE.md` holds the rules Claude Code has to follow, such as `BigDecimal` only, an
injected `Clock` in `Europe/Prague`, no I/O under the wallet lock, and no new dependency without asking.

I worked in stages, stopped after each one, read the code
and committed it myself. Some of the most useful changes came from plain questions, eg.:

- Removed business logic from model layer.
- `Balance` reject a wrong scale instead of quietly changing
  it.
- Removed the callback into the storage class, so CNB now can't
  be called under the wallet lock at all.
- Replaced feature packages with
  standard layers.
  
I also ran Claude Code's review between stages and treated the findings as input. The later
reviews found real bugs in its own code. A damaged CNB rate like `2.1814e3` was read as 2181.4, which would
have made totals 100 times too high with no warning, and a body with two `amount` keys deposited the last
one.

Finally, I manually tested the application with a list of edge cases, and connected the MCP server from Claude.

## Where the AI was wrong

**Rates before publication.** Its first "missing rates" decision treated Friday's table the same on a Sunday
and on a Monday morning. But CNB publishes at about 14:30 and that rate is valid for the whole day, so on
Monday morning Friday's table is only a stand-in. That's why the app has a holiday calendar and marks such
totals as `provisional`.

**Over-engineering: Easter.** Claude calculated Good Friday and Easter Monday so the calendar matched CNB
exactly. It worked, but it was extra logic and tests for two days a year, when the only effect is a wrong
`provisional` flag and the rates stay correct. I cut it and listed it as a limitation.

Smaller catches while reading: the converter expected a CNB rate for CZK, which CNB doesn't publish.
`CnbFiles` could throw a `NullPointerException` on a missing file, which its own reviews missed. And I found
untested `equals` branches in the coverage report.

## What I decided myself
- **The assignment's seven questions.** The answers are in README.md, decisions 1 to 7.
- **A real holiday calendar** instead of treating every weekday as a working day.
- **Standard layers, Lombok and one Maven module,** to match Wultra's public services.
- **Java 21 and Maven, with minimal dependencies.**
- **One wallet.** Claude suggested multiple wallets with ids, but the assignment only needs one.
- **A fixed list of currencies.** Claude suggested any ISO code, then checking the live table. With a fixed
  list, deposits never depend on CNB. The cost is a code change when CNB's list changes.
- **Daily rates only.** The monthly table covers more currencies, but its rates stay fixed for a month and
  the assignment asks for the current day. So "any currency" is only partly met, and the README says so.
- **When to call CNB API.** Application fetches whenever its table is older than the newest one that can exist.

Final thing to note is that I went over the estimated 2-3 hours required. I added a Dockerfile, basic observability capabilities and the MCP server.
