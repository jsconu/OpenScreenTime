# Draft answers for Google Play Console

Paste-ready drafts for the **App content** forms and the sensitive-permission declarations. They
describe what the code in this repo actually does (checked against the manifests and services), but
Play's forms change - read each question, and correct anything that no longer matches your build.
Answer honestly; a mismatch between a declaration and behavior is what gets apps suspended.

Both apps: `org.openscreentime.kid` (child's phone) and `org.openscreentime.parent`.

## Store listing text (short and full description, honest framing)

> OpenScreenTime is a free, open-source screen-time app for families. A parent sets daily and
> per-app limits, a bedtime and blocked websites; the child's phone shows a calm reminder and a
> block screen when time is up. Children see only a simple status icon - detailed reports are for
> parents. **This is a beta.** An iPhone parent app is built but not yet in the App Store, and there
> is no iPhone kid app yet (a future project the community is welcome to take on).
>
> It needs several sensitive permissions (see below) and a parent's Firebase account.

Do not describe it as a way to monitor someone secretly. The app is designed to be visible to the
person using the phone (persistent notification, in-app explanation of what's tracked).

## Target audience and content

- **Kid app:** children use it, but a parent installs, configures and pairs it. If the form asks
  whether the app is designed for children, answer per the current definition: the *child* never
  signs up or gives personal details; a *parent* does. Read Play's **Families policy** before
  choosing "children" as a target age group, because that triggers extra requirements (approved SDKs
  only, no ads, etc. - Firebase Auth/Firestore matter here; there is no crash-reporting SDK). The conservative option is
  to publish the **parent app** first and target it at adults.
- **Parent app:** target audience is adults (parents/guardians).
- **Ads:** none.
- **App category:** Parenting.

## Data safety

Collected data is transmitted to a Firebase project operated by you; it is not sold or shared with
third parties for their own purposes. (Firebase acts as your service provider - Play generally treats
that as "not shared".) Data is encrypted in transit. Users can request deletion (see
[`PRIVACY.md`](PRIVACY.md)).

| Data type | Collected? | Purpose | Required or optional |
|---|---|---|---|
| Email address (parent) | Yes | Account management | Required (parent app) |
| Name (child's display name, entered by parent) | Yes | App functionality | Required |
| User IDs (Firebase anonymous / account uid) | Yes | Account management, app functionality | Required |
| App activity - apps in use and time per app | Yes | App functionality, analytics for the parent's report | Required (core feature) |
| App activity - **installed apps** (name and package of each app with a launcher icon, from the kid's phone) | Yes | App functionality (lets a parent set a limit on any app) | Required (kid app) |
| App activity - unlock counts | Yes | App functionality | Required |
| App activity - first app after unlock, notification counts per app | Yes | App functionality | **Optional** (parent turns on) |
| Web browsing history - **site names only** (domains looked up while a browser is open, as counts) | Yes | App functionality | **Optional** (parent turns on "Track websites") |
| Phone numbers allowed during bedtime | Yes | App functionality | Optional |
| Crash logs and diagnostics | Yes | App stability | Required (not user-configurable) |
| Feedback text (free text a parent sends) | Yes | App functionality | Optional |

*(verify: Play's form lists "Installed apps" as its own data type under App activity - it's the one to
tick for the row above. The parent app reads its own phone's installed apps only on the device, to build
the limits screen, and doesn't upload them, so it collects nothing extra for that.)*

Not collected: location, contacts list, messages/SMS/call history, photos, audio, files, health data,
financial info, precise device identifiers for advertising, full web addresses/URLs, searches or page content.
Only when a parent turns on "Track websites" are site NAMES (domains) counted - see the table. Notification content
is never uploaded (the calm notification list on either app stays on that phone).

Security practices: data encrypted in transit; you can request data be deleted. Do **not** claim an
independent security review - there has been none.

## Sensitive permission and API declarations

Each answer should say *why* the permission is core to the app's purpose, and what it does not do.

### Accessibility service (kid app and parent app)
- **Core purpose:** parental controls - knowing which app is in the foreground, to count time per app
  and show a reminder or block screen when a parent-set limit is reached.
- **What it does not do:** it does not read screen content (`canRetrieveWindowContent="false"`), does
  not capture keystrokes, passwords or text, does not perform actions on the user's behalf, and does not
  send anything about window content anywhere. Only the foreground app name is used.
- **In-app disclosure:** shown before the user is sent to Accessibility settings (`AccessibilityDisclosureDialog`),
  with an explicit "I agree - continue".
- **Video:** Play may ask for a short video demonstrating the disclosure and the feature; record the
  kid app flow: disclosure -> Settings -> service on -> a limit reached -> block screen.

### VPN service (kid app)
- **Purpose:** a *local* DNS-filter so a parent can block chosen websites in any browser. It runs on
  the device and receives only the device's DNS lookups (not web traffic): a lookup for a parent's
  blocked domain gets a blocked answer, and every other lookup is forwarded to a public resolver
  (1.1.1.1). It does not inspect traffic content and does not route web traffic through any remote server.
  It records nothing about lookups unless a parent turns on the optional "Track websites" setting, in which
  case it counts site names (domains only, never URLs) looked up while a browser is open and uploads those
  counts to the parent's account. The parent app runs the same filter on the parent's own phone, only if the
  parent allows the VPN prompt from the Permissions screen. Declared as a foreground service of type special use.

### Device administration (kid app)
- **Purpose:** an optional "uninstall protection" deterrent - the child must get a parent's approval to
  remove the app. Opt-in from the passcode-protected Parent controls screen. It does not lock, wipe or
  control the device. It is a deterrent, not an unremovable lock.

### Call screening / call redirection (kid app, Android 10+)
- **Purpose:** optional "bedtime calls" - during the bedtime a parent set, reject incoming and outgoing
  calls except to numbers the parent listed (for example a parent's own number). Nothing about calls
  is uploaded. Off unless a parent grants the role. *(verify: Play restricts call-related permissions;
  this uses the Call Screening and Call Redirection roles, not READ_CALL_LOG/SMS permissions, so it
  should not fall under the SMS/Call Log declaration - but confirm in the current policy.)*

### Notification listener (kid app and parent app)
- **Purpose:** optional. (1) Counting how many notifications each app sends, counts only, when a parent
  turns that on; (2) quieting text-message notifications from unlisted numbers during bedtime; (3) a
  local "calm notification list". Notification content is never uploaded.

### Home screen role and "Dumb phone" (kid app and parent app)
- **Purpose:** an optional parent-chosen Focus mode. When on, the app offers a plain home screen (registered as a
  HOME activity, disabled until the mode is turned on, and only used if the user makes it the default home app from
  the system's own prompt) listing calls, texts, contacts, authenticator apps and the apps a parent allowed. The
  Accessibility service, which already reports the foreground app, sends non-allowed apps back to the home screen.
  On a child's phone opening everything needs the family passcode; on a parent's own phone an "All apps" button opens
  everything for 10 minutes. Nothing about this is uploaded beyond the on/off choice and the allowed-app list.
- **Calm notifications ("Hide other notifications"):** uses the Notification listener to remove other apps'
  notifications from the shade, keeping them on the device in a local list; calls, alarms, navigation and system
  messages, texts and sign-in-code apps are excluded. A Quick Settings tile opens that list.

### Display over other apps (SYSTEM_ALERT_WINDOW)
- **Purpose:** to show the block screen when a limit is reached, on top of the limited app.

### Foreground service (special use)
- **Purpose:** the visible "screen time monitoring" and "website filter" notifications, so it's always
  clear to the user that these are running. Subtype text is in the manifest.

### Ignore battery optimizations (REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
- **Purpose:** keeps the monitoring service from being killed so limits are enforced reliably. Requested
  through the standard system prompt, only from the setup checklist.

### Package visibility (app list)
- **QUERY_ALL_PACKAGES is not declared.** The apps use a narrow `<queries>` entry for the launcher
  intent, which shows only apps that have a launcher icon. The kid app uses it to list what's on the
  child's phone (and send that list to the parent's account); the parent app uses it to list its own
  phone's apps on the limits screen. Purpose: parental controls - setting a limit on any installed app.

### Not used
- Usage-stats access, SMS, call log, contacts, location, camera, microphone: not requested.
- QUERY_ALL_PACKAGES: not declared (see "Package visibility" above).

## Content rating (IARC questionnaire)
Utility / parenting app: no user-generated content, no violence, no purchases, no location sharing,
no interaction between users. Expect an "Everyone" rating.

## Government / financial / health / news declarations
None apply.
