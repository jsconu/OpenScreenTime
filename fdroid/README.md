# Getting OpenScreenTime into F-Droid

Everything needed to submit these apps to [F-Droid](https://f-droid.org), and an honest account of
what will be asked about.

Two apps means **two of everything**: `org.openscreentime.parent` and `org.openscreentime.kid` are
separate packages, separate metadata files and separate submissions.

## What's here

- `org.openscreentime.parent.yml`, `org.openscreentime.kid.yml` — build recipes, ready to copy into
  the `metadata/` directory of a [fdroiddata](https://gitlab.com/fdroid/fdroiddata) fork
- `rfp-parent.md`, `rfp-kid.md` — the text to paste if you file a request on the
  [RFP tracker](https://gitlab.com/fdroid/rfp/-/issues) instead

Descriptions, screenshots and changelogs are **not** here. They live where F-Droid reads them
automatically, in each app's own source tree:

    parent/src/main/fastlane/metadata/android/en-US/
    kid/src/main/fastlane/metadata/android/en-US/

Edit them there and F-Droid picks the changes up on the next build.

## Which way to submit

F-Droid has two front doors, and as the app's author you are pointed at the second:

1. **RFP (Request For Packaging)** — the tracker at <https://gitlab.com/fdroid/rfp/-/issues>. It is
   meant for *users asking for an app they did not write*. A developer may file one, and it will be
   handled, but it sits in a long queue.
2. **A merge request to fdroiddata** — fork
   [fdroiddata](https://gitlab.com/fdroid/fdroiddata), add the two files from this directory under
   `metadata/`, and open a merge request. This is what F-Droid asks developers to do, and it is
   considerably faster because the reviewer has something concrete to run.

Both need a GitLab account, which has to be created by a person.

## Does it qualify?

Checked against F-Droid's [inclusion policy](https://f-droid.org/docs/Inclusion_Policy/):

| Requirement | Status |
| --- | --- |
| Free software licence | **Yes** — MIT, `LICENSE` at the repository root |
| Full source available | **Yes** — this repository, tagged `v0.2.0-beta` |
| Builds from source with free tools | **Yes** — Gradle, AGP, Kotlin, AndroidX, Compose |
| No proprietary libraries in the build | **Yes for the local flavour** — see below |
| No tracking, ads or analytics | **Yes** — none in either flavour |
| Works without a proprietary network service | **Yes** — the local build talks to no server at all |
| Version tagged in git | **Yes** — `v0.2.0-beta`, `versionCode 2` |
| No bundled binaries | **Yes** — no `gradle-wrapper.jar` is checked in |

### The one thing a reviewer will ask about

The repository declares Firebase and Play Services coordinates in `gradle/libs.versions.toml` and
`cloud/build.gradle.kts`, for the **cloud** flavour — the one people compile themselves to pair two
phones through their own Firebase project. F-Droid's scanner reads gradle files rather than build
output, so it will see those coordinates and stop.

They are genuinely not in the built artefact. The `:cloud` module is a dependency of the cloud
flavour only, so building `local` resolves none of them, and the result can be checked:

    unzip -p parent-local-release.apk 'classes*.dex' | grep -ac 'com/google/firebase'

prints `0`. The recipes here carry `scanignore` entries for exactly those two files, and nothing
else, with a comment explaining why.

### Things worth declaring rather than being caught on

- **Accessibility service.** Both apps use one to know which app is in the foreground. That is how
  they count time and show the block screen. They never read screen contents.
- **Cloudflare DNS.** The optional website filter answers the phone's DNS queries and forwards
  anything it does not block to `1.1.1.1`. It is off unless a parent turns it on, and the apps work
  fully without it.
- **Device admin (kid app).** Used only for optional uninstall protection, which a parent turns on
  deliberately.
- **Beta.** Feature-complete and used daily on real phones, but with no independent security audit
  and limited coverage across manufacturers. Say so; F-Droid would rather know.

## After it is accepted

**F-Droid signs with its own key, not ours.** Anyone who installed an APK from the GitHub releases
page cannot upgrade in place to the F-Droid build — Android refuses an update signed by a different
key. They would have to uninstall first, which loses the app's data.

Worth putting in the README and the release notes before the F-Droid build goes live, so nobody
finds out the hard way. Reproducible builds would let F-Droid ship our own signature instead, and
are worth looking at later:
<https://f-droid.org/docs/Reproducible_Builds/>.
