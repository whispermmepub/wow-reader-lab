# WoW Reader Lab

This repository is the experimental integration track for WoW Reader. The production repository remains separate.

## Integration rules

- Preserve offline EPUB/PDF reading and existing reader behavior.
- Preserve Google account sign-in, Drive appDataFolder backup/restore, and auto sync.
- Add new features in isolated feature branches and require Lab CI to compile and lint before merging.
- Treat HandyReader as a product/architecture reference only. Do not copy GPLv3 implementation code into WoW Reader.
- Prefer local-first data stored in the existing reader state so it participates in the current backup pipeline.

## Implemented / in progress

- Reading statistics foundation: local reading time, daily totals, current/longest streaks, active days, Home summary UI.
- Smart Library: reading-state filters and custom shelves/collections (in progress).
