# Reading statistics foundation

WoW Reader Lab tracks active reading sessions locally and shows a compact Home summary.

Tracked values:
- reading time today
- total reading time
- per-book reading time foundation
- current reading streak
- longest reading streak
- active reading days

Sessions shorter than five seconds are ignored and a single lifecycle session is capped to guard against stale timestamps. Statistics use the existing `wow_reader` SharedPreferences state, so the current Google Drive backup exports them through `state.json` without changing Google authorization code.
