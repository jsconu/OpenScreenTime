# Getting OpenScreenTime into F-Droid

Everything needed to submit these apps, checked against
[fdroiddata's CONTRIBUTING.md](https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md)
and the [inclusion policy](https://f-droid.org/docs/Inclusion_Policy/).

**Two apps means two of everything** — two metadata files, **two branches**, two merge requests.
fdroiddata's guidelines are explicit: *"Keep a separate branch for every app you want to submit."*

## What's here

- `org.openscreentime.parent.yml`, `org.openscreentime.kid.yml` — build recipes, ready to copy into
  `metadata/` in a fdroiddata fork
- `rfp-parent.md`, `rfp-kid.md` — text for the merge request description, or for an RFP issue

Descriptions, screenshots and changelogs are **not** here. They live where F-Droid reads them
automatically, in each app's own source tree:

    parent/src/main/fastlane/metadata/android/en-US/
    kid/src/main/fastlane/metadata/android/en-US/

## Which way to submit

1. **Merge request to fdroiddata** — fork
   [fdroiddata](https://gitlab.com/fdroid/fdroiddata), add the file, open the merge request. This is
   the documented path and the faster one, because a reviewer gets something they can build.
2. **RFP** — an issue at <https://gitlab.com/fdroid/rfp/-/issues>. Their CONTRIBUTING suggests this
   for *first-time* contributors. It works, but it is a request queue rather than a review queue.

Either needs a GitLab account.

## Doing it on the GitLab website, per their steps

For **each** app, separately:

1. Fork <https://gitlab.com/fdroid/fdroiddata> (once; it is a large repository and takes a minute).
2. In your fork, create a new branch named after the application id — `org.openscreentime.parent`
   for the first one. **Do not commit to `master`**; it is protected, and a merge request cannot be
   opened from it.
3. Go to the `metadata` directory, use the **+** button, choose **New file**.
4. Name it `<application id>.yml` — exactly `org.openscreentime.parent.yml`.
5. Paste the recipe from this directory. Copy from GitHub's **Raw** view: YAML is unforgiving about
   indentation and the rendered view can introduce stray characters.
6. Commit to your new branch.
7. **Go to CI/CD in your fork and wait for the pipeline to pass.** fdroiddata lints every metadata
   file, and this is where a mistake shows up. Fix and re-commit until it is green.
8. Open the merge request against `fdroid/fdroiddata`, and fill in their template.

Then repeat from step 2 for `org.openscreentime.kid`, on its own branch.

## Does it qualify?

| Requirement | Status |
| --- | --- |
| Free software licence | **Yes** — MIT, `LICENSE` at the repository root |
| Full source available | **Yes** — tagged `v0.2.0-beta` |
| Builds from source with free tools | **Yes** — Gradle, AGP, Kotlin, AndroidX, Compose |
| No proprietary libraries in the build | **Yes for the local flavour** — see below |
| No tracking, ads or analytics | **Yes** — none in either flavour |
| Works without a proprietary network service | **Yes** — the local build talks to no server at all |
| Version tagged in git | **Yes** — `v0.2.0-beta`, `versionCode 2` |
| No bundled binaries | **Yes** — no `gradle-wrapper.jar` is checked in |
| Valid category | **Yes** — `Time Tracker`, from `config/categories.yml` |

### The one thing a reviewer will ask about

`gradle/libs.versions.toml` and `cloud/build.gradle.kts` declare Firebase and Play Services
coordinates, for the **cloud** flavour — the one people compile themselves to pair two phones
through their own Firebase project. F-Droid's scanner reads gradle files rather than build output,
so it will see those coordinates and stop.

They are genuinely not in the built artefact:

    unzip -p parent-local-release.apk 'classes*.dex' | grep -ac 'com/google/firebase'

prints `0`. The recipes carry `scanignore` for exactly those two files, and nothing else, with a
comment explaining why. If a reviewer would rather not carry the exception, the alternative is to
move the cloud flavour to a branch of its own — worth offering rather than waiting to be asked.

### Declared rather than discovered

- **Accessibility service**, to know which app is in the foreground. Never reads screen contents.
- **Cloudflare DNS**, when the optional website filter is on, and only then.
- **Device admin** (kid app), for optional uninstall protection a parent turns on deliberately.
- **Beta** — no independent security audit, limited coverage across manufacturers.

### Why AutoUpdateMode is None

The local flavour used to append `-local` to versionName, so no git tag could be derived from it.
That suffix has been removed for future releases; once a tag matches its versionName exactly, these
files can move to:

    AutoUpdateMode: Version v%v
    UpdateCheckMode: Tags

## After it is accepted

**F-Droid signs with its own key, not ours.** Anyone who installed an APK from the GitHub releases
page cannot upgrade in place to the F-Droid build — Android refuses an update signed by a different
key. They would have to uninstall first, which loses the app's data.

Put that in the README and the release notes before an F-Droid build goes live, so nobody finds out
the hard way. [Reproducible builds](https://f-droid.org/docs/Reproducible_Builds/) would let F-Droid
ship our own signature instead, and are worth looking at later.
