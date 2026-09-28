# PLAN: Link Shortener API

**Deploy required: yes.** "Deploy it on DigitalOcean so it's reachable over the public internet" + "a public URL we can curl".

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

### Phase 2: Create + redirect, persisted
`POST /links` (no auth yet), `GET /{code}`, URL validation, storage wired locally and on App Platform. Ends deployed.
Decisions:
- [ ] Persistent storage choice
- [ ] Short-code generation scheme (and collision handling)
- [ ] URL validation strictness
- [ ] Redirect status code
- [ ] Duplicate long URL behaviour
- [ ] Request/response shape of `POST /links`

### Phase 3: API keys + rate limiting (**hand-written core: the rate limiter**)
Key seeding, `X-API-Key` check on creation, per-key tiered limiter, 429 with retry info. Ends deployed.
Decisions:
- [ ] How keys and tiers are seeded and stored
- [ ] Auth failure responses (missing vs unknown key)
- [ ] Rate-limit algorithm
- [ ] Where limiter state lives (in-memory vs shared)
- [ ] How "when to retry" is communicated (headers/body)
- [ ] Whether rejected requests consume quota

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
