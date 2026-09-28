# Decisions

One entry per decision I make. Write it when I make it, not at the end.

| # | Decision | Rejected alternative | Why | What breaks at 10× / what I'd change with more time |
|---|---|---|---|---|
| 1 | Spring Boot 4 + Maven | Javalin, Gradle | Muscle memory. Framework overhead measured at under 1 min. apt Gradle is too old | — |
| 2 | App Platform, Dockerfile build | Buildpack, Droplet | Buildpack failed detection on a plain Maven project. A Dockerfile is deterministic. A Droplet needs manual provisioning | — |
| 3 | Public GitHub repo, App Platform deploy_on_push | Private repo; DOCR image push | Repo is a deliverable anyway; no extra GitHub-app auth; no local Docker daemon needed | Nothing at 10× load; for a real team: private repo + CI gate before deploy |
| 4 | Single service `web`, 1 instance (0.5GB) | 2+ instances from day one | Cheapest; keeps an in-memory rate limiter correct | Single point of failure; >1 instance needs shared limiter state (Redis/DB) |
| 5 | DO Managed Postgres (blr1, smallest node) | App Platform dev DB; Spaces object-per-code | Unique constraint on code + atomic click increments; container FS is ephemeral | One small node: no HA, ~25 conns. At 10× add a standby, PgBouncer, read replica or cache for redirects |
| 6 | Random base62, 7 chars, retry on unique violation | Base62 of sequence id; truncated hash | 3.5e12 space, not enumerable, no coordination | Collision retries only matter near saturation; bump length. Hot-path insert is one round trip |
| 7 | URL validation: http/https scheme, non-empty host, ≤2048 chars (java.net.URI) | Block private/loopback hosts; prefix check | Rejects malformed and non-web schemes without DNS | No phishing/malware or SSRF-style host checks; add blocklist/Safe Browsing lookup |
| 8 | 302 redirect | 301; 307 | Uncached, so every click reaches us and is counted | Every click hits the app+DB; at 10× cache code→URL in memory/CDN |
| 9 | Deps: starter-jdbc + postgresql + Flyway | schema.sql init; Spring Data JPA | JdbcClient is explicit SQL; Flyway gives migration history for Phase 3/4 columns | — |
| 10 | DB creds via `databases:` attachment + bindable vars in app.yaml | Manual SECRET envs | No secrets handled by hand | Uses `doadmin`; production would use a least-privilege DB user |
| 11 | Duplicate long URL → new code every time | Reuse per key; reuse globally | No lookup on create, per-link stats | Table grows with duplicates; dedupe per key if storage matters |
| 12 | `POST /links` {url} → 201 + Location, {code, shortUrl, longUrl}; base from `BASE_URL` | 200 {code, shortUrl} | REST-correct, echoes the normalized URL | — |
| 13 | API keys from `API_KEYS` SECRET env (`key:tier,...`), parsed into memory at startup | DB table of hashed keys; plaintext in repo | Public repo: no key material committed; zero lookups | Key rotation = redeploy; no per-key metadata. At 10× tenants: DB table + cache, hashed keys |
| 14 | Sliding window log limiter (exact N per any 60s) | Fixed window (2× burst at boundary); token bucket (~2N−1 in a window); sliding counter (approx.) | Exact semantics, Retry-After = oldest + 60s − now; ≤100 timestamps/key | Memory O(limit) per key; fine for 100/min, not for 10k/min (then token bucket or sliding counter) |
| 15 | Limiter state in-memory per instance | Postgres; Redis | Zero latency, no deps; correct with instance_count 1 | Resets on redeploy; >1 instance multiplies the effective limit. At 10×: Redis (sorted set / Lua) |
| 16 | 429 + `Retry-After` (whole seconds, rounded up) + `{error, retryAfterSeconds}` | Also RateLimit-* headers on every response | Standard, exactly what the prompt asks | Clients can't pace proactively; add RateLimit headers |
| 17 | 401 for missing and unknown key, with `WWW-Authenticate` | 401 missing / 403 unknown | Doesn't reveal which keys exist | — |
| 18 | Quota consumed by every authenticated request that isn't rate-limited (400s count, 429s don't) | Only successful creations (reserve/refund) | One atomic tryAcquire, no overshoot race; denied attempts can't cause permanent lockout | Clients with buggy input burn quota |

## Hand-written core
- Piece: `SlidingWindowRateLimiter.tryAcquire` (sliding window log, per-key, injected Clock)
- Written by me / finished by Claude (why): **Written by Claude.** Claude wrote the interface + 11 spec tests first; I then asked Claude to implement it rather than doing the 25-min attempt myself. I review it instead.
- Edge cases handled (for my review): clock read *inside* the per-key lock so each deque stays sorted under concurrency; expiry at exactly `t + window`; denied attempts not recorded; limit lowered below current log size → retryAfter waits for the (size−limit+1)-th oldest entry, not the oldest (test added: `loweredLimitWaitsUntilEnoughEntriesExpire`).
- Known limits: uses wall clock (`Clock.systemUTC`), so an NTP step backwards can stretch a window (a monotonic `System.nanoTime` source fixes it); the per-key map is never evicted (bounded by the seeded keys, since limiting runs only after auth).

## With more time
- Split `/healthz` into `/health/live` (no deps) and `/health/ready` (pings DB). Liveness must never depend on the DB.
- CI gate: Dockerfile builds with `-DskipTests`, so failing tests still deploy. Add a GitHub Action running `./mvnw verify`, deploy only on green.
- Rollback: `git revert` + push is a full rebuild (~4 min); redeploying the previous App Platform build is faster.
- URL validation uses `java.net.URI`, which rejects internationalized hosts (`https://例子.测试`) and hosts with `_`. Normalize via `IDN.toASCII` first.
- Least-privilege DB user instead of `doadmin`; restrict DB trusted sources to the app.

## Not verified / skipped
- Skipped: repository tests against a real Postgres (Testcontainers). Instead verified by hand: local Postgres 18 in Docker, create → redirect → restart → redirect still works.
- Removed the generated `@SpringBootTest` contextLoads test: it needs a live DB.
- `doctl apps spec validate` rejects specs containing encrypted (`EV[...]`) secrets ("must not be encrypted before app is created"), so `.do/app.yaml` is now checked with `--schema-only`; full validation happens in `doctl apps update` against the real app.
- API keys: generated with `openssl rand -hex 16`, pushed once via a temp spec outside the repo, then the `EV[...]` ciphertext DO returned was committed. Plaintext lives only in the local scratchpad; handed to reviewers out of band.

## Budget (say it out loud at minute 0)
~15 min plan · ~120 min build in phases (incl. ~25 min hand-written core) · ~25 min deploy + verify · ~20 min final read-through (`git log -p`, DECISIONS.md closed)
