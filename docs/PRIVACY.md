# OpenScreenTime Privacy Policy

*Draft - fill in the bracketed items, have it read over, and host it at a public URL (for example
GitHub Pages) before submitting to Google Play. Last updated: [DATE].*

OpenScreenTime is a free, open-source screen-time app for families: a **parent app** and a **kid
app** (Android), plus a parent app for iPhone. It is meant to support a conversation about healthy
screen time, not to watch someone in secret. This policy explains what it collects, where that goes,
and how to have it deleted.

**Who runs this service:** [YOUR NAME OR ORGANIZATION], the person who operates the Firebase project
this build connects to. Contact: [CONTACT EMAIL].

## What the apps collect

**On a child's phone (kid app)**
- Which app is in front, and for how long, per app, per day (app name and package name).
- How many times the phone was unlocked each day.
- Only if a parent turns the matching option on: which app was opened first after each unlock, and
  how many notifications each app sent (counts only).
- Settings a parent chooses: the child's display name, daily and per-app limits, bedtime window,
  blocked website domains, always-allowed apps, and phone numbers allowed to call during bedtime.
- An anonymous device identifier created by Firebase Authentication, used to pair the phone with a
  parent.

**On a parent's phone (parent app)**
- The parent's email address and a password handled by Firebase Authentication.
- The same per-app time and unlock information for the parent's own phone, only if the parent turns
  on tracking their own device.
- A family passcode, stored only as a salted hash.
- Feedback a parent chooses to send (the text, app version and device model).

**On any device, for stability**
- Crash reports (stack traces, device model, Android version, app version) sent to Firebase
  Crashlytics.

## What the apps do **not** collect

- No screen content, keystrokes, passwords, messages, photos, contacts list, or location.
- The Accessibility service only learns **which app is in front**; it is configured not to read
  window content.
- Notification **content** is never uploaded. An optional notification list on a child's phone stays
  on that phone.
- The website filter checks website *names* on the device against the parent's blocked list; it does
  not record or upload browsing history. Allowed lookups are passed to a public DNS resolver
  (Cloudflare, 1.1.1.1) as they would be without the filter.
- No advertising, no analytics beyond crash reports, and no selling or sharing of data.

## Where the data goes

To Google Firebase (Authentication, Firestore, Crashlytics) in a project operated by the service
operator named above. Firebase is provided by Google; see Google's privacy policy for how they
process it. Data is protected in transit, and access is restricted by security rules to the family
that created it. This is a community project and has **not had an independent security audit**. The
family passcode is a deterrent against casual changes, not a security boundary against a determined,
technically skilled user with access to the child's phone.

## Children

The kid app is used by children, but only after a parent has installed it, granted its permissions,
and paired it. The child does not create an account or provide personal details; a parent enters
the child's display name. Parents can see the child's usage; children see only a simple calm
status indicator unless a parent chooses to show more. We do not knowingly collect information from
children outside that parent-controlled setup. [ADD ANY REGION-SPECIFIC WORDING YOU NEED, e.g.
COPPA/GDPR-K, after checking the laws that apply to you.]

## Retention and deletion

Usage is kept per day so the app can show a four-week report. A parent can delete a child profile in
the app. To have **all** of a family's data removed, email [CONTACT EMAIL] from the parent's account
email and it will be deleted (the account, the child profiles and their daily usage). Uninstalling the
apps stops all collection on that phone.

## Your choices

- Parents control every optional category and can switch it off at any time.
- Turning off the Accessibility service, the notification access, or the website filter in Android
  settings stops the matching collection.
- Unpair a kid phone from the parent's account at any time.

## Changes

If this policy changes, the new version will be posted at this address with an updated date.

## Contact

[CONTACT EMAIL]
