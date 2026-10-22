# Contributing to WoW Reader Lab

Thank you for helping improve WoW Reader Lab. This repository is the current source of truth on `main` for the Android project. Do not start new work from the historical `stable/v61` line; check the current `README.md` and `NEXT_CHAT_HANDOFF.md` before making changes.

## Before you open a change

- Read `README.md`, `SIGNING.md`, and the relevant material in `docs/`.
- Keep changes focused and explain the user-facing or maintenance problem they address.
- Preserve the package identity, existing user data, update compatibility, and production signing identity. Never simplify an update with a destructive migration.
- Do not add APK/AAB files, build outputs, Gradle/IDE state, keystores, passwords, recovery material, or generated caches to Git.
- Evaluate library, storage, sync, cover, search, calendar, and reader-state changes against the **100,000+ book** scale rule. Normal use must not load or process the whole library unnecessarily.
- This repository is the original app's current official Android source. Do not modify or port to the older `whispermmepub/wow-reader-app` source line without explicit authorization.

## Making a change

1. Create a focused branch from the current `main` source.
2. Make the smallest change that addresses the issue. Preserve offline reading, import/export, notes, shelves, history, settings, and account/sync behavior unless a change explicitly concerns one of them.
3. Update documentation or tests when the behavior or contract changes. Do not add claims about device, Play, or live-service behavior that was not actually checked.
4. Review `git diff` and `git status` before submitting. Confirm that no book files, credentials, signing material, release artifacts, or unrelated local files are included.
5. Open a pull request describing the problem, the approach, compatibility/data-preservation impact, and validation.

## Validation and reporting

Use an installed **Gradle 8.11.1** and **Android SDK 36** toolchain; there is no committed Gradle wrapper. Java source compatibility is **17**; the recorded v81 preparation ran **JDK 21**. Configure the local Android SDK path for your environment. Production release signing is not needed for the debug host checks below and must not be replaced with new production keys.

From the repository root, typical checks for a code change are:

```sh
bash tools/run-contract-tests.sh
gradle --no-daemon :app:testDebugUnitTest :app:lintDebug
```

These are commands for contributors, not a claim that this documentation pass executed them. Host reader scripts may require exported fixtures and additional browser dependencies; read their command-line help and the maintainer handoff before running them. Do not fabricate a missing historical comparison baseline.

Use the repository’s documented tools and workflows rather than inventing a new command. The available host-side tools are listed under `tools/`, including `tools/run-contract-tests.sh` and the reader, publisher-layout, progressive-chapter, resource-readiness, large-EPUB, and restore benchmark scripts. The release-oriented Gradle command is documented in `NEXT_CHAT_HANDOFF.md`; signing instructions and secret handling are in `SIGNING.md`.

In a pull request, report **only checks you actually ran**. Identify the execution boundary for each result:

- **Host:** unit tests, contract tests, Python tools, or desktop/Chromium harnesses run on the development machine.
- **Device/emulator:** Android installation, rendering, memory, touch, or instrumentation checks run on a named device or emulator.
- **Live service:** Google sign-in, Drive restore/sync, or Play Console/production observations.

Host results do not prove device behavior, and device results do not prove live-service or Play behavior. If you did not run a category, say so. Do not claim that a build, install, update, large-library run, original EPUB, live account, or Play release was verified when it was not.

Do not run or request a release build merely to report a documentation change. Never expose signing variables or private signing material in logs, issues, pull requests, or public files.

## Licensing and third-party material

Project-owned source is available under the [PolyForm Noncommercial License 1.0.0](LICENSE), a source-available license that is **not OSI-approved**. Contributions must remain compatible with that repository license and its terms; public availability does not grant unrestricted commercial-use rights.

Third-party code, dependencies, fonts, dictionaries, and other assets retain their own licenses, notices, attribution, and reuse conditions. They are not relicensed under PolyForm by being present here. Check the applicable upstream notice before adding or redistributing material; in particular, see the dictionary notices linked from `README.md`.

## Reporting problems or proposing features

Use the issue forms for reproducible bugs and feature proposals. Remove or redact private data before posting. Never attach book files, account tokens, signing secrets, keystores, passwords, private recovery material, or unsanitized logs. If a log may contain paths, account identifiers, document titles, tokens, or book content, sanitize it first.

Please be patient and constructive. Maintainers may ask for a minimal reproduction or a narrower diagnostic that protects user data and preserves the project’s update, storage, and signing contracts.
