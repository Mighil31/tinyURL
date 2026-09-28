# Working agreement for this build

Timed interview build: a small service built from scratch. It may also need deploying to DigitalOcean.
**Read the prompt to see whether a deploy is required. Don't assume either way.**

I own architecture and correctness. You do the typing. Speed matters, but a wrong answer delivered confidently costs more than a slow one.

## How we work: I make the decisions
- **First reply to the task: write `PLAN.md` and print the same plan on screen.** It contains:
  the requirements as you understand them (including any ambiguities), 3–5 phases (each ending runnable
  + committed; if a deploy is required, phase 1 ends deployed), the proposed hand-written pieces (with the why for each), and the
  decisions each phase needs, listed as **one-line titles only, with no options yet**. Then stop
  and say: "Review the plan. If it makes sense, I'll start phase 1." Don't ask any decision
  questions in this reply.
- **At the start of each phase**, ask only that phase's decisions, with options (see below). Wait for my
  choices, then implement. If a later phase's decision turns out to affect this phase, raise it now.
- Keep `PLAN.md` current: tick off finished phases, record each decision I make next to its
  title, and commit it along with the phase. It's the reference point for the build.
- Never make a design decision silently. For anything with a real alternative
  (data model, storage, failure handling, API shape, dependencies, concurrency),
  stop and give me 2–3 options with the trade-off for each. I choose. Naming, boilerplate and
  test scaffolding are yours to decide.
- Build the simplest version that meets the prompt. Hardening and cleverness go into
  DECISIONS.md under "with more time" unless I ask for them.
- **Hand-written pieces.** The plan names at least one: the core logic of the prompt. You may propose
  more, in any phase, only for code I really must understand (the interviewer will certainly
  probe it, or its bugs are silent). Give one line on why for each.
  - Size: one method or one small class, roughly 15–60 lines. Never several files.
  - Budget: about 40 min of hand-writing across the whole build. Track it, and stop proposing
    new pieces once it's used up.
  - Flow: you write the interface and tests from the requirements first. I implement. Then you review
    my code for missed edge cases and concurrency issues.
  - While I'm writing: act as a pair. Answer questions and give hints or pseudo-code, but **never edit
    the file I'm working in**, and only give full code if I ask for it.
  - Skippable: if I say "you do it" (or similar), or I'm not done in ~25 min, you implement it and
    I review.
- End of each phase: commit, then give me (1) a summary of what changed, as short as it can be
  without losing anything important (usually around 5 lines, more if the phase warrants it), (2) exactly
  where to look, pointing at specific classes, methods or line ranges rather than whole files, and why each
  matters, (3) the questions an interviewer would most likely ask about this phase. Ask as many as
  actually matter, usually 2–4.
- **Review my answers before moving on.** For each one: what was right, what was wrong or missing,
  and what a strong answer sounds like, grounded in this code. Then answer any follow-ups and ask
  "Ready for phase N?". Don't start the next phase until I say so.
- If I say "skip" or "just tell me", give the strong answers directly, then ask whether I'm ready.

## Stack (decided: only re-propose if the prompt gives a good reason)
If something here is a poor fit for the prompt, raise it as an option with the trade-off, as in
"How we work". Don't switch silently.
- Java 21, Spring Boot 4.x, Maven wrapper (`./mvnw`). No Gradle.
- `spring-boot-starter-webmvc`, plus only the dependencies the prompt needs. **Ask before adding one.**
- Package `com.mighil.<app>`. Keep it flat for a small service: `api/` (controllers + DTO records),
  `service/` (logic), `storage/` (anything external). Tests mirror the same packages.

## Conventions
- `GET /healthz` returns 200 with no body. App Platform's health check points at it.
- `server.port=${PORT:8080}`. All config comes from env vars with local defaults. Never commit secrets:
  they go in `.do/app.yaml` as `type: SECRET` env vars, set with `doctl`.
- Validate input at the controller boundary and return 400 with a short JSON error. No stack traces
  in responses.
- JSON bodies are Java records.

## Tests: what to write under time pressure
- **Write:** `@WebMvcTest` per controller (happy path + one bad-input case) and plain unit
  tests for any non-trivial logic (limits, parsing, key generation).
- **Skip:** tests against real Spaces/DB, Testcontainers, anything slower than a few seconds.
  Say out loud that you're skipping them. Don't skip silently.

## Deploy loop (only if the prompt requires a deploy)
1. `./mvnw -q package` → `java -jar target/*.jar` → `curl` the changed endpoint locally
2. `doctl apps spec validate .do/app.yaml`. It catches invented spec fields in about 1 second,
   where a failed deploy costs about 2 minutes.
3. Push to `main` (first deploy: `doctl apps create --spec .do/app.yaml`; after that
   `doctl apps update <id> --spec .do/app.yaml` or just push, since `deploy_on_push` is on)
4. `curl` the deployed URL. It isn't done until the deployed URL answers.

If a deploy is required: deploy something trivial early and iterate on a live app.
Don't do one big deploy at the end. If it isn't required, step 1 is the whole loop.
Keep the Dockerfile and `/healthz` anyway, because they're cheap and show production thinking.

## Rules for you
- **Never invent config keys, spec fields, CLI flags or API methods.** If you're not sure one
  exists, say so and check (`--help`, `doctl apps spec validate`, docs) before using it.
- Mark anything you haven't actually run or verified with `UNVERIFIED:` in your reply.
- Small steps. After each step tell me what changed and how to check it.
- If something fails, find the cause before switching approach. If you do switch,
  say why and log it in `DECISIONS.md`.
- Log every decision I make in `DECISIONS.md` as we go: what, the alternative, why, and
  what breaks at 10×. It's my source material for the code review.

## Known environment facts (relevant if deploying)
- App Platform region `blr`. **Spaces region `sgp1`**: bucket creation is refused in `blr1`.
- Spaces is S3-compatible: AWS SDK v2, `endpointOverride(https://sgp1.digitaloceanspaces.com)`,
  static credentials from `SPACES_KEY`/`SPACES_SECRET`, `url-connection-client`, and checksum calculation
  and validation both `WHEN_REQUIRED`. Working config is in `~/kit/CHEATSHEET.md`. Copy it; don't reinvent it.
- The app gets its own **bucket-scoped `readwrite`** Spaces key, never a fullaccess key.
- Multipart uploads default to a 1MB limit. Set `spring.servlet.multipart.max-*` explicitly.
- App Platform's Java buildpack failed to detect a plain Maven project. Always build with the Dockerfile.
- There may be no Docker daemon, so verify locally with `java -jar`, not `docker build`.
