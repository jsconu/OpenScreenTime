# Security policy

OpenScreenTime can see how a child uses their phone, so a bug here is a bug about a
family's private life. Reports are welcome and taken seriously.

## Reporting a vulnerability

Use GitHub's private vulnerability reporting:
**[Report a vulnerability](https://github.com/jsconu/OpenScreenTime/security/advisories/new)**
(the Security tab of this repository). That opens a private thread with the maintainer —
please don't open a public issue for something exploitable.

There's no bounty, and no SLA beyond a person reading it as soon as they can. If a report
is valid you'll be credited in the advisory unless you'd rather not be.

## What's in scope

The boundaries this project actually tries to enforce, and would consider a vulnerability
to break:

- **One family's data from another's.** No account, device or pairing code should ever
  reach a different parent's children, stats or account document.
- **A kid device touching only its own child profile.** A paired device gets its own
  Firestore credentials; it must not be able to read or write a sibling's profile, list
  the children collection, or read the parent's passcode verifier.
- **Pairing codes.** Six digits is a small space. Codes are readable only while live,
  claimable once, and expire in 30 minutes; anything that widens that window, makes the
  code space enumerable, or lets a stranger claim a child is in scope.
- **The apps themselves.** Anything that leaks usage data off the device beyond what
  [docs/PRIVACY.md](docs/PRIVACY.md) describes, or that a non-parent can trigger remotely.

## What's already known, and not a vulnerability

These are documented trade-offs, explained at the top of
[`firebase/firestore.rules`](firebase/firestore.rules). Please don't spend your time on
them:

- **The family passcode is a deterrent, not a boundary.** Firestore rules can't run
  PBKDF2, so the "parent mode" rule has no passcode check. Whoever can extract a paired
  device's token can write that child's own limits, lock state and bedtime. A kid with
  physical access can also just turn off the accessibility service or uninstall the app.
  Closing this needs a server, which this project deliberately doesn't have.
- **A paired kid device can read its own child document,** including the passcode hash
  and salt denormalized onto it. A 4–6 digit passcode is therefore guessable offline.
- **Self-hosted deployments are the operator's responsibility.** Every build talks to
  whoever published it's own Firebase project. If a fork's rules are misconfigured, that's
  a bug in that deployment — though if this repo's rules or docs led them there, that
  *is* worth reporting.

## Supported versions

This is a beta with no independent security audit. Only `main` is supported: fixes land
there, and there are no backports to older builds. If you're running a fork, check your
deployed `firestore.rules` against this repository's — an app newer than its deployed
rules has caused real breakage before.
