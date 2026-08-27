# CLAUDE.md

Guidance for Claude Code when working in this repository.

## What this is

Sync Up is a [Morphe](https://github.com/MorpheApp) patch bundle for **Sync for Reddit**
(`com.laurencedawson.reddit_sync`, plus the `.pro` and `.dev` variants). It is a derivative
of [Patcheddit](https://github.com/wchill/patcheddit), reduced to only the Sync for Reddit
patches.

Morphe is a fork of ReVanced. A patch bundle is a `.mpp` file: a JAR of Kotlin patch
definitions plus DEX'd "extension" code that gets injected into the target APK. Patches
locate methods in the obfuscated app with **fingerprints** and rewrite their smali.

## Layout

```
patches/src/main/kotlin/app/morphe/patches/
  all/misc/debugging/            Universal patch (applies to any app)
  reddit/customclients/          Reusable patch builders shared across Reddit clients
    Constants.kt                 Extension method descriptors
    SpoofClientPatch.kt          The client-id / redirect-uri / user-agent option set
    ModifyWebViewPatch.kt        Redirects WebView.loadUrl through the extension
    FixRedgifsApiPatch.kt        Enables the shared Redgifs interceptor
    FixSLinksPatch.kt            Enables the shared /s/ link resolver
    sync/                        Sync-family patches
      Constants.kt               Compatibility declarations (the supported packages)
      ads/, detection/piracy/    Shared across the Sync family; piracy detection is a
                                 dependency-only patch (it has no name, so it is not
                                 user-selectable)
      syncforreddit/             The Sync for Reddit patches themselves
patches/src/main/resources/branding-license/   License texts bundled into the .mpp
extensions/shared/library/       Java injected into every patched app (shared base classes)
extensions/shared/               Wrapper project that packages the library as an extension
extensions/syncforreddit/        Sync-specific injected Java
extensions/syncforreddit/stub/   Compile-only stubs of app classes the extension references
test/test_all_apks.py            Rebuild the bundle and patch APKs dropped into test/
.github/scripts/                 README patch list generator, run by the release
```

Each patch directory pairs a `Fingerprints.kt` (how to find the code) with the patch file
(what to do to it). Follow that split when adding patches.

Extension classes must stay under `app.morphe.extension.*` — the Morphe extensions library
provides `app.morphe.extension.shared.{Logger,Utils}` and the patches reference extension
classes by their exact smali descriptor, e.g.
`Lapp/morphe/extension/syncforreddit/FixSLinksPatch;`. Renaming a package or class means
updating the descriptor string in the corresponding patch.

`extensions/*/build.gradle.kts` files are auto-discovered by the Morphe settings plugin, which
scans `extensions/` for build files. Nested projects such as `:extensions:syncforreddit:stub`
do not need to be listed in `settings.gradle.kts`.

## Commands

| Task | Command |
| --- | --- |
| Build the bundle | `./gradlew buildAndroid` → `patches/build/libs/patches-*.mpp` |
| Regenerate the patch list | `./gradlew generatePatchesList` → `patches-list.json` |
| Patch real APKs | `uv run test/test_all_apks.py` (APKs go in `test/`) |
| Refresh the README patch list | `uv run .github/scripts/generate_patches_readme.py RashKash103/sync-up dev patches-list.json README.md` |

Building needs **JDK 21** and a GitHub PAT with `read:packages`, exported as `GITHUB_ACTOR` /
`GITHUB_TOKEN` or set as `gpr.user` / `gpr.key` in `~/.gradle/gradle.properties`; the Morphe
plugin and libraries come from GitHub Packages.

Use `uv run` for Python, not bare `python3`.

## ⚠️ Build has never been verified

The initial import was assembled without ever running Gradle — the machine it was written on
had only JDK 11 and no `read:packages` token. **Nothing here has been compiled.** The first CI
run is the first real verification. Expect to fix things there rather than assuming the tree
is sound.

Most likely first failures, in order:

1. The Morphe Gradle plugin pin `1.3.2-dev.1` in `settings.gradle.kts` is a dev prerelease
   inherited from Patcheddit. If it no longer resolves, bump it to the current stable release
   (`1.3.4` at the time of writing, which is what the official template uses).
2. A stale reference to something only the dropped clients used.

## Version pinning

`gradle/libs.versions.toml` pins Morphe Patcher `1.3.3` and morphe-patches-library
`1.0.2-dev.2`, which is what Patcheddit was last built against (April 2026). Current upstream
is far ahead — patcher `1.11.x`, library `1.6.x`. **This is deliberate**: the ported patch
sources were written against the older API, and pinning keeps them compilable.

Modernising the dependency stack is the largest known follow-up. It is an API migration, not a
version bump, and it should be done as its own change with a real build behind it. `morphe-patcher`
and `smali` are read out of the version catalog by the Gradle plugin and are not listed under
`[libraries]`, so Dependabot will not propose those bumps.

## Relationship to Patcheddit

The patch logic is a faithful port. Where this repository intentionally differs:

- Only Sync for Reddit is targeted. All other client patches, the Sync for Lemmy target, and
  the shared code only those used, are dropped.
- Identity: project name, `rootProject.name`, the bundle `about` block, and Gradle group
  (`app.syncup`) are this project's. Required by the upstream Section 7 name restrictions.
- The `Spoof client` option help text says "Sync Up documentation" rather than the upstream
  project's name.
- Release plumbing follows the current
  [morphe-patches-template](https://github.com/MorpheApp/morphe-patches-template) rather than
  Patcheddit's older setup: `.releaserc`, `release.yml`, `open_pull_request.yml`,
  `package.json`, and the README patch list generator all come from there. No GPG signing is
  configured, so `signatureUrlTemplate` is empty.
- `patches/api/patches.api`, the settings and `addresources` resource trees, and the unused
  `:patches:stub` project are not carried over. The API dump was stale upstream; the resources
  belong to settings patches this bundle does not use.
- `test/test_all_apks.py` points at `morphe-desktop` (morphe-cli was renamed), tolerates a
  missing `test/config.json`, and carries a PEP 723 header.

When porting a fix from upstream, keep the `Copyright 2026 wchill` headers on ported files —
that attribution is what the upstream license requires.

## Licensing constraints

GPLv3 with Section 7 conditions inherited from **both** Patcheddit and Morphe; the full text
is in [NOTICE](NOTICE). Two things constrain the code:

- **7c name restriction.** Do not name this project, its bundle, or its branding after
  "Patcheddit" or "Morphe". Descriptive references ("compatible with Morphe", "derived from
  Patcheddit") are fine. Note that `app.morphe.*` package names and the
  `PatchedditInterceptor` class name are inherited identifiers in ported code, not project
  identity — leave them alone.
- **7b attribution.** Both upstream notices must stay reachable: `NOTICE`, the README license
  section, and the license texts bundled into the `.mpp` under
  `patches/src/main/resources/branding-license/`.

## Conventions

- Semantic commit messages; the release version and changelog are generated from them.
  `feat:` minor, `fix:`/`bump:`/`perf:` patch, `chore:` no release.
- Work on `dev`. `main` is stable releases only, reached by merging `dev` (not squashing).
- Never hand-edit `CHANGELOG.md`, `patches-list.json`, `patches-bundle.json`, or the region
  between the `PATCHES_START` / `PATCHES_END` markers in `README.md`. The release writes them.
- Do not create releases by hand.

## Known rough edges

Inherited from upstream, deliberately left alone in the initial import:

- `ModifyWebViewPatch.kt` has a leftover `println` in its execute block.
- `Constants.kt` in `reddit/customclients/` still declares `CREATE_NEW_CLIENT_METHOD`, which
  no remaining patch uses.
- The `sync/ads/DisableAdsPatch.kt` builder plus its `syncforreddit/ads` wrapper is now an
  indirection with a single caller, since the Lemmy target is gone.
- `.github/scripts/generate_patches_readme.py` counts a patch once per compatible package, so
  the "N patches total" line reads 26 for the 10 patches in this bundle. That counting comes
  from the upstream template.
