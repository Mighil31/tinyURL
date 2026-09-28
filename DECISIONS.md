# Decisions

One entry per decision I make. Write it when I make it, not at the end.

| # | Decision | Rejected alternative | Why | What breaks at 10× / what I'd change with more time |
|---|---|---|---|---|
| 1 | Spring Boot 4 + Maven | Javalin, Gradle | Muscle memory. Framework overhead measured at under 1 min. apt Gradle is too old | — |
| 2 | App Platform, Dockerfile build | Buildpack, Droplet | Buildpack failed detection on a plain Maven project. A Dockerfile is deterministic. A Droplet needs manual provisioning | — |
| 3 | Public GitHub repo, App Platform deploy_on_push | Private repo; DOCR image push | Repo is a deliverable anyway; no extra GitHub-app auth; no local Docker daemon needed | Nothing at 10× load; for a real team: private repo + CI gate before deploy |
| 4 | Single service `web`, 1 instance (0.5GB) | 2+ instances from day one | Cheapest; keeps an in-memory rate limiter correct | Single point of failure; >1 instance needs shared limiter state (Redis/DB) |

## Hand-written core
- Piece:
- Written by me / finished by Claude (why):
- What Claude's review found:

## With more time
-

## Not verified / skipped
-

## Budget (say it out loud at minute 0)
~15 min plan · ~120 min build in phases (incl. ~25 min hand-written core) · ~25 min deploy + verify · ~20 min final read-through (`git log -p`, DECISIONS.md closed)
