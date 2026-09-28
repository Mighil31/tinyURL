# PLAN: Link Shortener API

**Deploy required: yes.** "Deploy it on DigitalOcean so it's reachable over the public internet" + "a public URL we can curl".

- Live: https://tinyurl-2w4px.ondigitalocean.app (App Platform app `222fa8ca-da0e-4ae4-b5fd-8df6b38aa3d3`)
- Repo: https://github.com/Mighil31/tinyURL
- Postgres: managed cluster `tinyurl-db` (`16f7bce1-3710-4b93-99c3-2862c7977a15`), blr1, db-s-1vcpu-1gb (~$0.0225/h)

### Teardown by EOD 2026-09-28 (deleting the app does NOT delete the DB) ✅ done 2026-09-28
- [x] `doctl apps delete 222fa8ca-da0e-4ae4-b5fd-8df6b38aa3d3`
- [x] `doctl databases delete 16f7bce1-3710-4b93-99c3-2862c7977a15`
- [x] `doctl apps list` and `doctl databases list` are both empty of tinyurl

Budget (3h): ~15 min plan · ~120 min build in phases (incl. ~25 min hand-written core) · ~25 min deploy + verify · ~20 min final read-through.

## Requirements as I understand them
1. `POST /links` with `X-API-Key`: body has a long URL; returns `{code, shortUrl}`.
2. `GET /{code}`: public redirect to the original URL. Unknown code returns 404.
3. `GET /links/{code}/stats`: at least the total click count.
4. API key required to create links. Keys are seeded (no management endpoints). Each key has a tier: free or pro.
5. Rate limit on **creation only**, per key: free 10/min, pro 100/min. Over the limit returns 429, and the caller can tell when to retry (e.g. `Retry-After`).
6. Links persist across redeploy/restart. The App Platform container filesystem is ephemeral, so storage has to live outside the container.
7. Reject anything that isn't a valid http/https URL with 400.
8. Deliverable: public URL + repo, plus the design/trade-off story (DECISIONS.md).

### Ambiguities (to resolve in the phase that needs them)
- Is `/links/{code}/stats` public, or only for the key that created the link? The prompt only says redirects are public.
- Missing or unknown API key: 401 or 403?
- Same long URL submitted twice: new code each time, or return the existing one?
- How strict is "valid http/https"? Host required? Reject localhost/private IPs (open-redirect/SSRF-ish concerns)?
- Base for `shortUrl`: the deployed App Platform URL, configured by env var.
- Does the rate limiter need to be correct across multiple instances, or is one instance acceptable for the prototype?
- Should a rejected (400) creation count against the rate limit?
- Should clicks from bots/HEAD requests count? (Probably out of scope.)

## Phases

### Phase 1: Skeleton, deployed ✅
Spring Boot app, `GET /healthz`, Dockerfile, `.do/app.yaml`, git repo; it **ends deployed**, with the public URL answering `/healthz`.
Decisions:
- [x] Repo hosting and deploy source for App Platform → **public GitHub repo `Mighil31/tinyURL`, deploy_on_push**
- [x] App name / component layout in `app.yaml` → **app `tinyurl`, one service `web`, 1 instance, apps-s-1vcpu-0.5gb, blr**

### Phase 2: Create + redirect, persisted ✅
`POST /links` (no auth yet), `GET /{code}`, URL validation, storage wired locally and on App Platform. Ends deployed.
Decisions:
- [x] Persistent storage choice → **DO Managed Postgres; jdbc + postgresql + Flyway; creds via `databases:` attachment**
- [x] Short-code generation scheme (and collision handling) → **random base62, 7 chars, retry on unique violation**
- [x] URL validation strictness → **http/https + non-empty host + ≤2048 chars**
- [x] Redirect status code → **302**
- [x] Duplicate long URL behaviour → **new code every time**
- [x] Request/response shape of `POST /links` → **{url} → 201 + Location, {code, shortUrl, longUrl}**

### Phase 3: API keys + rate limiting (**hand-written core: the rate limiter**) ✅
Key seeding, `X-API-Key` check on creation, per-key tiered limiter, 429 with retry info. Ends deployed.
Decisions:
- [x] How keys and tiers are seeded and stored → **`API_KEYS` SECRET env var, parsed at startup**
- [x] Auth failure responses (missing vs unknown key) → **401 for both**
- [x] Rate-limit algorithm → **sliding window log**
- [x] Where limiter state lives (in-memory vs shared) → **in-memory, per instance**
- [x] How "when to retry" is communicated (headers/body) → **429 + Retry-After + JSON body**
- [x] Whether rejected requests consume quota → **every authenticated non-429 request counts**

### Phase 4: Click stats + wrap-up
Count clicks on redirect, `GET /links/{code}/stats`, final deploy, DECISIONS.md "with more time" closed out.
Decisions:
- [ ] Click-count write path (sync increment vs buffered)
- [ ] Stats visibility (public vs owner key)
- [ ] Stats fields beyond total clicks

## Hand-written phase
**Phase 3: the rate limiter.** It's small, pure (inject a clock), full of subtle edges (window boundaries,
retry-after arithmetic, tier lookup, concurrent requests on the same key), and testable in isolation.
I write the interface + tests first, you implement (~25 min), then I review for edge cases and concurrency.
