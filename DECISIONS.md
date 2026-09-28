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

## Hand-written core
- Piece:
- Written by me / finished by Claude (why):
- What Claude's review found:

## With more time
- Split `/healthz` into `/health/live` (no deps) and `/health/ready` (pings DB). Liveness must never depend on the DB.
- CI gate: Dockerfile builds with `-DskipTests`, so failing tests still deploy. Add a GitHub Action running `./mvnw verify`, deploy only on green.
- Rollback: `git revert` + push is a full rebuild (~4 min); redeploying the previous App Platform build is faster.
- URL validation uses `java.net.URI`, which rejects internationalized hosts (`https://例子.测试`) and hosts with `_`. Normalize via `IDN.toASCII` first.
- Least-privilege DB user instead of `doadmin`; restrict DB trusted sources to the app.

## Not verified / skipped
- Skipped: repository tests against a real Postgres (Testcontainers). Instead verified by hand: local Postgres 18 in Docker, create → redirect → restart → redirect still works.
- Removed the generated `@SpringBootTest` contextLoads test: it needs a live DB.

## Budget (say it out loud at minute 0)
~15 min plan · ~120 min build in phases (incl. ~25 min hand-written core) · ~25 min deploy + verify · ~20 min final read-through (`git log -p`, DECISIONS.md closed)
