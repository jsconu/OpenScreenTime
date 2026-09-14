# Contributing to OpenScreenTime

Thanks for considering it — this project only stays free and good if people
chip in over time. A few ground rules to keep it easy to maintain:

## Philosophy

The whole point of this app is to be simpler than the alternatives. Before
adding a feature, ask whether it earns its place on the kid's status screen
or the parent's dashboard. Prefer removing a setting to adding one. If
something needs a paragraph of explanation to use, it's probably too
complicated.

## Getting set up

Follow the [Building](README.md#building) section of the README — you'll
need your own free Firebase project to run and test against, since there's
no shared backend.

## Making a change

1. Open an issue first for anything non-trivial (new features, permission
   changes, data model changes), so we can agree on the approach before you
   put time into it. Small fixes and bug reports can just be a PR.
2. Keep PRs focused — one change per PR is much easier to review than a
   grab-bag.
3. If you touch `shared/`, remember both apps depend on it — check both
   still build.
4. If you change `firebase/firestore.rules`, explain *why* in the PR
   description; these rules are the only thing standing between one
   family's data and another's, so they get extra scrutiny.
5. Match the existing code style (plain Kotlin + Jetpack Compose, no extra
   architecture layers beyond what's already there).

## Reporting bugs

Please include: your Android version, which app (kid or parent), and steps
to reproduce. If it's a tracking/limit-enforcement bug, note the device
manufacturer — background execution and accessibility service behavior
varies a lot between OEMs (Samsung, Xiaomi, etc. are known to be aggressive
about killing background services; see if it happens on stock Android too).

## Code of conduct

Be respectful. This is a project built by parents, for parents — assume good
faith, and keep discussion focused on the software.
