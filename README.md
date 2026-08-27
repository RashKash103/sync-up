# 🔄🧩 Sync Up

> [!WARNING]
> **This project has been primarily driven by AI. Use it with caution.**
> The only patches that have been tested are the ones I use myself; everything
> else is unverified. Review the code before you run it, and expect rough edges.

[![Add to Morphe](https://img.shields.io/github/v/release/RashKash103/sync-up?sort=semver&display_name=tag&label=Add%20to%20Morphe&labelColor=1f1f26&color=6750a4&style=for-the-badge)](https://morphe.software/add-source?github=RashKash103/sync-up)

Morphe patches for [Sync for Reddit](https://play.google.com/store/apps/details?id=com.laurencedawson.reddit_sync).

Click the badge above to add this patch source to Morphe Manager. It always shows the
current release.

## ❓ About

Sync for Reddit was discontinued after Reddit's 2023 API pricing changes. Sync Up is a
Morphe compatible patch bundle that brings it back: it patches the app to use your own
OAuth client credentials and fixes a handful of things that broke along the way.

Sync Up started as a port of the Sync for Reddit patches from
[Patcheddit](https://github.com/wchill/patcheddit) by [wchill](https://github.com/wchill).
The initial import is a faithful copy of those patches, with the patches for the other
third party Reddit clients (Boost, RIF, Relay, BaconReader, Joey, Infinity+, Continuum,
Slide) and for Sync for Lemmy left out. Improvements specific to Sync for Reddit are made
here on top of that baseline.

If you use one of those other apps, use [Patcheddit](https://github.com/wchill/patcheddit)
directly — it is the upstream project and it supports all of them.

### How to use these patches

Add this patch source to Morphe Manager: https://morphe.software/add-source?github=RashKash103/sync-up

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.2.0-dev.8](https://github.com/RashKash103/sync-up/releases/tag/v1.2.0-dev.8)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;15 patches total
<details open>
<summary>📦 com.laurencedawson.reddit_sync&nbsp;&nbsp;•&nbsp;&nbsp;14 patches</summary>
<br>

**🎯 Supported versions:**

| v23.06.30-13:39 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Add archive links to the post menu](#add-archive-links-to-the-post-menu) | Adds Wayback Machine and archive.today options to the menu behind a post's overflow button, next to "Open in browser". Useful for reading a page that has since been taken down or put behind a paywall. |  |
| [Automatically undelete Imgur images](#automatically-undelete-imgur-images) | Loads Imgur images that no longer exist from the Wayback Machine. Imgur removed a large amount of older content, so links in old posts often fail. Only images the archive happens to hold can be recovered. |  |
| [Automatically undelete Reddit content](#automatically-undelete-reddit-content) | Restores the text of removed posts and comments from Project Arctic Shift. Restored text is marked to show why it was taken down. Only text can be recovered, and only where the archive has it. |  |
| [Disable Sync for Lemmy bottom sheet](#disable-sync-for-lemmy-bottom-sheet) | Disables the bottom sheet at the startup that asks you to signup to "Sync for Lemmy". |  |
| [Disable ads](#disable-ads) |  |  |
| [Fix /s/ links](#fix-s-links) |  |  |
| [Fix Imgur links](#fix-imgur-links) | Sync resolves Imgur links through a proxy of its own that no longer exists, so they fail to load. Answers those requests locally instead. Album contents are read from an archived copy of the album page, which only works for albums the archive captured while Imgur still rendered them. |  |
| [Fix Redgifs API](#fix-redgifs-api) |  |  |
| [Fix post thumbnails](#fix-post-thumbnails) | Fixes loading post thumbnails by correcting their URLs. |  |
| [Fix video downloads](#fix-video-downloads) | Fixes a bug in Sync's MPD parser resulting in only the audio-track being saved. |  |
| [Modify login WebView](#modify-login-webview) | Modify the WebView used for logging into reddit to prevent login issues |  |
| [Redirect Gfycat links to RedGifs](#redirect-gfycat-links-to-redgifs) | Answers Gfycat requests from RedGifs, which hosts much of the content that moved there before Gfycat shut down. Gfycat's domains no longer resolve, so without this every Gfycat link fails to load. |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |
| [Use /user/ endpoint](#use-user-endpoint) | Replaces the deprecated endpoint for viewing user profiles /u with /user, that used to fix a bug. |  |

</details>

<details open>
<summary>📦 com.laurencedawson.reddit_sync.pro&nbsp;&nbsp;•&nbsp;&nbsp;13 patches</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Add archive links to the post menu](#add-archive-links-to-the-post-menu) | Adds Wayback Machine and archive.today options to the menu behind a post's overflow button, next to "Open in browser". Useful for reading a page that has since been taken down or put behind a paywall. |  |
| [Automatically undelete Imgur images](#automatically-undelete-imgur-images) | Loads Imgur images that no longer exist from the Wayback Machine. Imgur removed a large amount of older content, so links in old posts often fail. Only images the archive happens to hold can be recovered. |  |
| [Automatically undelete Reddit content](#automatically-undelete-reddit-content) | Restores the text of removed posts and comments from Project Arctic Shift. Restored text is marked to show why it was taken down. Only text can be recovered, and only where the archive has it. |  |
| [Disable Sync for Lemmy bottom sheet](#disable-sync-for-lemmy-bottom-sheet) | Disables the bottom sheet at the startup that asks you to signup to "Sync for Lemmy". |  |
| [Fix /s/ links](#fix-s-links) |  |  |
| [Fix Imgur links](#fix-imgur-links) | Sync resolves Imgur links through a proxy of its own that no longer exists, so they fail to load. Answers those requests locally instead. Album contents are read from an archived copy of the album page, which only works for albums the archive captured while Imgur still rendered them. |  |
| [Fix Redgifs API](#fix-redgifs-api) |  |  |
| [Fix post thumbnails](#fix-post-thumbnails) | Fixes loading post thumbnails by correcting their URLs. |  |
| [Fix video downloads](#fix-video-downloads) | Fixes a bug in Sync's MPD parser resulting in only the audio-track being saved. |  |
| [Modify login WebView](#modify-login-webview) | Modify the WebView used for logging into reddit to prevent login issues |  |
| [Redirect Gfycat links to RedGifs](#redirect-gfycat-links-to-redgifs) | Answers Gfycat requests from RedGifs, which hosts much of the content that moved there before Gfycat shut down. Gfycat's domains no longer resolve, so without this every Gfycat link fails to load. |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |
| [Use /user/ endpoint](#use-user-endpoint) | Replaces the deprecated endpoint for viewing user profiles /u with /user, that used to fix a bug. |  |

</details>

<details open>
<summary>📦 com.laurencedawson.reddit_sync.dev&nbsp;&nbsp;•&nbsp;&nbsp;13 patches</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Add archive links to the post menu](#add-archive-links-to-the-post-menu) | Adds Wayback Machine and archive.today options to the menu behind a post's overflow button, next to "Open in browser". Useful for reading a page that has since been taken down or put behind a paywall. |  |
| [Automatically undelete Imgur images](#automatically-undelete-imgur-images) | Loads Imgur images that no longer exist from the Wayback Machine. Imgur removed a large amount of older content, so links in old posts often fail. Only images the archive happens to hold can be recovered. |  |
| [Automatically undelete Reddit content](#automatically-undelete-reddit-content) | Restores the text of removed posts and comments from Project Arctic Shift. Restored text is marked to show why it was taken down. Only text can be recovered, and only where the archive has it. |  |
| [Disable Sync for Lemmy bottom sheet](#disable-sync-for-lemmy-bottom-sheet) | Disables the bottom sheet at the startup that asks you to signup to "Sync for Lemmy". |  |
| [Fix /s/ links](#fix-s-links) |  |  |
| [Fix Imgur links](#fix-imgur-links) | Sync resolves Imgur links through a proxy of its own that no longer exists, so they fail to load. Answers those requests locally instead. Album contents are read from an archived copy of the album page, which only works for albums the archive captured while Imgur still rendered them. |  |
| [Fix Redgifs API](#fix-redgifs-api) |  |  |
| [Fix post thumbnails](#fix-post-thumbnails) | Fixes loading post thumbnails by correcting their URLs. |  |
| [Fix video downloads](#fix-video-downloads) | Fixes a bug in Sync's MPD parser resulting in only the audio-track being saved. |  |
| [Modify login WebView](#modify-login-webview) | Modify the WebView used for logging into reddit to prevent login issues |  |
| [Redirect Gfycat links to RedGifs](#redirect-gfycat-links-to-redgifs) | Answers Gfycat requests from RedGifs, which hosts much of the content that moved there before Gfycat shut down. Gfycat's domains no longer resolve, so without this every Gfycat link fails to load. |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |
| [Use /user/ endpoint](#use-user-endpoint) | Replaces the deprecated endpoint for viewing user profiles /u with /user, that used to fix a bug. |  |

</details>

<details open>
<summary>🌐 Universal&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Enable Android debugging](#enable-android-debugging) | Enables Android developer debugging capabilities. Including this patch can slow down the app. |  |

</details>

<!-- PATCHES_END -->

## 🚀 Getting started

1. Install [Morphe Manager](https://morphe.software/) and switch it to advanced/expert mode.
2. Click [this link](https://morphe.software/add-source?github=RashKash103/sync-up) to add
   this patch source to Morphe Manager.
3. Get the APK for Sync for Reddit. APKM bundles (aka split APKs) also work.
   * Use the last released version, `v23.06.30-13:39`. The Pro and Dev package names are
     supported too, but no specific version of them is verified.
4. Select the patches you want, then patch.
   * You will need an OAuth client ID from https://www.reddit.com/prefs/apps/. Reddit is no
     longer issuing new ones without an approval process that usually ends in denial, so see
     [What if I don't have a client ID?](#what-if-i-dont-have-a-client-id) below.
   * Make sure the redirect URI in the `Spoof client` options and on
     https://www.reddit.com/prefs/apps/ match exactly.
5. Once patching is complete, install the app and set it up as usual.

### What if I don't have a client ID?

You can use the client ID from another working third party Reddit app (sadly not the official
one). RedReader is the one to use: they have a deal with Reddit to use the API for free for
accessibility reasons. The same steps work with other apps, but then that app's developer pays
for the API calls you make, which is not recommended.

1. Install RedReader from the Play Store and log in with it.
2. After you log in you should get an email titled `You've authorized a new app in your Reddit
   account`. Look for `App ID:` in that email and note the random looking string.
   * You can uninstall RedReader afterwards.
3. In Morphe Manager, set these values for the `Spoof client` patch. Make sure there are no
   extra spaces or trailing slashes.
   * `OAuth client ID` — the random looking string from the email.
   * `Redirect URI` — `redreader://rr_oauth_redir` (or the matching value if you took the
     client ID from a different app).
   * `User agent` — `org.quantumbadger.redreader/1.25.1`. Ignore the description that says
     what the user agent format should be.
4. Select your other patches and patch as normal.

The client ID is not shipped inside the patch on purpose: it would end up scraped, revoked,
and broken for everyone.

### Resolving login problems

**I cannot select the username/password fields on the login page — my keyboard pops up and
disappears again.**
Switch your phone to landscape mode. There is a cookie banner that needs to be dismissed, but
it is bugged and only shows up in landscape.

**I get `{}` or `Error: Invalid request to Oauth API`.**
Your redirect URI is probably wrong. It has to match exactly between the patch options and
your Reddit app at https://www.reddit.com/prefs/apps/. Do not add a trailing `/`.

**Reddit says my username/password are invalid even though they are correct.**
Reddit does not like the network you are logging in from (corporate/work network, country with
banned IP ranges, etc). Try a VPN or cellular data. Restarting your phone, clearing Chrome
cookies, clearing app data for Android System WebView, or updating Android System WebView can
also help.

**I cannot hit Accept on the Authorize screen.**
Change your Reddit site language to English. This screen is reported not to work otherwise.

**I still get 403 Blocked.**
A garbage user agent eventually gets blocked by Reddit. Reddit also blocks any mention of
`rubenmayayo`, and other substrings such as `isfun` are known to trigger blocks.

**I get 401 when I open the app.**
You probably created a web app instead of an installed app. Delete it, create an installed app,
and repatch with the new client ID. Also, do not use autofill when logging into Reddit — type
or paste your password manually.

**I get 400 Bad Request while logged in.**
Log out and back in, or uninstall the app (back up your settings first) and reinstall it.

**I get a `null: null` error when I open the app.**
Your client ID is incorrect. Check that you copied it correctly.

## 🛠️ Building locally

1. Set up a GitHub PAT as described in the
   [Morphe Patcher setup docs](https://github.com/MorpheApp/morphe-patcher/blob/main/docs/2_1_setup.md#-prepare-the-environment).
   The build resolves the Morphe Gradle plugin and libraries from GitHub Packages.
2. Run `./gradlew buildAndroid` (JDK 21).
3. The bundle is written to `patches/build/libs/patches-*.mpp`. Apply it with
   [Morphe Desktop](https://github.com/MorpheApp/morphe-desktop) or Morphe Manager like any
   other patch bundle.

See the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation) for more.

### 🧪 Testing against a real APK

`test/test_all_apks.py` rebuilds the bundle, downloads the latest
[Morphe Desktop](https://github.com/MorpheApp/morphe-desktop), and patches every APK you drop into
`test/`:

```sh
cp ~/Downloads/sync-for-reddit.apk test/
uv run test/test_all_apks.py
```

Patch options are read from `test/config.json` if it exists (Morphe options file format);
without it, the patches run with their defaults, which means `Spoof client` will fail its
required `client-id` option. `test/config.json` and `test/output/` are gitignored.

## 🧑‍💻 Development

- **Make all changes on the `dev` branch.** `main` holds stable releases.
- Use [semantic commit](https://kapeli.com/cheat_sheets/Semantic_Commits.docset/Contents/Resources/Documents/index)
  messages. In practice three types are enough:
  - `feat: Added a new feature` — minor release
  - `fix: Some problem now fixed` — patch release
  - `chore: Change you do not want in the user facing changelog` — no release
- `fix:` and `feat:` commits on `dev` publish a pre-release automatically. Users can opt into
  those by enabling `pre-release` for this source in Morphe Manager.
- When `dev` is ready for a stable release, merge it into `main` (merge, do not squash).
- Releases are handled entirely by [release.yml](.github/workflows/release.yml) and
  [.releaserc](.releaserc). Do not create releases by hand.
- Never edit the generated files by hand: `CHANGELOG.md`, `patches-list.json`,
  `patches-bundle.json`, and the patches list section of this README.

[CLAUDE.md](CLAUDE.md) documents the repository layout and conventions in more detail.

### 📙 Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## 📜 License

Sync Up is licensed under the [GNU General Public License v3.0](LICENSE).

Sync Up is a derivative work of Patcheddit, which is itself derived from Morphe. Both carry
additional conditions under GPLv3 Section 7 that are inherited by this project and reproduced
in full in the [NOTICE](NOTICE) file:

- **Attribution (7b).** Any application or derivative work that uses this code must display an
  easily accessible, user-facing message with the exact wording:

  > This app uses code from Patcheddit. To learn more, visit https://reddit.com/r/patcheddit

  and

  > This app uses code from Morphe. To learn more, visit http://morphe.software

- **Name restriction (7c).** The names **"Patcheddit"** and **"Morphe"** may not be used as the
  identity of derivative works. Sync Up is an independent project and is not authored by,
  affiliated with, or endorsed by either project; it is merely compatible with Morphe and
  derived from Patcheddit.

Sync Up adds no further conditions of its own under Section 7.

## 🙏 Credits

- [wchill](https://github.com/wchill) and the Patcheddit contributors, whose Sync for Reddit
  patches this project is built from.
- The [Morphe](https://github.com/MorpheApp) project, for the patcher, the patch library and
  the tooling.
- [ReVanced](https://github.com/ReVanced), whose prior work both of the above build on.
