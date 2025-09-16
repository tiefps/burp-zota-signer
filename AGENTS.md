# Repository Guidelines

## Project Structure & Module Organization
- Source: `src/main/java/` organized under `burp.zota.*`.
  - `burp.zota` — entrypoint `ZotaExtension` (registers UI tab, HTTP handler).
  - `burp.zota.signer` — endpoint-specific signing (`ZotaSigner`).
  - `burp.zota.profile` — profile model + persistence (`ProfileManager`, `ZotaProfile`).
  - `burp.zota.ui` — Swing settings tab and context menu helpers (`ZotaSettingsTab`, `menu/*`).
  - `burp.zota.util` — helpers (`SignatureUtil`, `QueryString`, `ZotaLogger`).
- Build output: `build/libs/` (shadow JAR excludes Montoya API).

## Build, Test, and Development Commands
- `./gradlew clean build` — compile + produce sources/javadoc jars (Java 21).
- `./gradlew clean shadowJar` — plugin JAR for Burp at `build/libs/*.jar` (no Montoya bundled).
- `./gradlew test` — run unit tests (none present yet).
- Dev setup: JDK 21, open as a Gradle project in IntelliJ.

## Release Process
- **Automatic releases**: Push to `main` creates GitHub release with auto-generated tag from `build.gradle` version.
- **Manual releases**: Push tag like `v1.0.0` to trigger release workflow.
- **CI/CD**: GitHub Actions workflow builds shadow JAR and publishes to GitHub Releases.
- **Versioning**: Update `version` in `build.gradle` before pushing to main for automatic releases.

## Coding Style & Naming Conventions
- Java 21, UTF‑8, 4‑space indentation; keep lines readable (~120 chars).
- Packages lowercase; classes `PascalCase`; methods/fields `camelCase`; constants `UPPER_SNAKE_CASE`.
- Keep `montoya-api` as `compileOnly`; do not log secrets from profiles.
- Match existing package layout; keep utilities side‑effect free.

## Testing Guidelines
- Place tests in `src/test/java/` mirroring packages; name classes `*Test`.
- Recommended: JUnit 5. Example Gradle snippet:
  - `testImplementation 'org.junit.jupiter:junit-jupiter:5.10.0'`
- Focus on signer logic and query parsing; run with `./gradlew test`.

## Commit & Pull Request Guidelines
- Use Conventional Commits (`feat:`, `fix:`, `docs:`, `refactor:`, `chore:`).
  - Example: `feat(signer): add exchange-rates signature`.
- PRs: clear description (what/why/how), linked issues, screenshots for UI changes, and sample request/response if behavior changes. Update `README.md` when user-facing behavior or build commands change.

## Security & Configuration Tips
- Profiles and toggles persist in Burp project storage; never commit credentials.
- Shadow JAR config already excludes Montoya; keep this behavior.
- Prefer stage API base during development.

## Agent-Specific Instructions
- Keep changes minimal and scoped; avoid unrelated refactors or reformatting.
- Follow existing naming and module layout; avoid moving packages.
- Prefer {@code java.net.URI} over deprecated {@code java.net.URL} constructors.
- Update this file when adding modules, commands, or workflows.
