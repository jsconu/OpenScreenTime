# Installing OpenScreenTime on Android - a step-by-step guide

This guide is for someone who has never installed an app from outside the Play Store. Take it slowly; each
step says what you should see. It takes about 20-30 minutes for the first phone.

**What you're installing:** two apps.

| App | Goes on | What it does |
|---|---|---|
| **OpenScreenTime Parent** | The parent's phone | Set limits, see how much time each app uses, add your kids |
| **OpenScreenTime Kid** | The child's phone | Shows the calm status icon, shows the block screen when time is up |

You need an Android phone for each (Android 8 or newer). The parent can also be on an iPhone, but the
child's phone has to be Android for now.

> **Installed it from Google Play instead?** Then skip Part 1 and everything marked **[APK only]**. Those
> steps exist only because an app that doesn't come from the Play Store is treated more carefully by Android.

---

## Part 1 - Get the two files onto the phone **[APK only]**

You should have two files from
[the latest release](https://github.com/jsconu/OpenScreenTime/releases/latest):

- **`OpenScreenTime-Parent-0.2.0-local.apk`** - goes on the parent's phone
- **`OpenScreenTime-Kid-0.2.0-local.apk`** - goes on the child's phone

An `.apk` file is an Android app. Download both on whichever phone is easiest and move the other across,
or download each one directly on the phone it belongs on.

> **Don't have them yet?** Download them from
> [the latest release](https://github.com/jsconu/OpenScreenTime/releases/latest). Those are **local**
> builds: everything each phone records stays on that phone, there's no account and nothing to set up.
> A parent can't see or change the child's phone from their own — for that you build the **cloud**
> flavor yourself against your own Firebase project, free, in about 45 minutes
> ([SELF_HOSTING.md](SELF_HOSTING.md)). [LOCAL_AND_CLOUD.md](LOCAL_AND_CLOUD.md) compares the two.

1. On **each phone**, get the file it needs (the parent app on the parent's phone, the kid app on the kid's
   phone). Any of these works:
   - Open the download link you were sent, in the phone's browser (Chrome), and tap **Download**.
   - Or attach the file to an email or message to yourself and download the attachment on the phone.
   - Or copy it over from a computer with a USB cable (set the phone to **File transfer** when it asks).
2. If your browser says **"This type of file can harm your device. Keep anyway?"**, tap **Keep** or
   **Download anyway**. That's the phone being cautious about any app file.
3. The file is saved in the **Downloads** folder. Open the **Files** (or **My Files**) app to find it.

## Part 2 - Install it **[APK only]**

1. In **Files**, open **Downloads** and tap the app file (the **Parent** one on the parent's phone).
2. Android asks permission to install from this source. You'll see something like **"For your security, your
   phone isn't allowed to install unknown apps from this source."** Tap **Settings**.
3. Switch on **Allow from this source** (the wording varies: "Allow app installs", "Install unknown apps").
   Press the back arrow to return.
4. Tap **Install**.
5. **Google will probably warn you. That is expected, and here is exactly what it means.**

   Google Play Protect checks every app installed on an Android phone. It has never seen this one, because
   it isn't distributed through the Play Store - so it warns you. The warning is about *unfamiliarity*, not
   about anything Google found in the app. You will see one of these:

   | What you see | What to tap |
   | --- | --- |
   | "Unsafe app blocked" or "App blocked to protect your device" | **More details**, then **Install anyway** |
   | "Send app for scanning?" | **Don't send** (or **Send** - either is fine; "Don't send" means the file never leaves your phone) |
   | "This app was built for an older version of Android" | **OK** - it runs fine |
   | "Harmful app blocked" | **Stop.** That wording means Play Protect matched something it considers malicious. Don't install it, and please [open an issue](https://github.com/jsconu/OpenScreenTime/issues) - a genuine OpenScreenTime release should never produce it. |

   You do **not** need to turn Play Protect off, and you shouldn't. It carries on working normally
   afterwards.

   If you'd rather not take anyone's word for which file you have, every release lists the fingerprint of
   the key it was signed with and the checksum of each file, so you can check both before installing - see
   [the release page](https://github.com/jsconu/OpenScreenTime/releases/latest).

6. Wait for **App installed**, then tap **Open**.

Afterwards you can switch **Allow from this source** back **off** for your browser or Files app: Android
Settings > Apps > (your browser) > Install unknown apps.

---

## Part 3 - Set up the parent phone

1. Open **OpenScreenTime Parent**.
2. Tap the **Create account** tab, type your email and a password (at least 6 characters), and tap
   **Create account**. Already have an account (from another phone)? Tap **Sign in** instead.
3. On the home screen tap **Add kid**, type your child's name, and tap **Create**.
4. A **pairing code** (6 digits) appears. It's copied for you automatically. You'll type it on your
   child's phone, and it works for 30 minutes. If it runs out, tap **New code** on that kid's card on the
   home screen: it makes a fresh code and copies it for you.
5. **Set your family passcode.** A card on the home screen says **Set your family passcode**. Tap **Set
   passcode** and choose a 4-6 digit number you'll remember. This passcode locks the parent app, and it's
   what lets *you* (and not your child) open the Parent controls on the child's phone.
   - Forgot it later? On the lock screen tap **Forgot passcode?** and enter your account password.

## Part 4 - Set up the kid phone

1. Open **OpenScreenTime Kid** and type the **6-digit pairing code** from the parent's phone. Tap **Pair device**.
   The parent's screen shows the kid as paired.
2. The kid app now shows a **My screen time** tile and a button **Finish setup in Settings**. Tap it.
   You'll turn on five permissions. Each explains itself; here's what to expect.

### The one that needs extra care: Accessibility (and "restricted settings") **[APK only]**

**Accessibility** is how the app knows *which* app is open, so it can count time and show the block screen.
It never reads what's on the screen.

This is the fiddliest part of the whole setup, and it is fiddly on purpose: Android deliberately makes it
awkward to give accessibility access to an app that didn't come from the Play Store. Expect about eight taps
across three different screens. It only has to be done once per phone.

**Step by step, on the kid's phone:**

1. In OpenScreenTime Kid, tap **Fix** next to **Accessibility service**, read the box explaining what it's
   for, and tap **I agree - continue**.
2. Android's **Accessibility** settings open. Tap **Downloaded apps** (some phones say **Installed apps**,
   **Downloaded services**, or list the app directly).
3. Tap **OpenScreenTime Kid**.
4. Try the switch at the top. On Android 13 and newer it will very likely **refuse**, with a box saying
   **"Restricted setting"** - *"For your security, this setting is currently unavailable."* Tap **OK**.
   Nothing has gone wrong. Everybody hits this.
5. Now grant the exception. Two ways in, the first being quicker:

   - **From this screen:** tap the **(i)** info icon - usually top-right, sometimes beside the app's name.
     That opens the app's **App info** page.
   - **Or from scratch:** Android **Settings > Apps > OpenScreenTime Kid**.

6. On the **App info** page, tap the **three dots** in the top-right corner.
7. Tap **Allow restricted settings**. Your phone may ask for its PIN, pattern or fingerprint first.
   - *No three dots, or no such item in the menu?* Go back to step 4 and try the Accessibility switch once
     more - on many phones the option only appears after Android has refused you at least once.
8. Go **back** to **Accessibility > Downloaded apps > OpenScreenTime Kid** and turn the switch **On**.
   Confirm the box that appears.
9. Return to OpenScreenTime Kid. The **Accessibility service** row should now show a tick instead of **Fix**.

**Each app that needs it gets its own trip through this.** If you turn on **Notification access** later (for
the calm notification list, or muting texts at bedtime), it has its own identical **Allow restricted
settings** step on its own App info page. The parent app needs the same treatment on the parent's phone if
you use self-tracking or dumb phone mode there.

### The other permissions

| Row in Settings | What to tap | Why |
|---|---|---|
| **Display over other apps** | Fix, then switch on **Allow display over other apps** for OpenScreenTime Kid | Lets the block screen appear on top of the app |
| **Notifications** | Fix, then **Allow** | Shows the small status icon at the top of the screen |
| **Battery optimization** | Fix, then **Allow** (or choose **Unrestricted**) | Stops Android from switching the app off in the background |
| **Website filter** | Fix, then **OK** on the "Connection request" box | Blocks the websites a parent chose |
| **Notification access** (optional) | Only if you want the calm notification list or bedtime text muting: turn on OpenScreenTime Kid (allow restricted settings first, as above) | Not needed for limits |

The **Website filter** asks to "set up a VPN connection". That's expected: it's a small filter *on the phone*
that answers website lookups. It doesn't send your browsing anywhere.

When every row says **Granted**, the kid app's home screen says **Screen time monitoring is active.**

### Battery settings on some phones (Samsung, Xiaomi, Huawei, OnePlus, Oppo)

These phone makers add their own battery rules on top of Android's, and they can quietly switch the app off.
If limits stop working after a while, look in the phone's settings for:
- **Samsung:** Settings > Battery > Background usage limits > make sure OpenScreenTime Kid is **not** in
  "Sleeping apps" or "Deep sleeping apps".
- **Xiaomi:** Settings > Apps > Manage apps > OpenScreenTime Kid > **Autostart** on, and **Battery saver**
  set to **No restrictions**.
- **Others:** search Settings for "auto-start", "protected apps" or "app launch".

## Part 5 - Check that it works (5 minutes)

1. On the parent's phone, open your child, tap **Change daily limit**, and set it to **1 minute**.
2. On the kid's phone, open any app for a minute or two.
3. You should see the **block screen**. Tap **OK** to get back to the home screen.
4. Look at the top-left of the kid's status bar, next to the clock: a small **thumbs-up, open-hand, or stop**
   icon. Pull down from the top of the screen to see **Screen Time Status**.
5. Set the daily limit back to what you want.
6. To try the website filter, on the parent's phone open the child, add a website (for example `example.com`)
   under **Blocked websites**, then try to open it on the kid's phone.

## Dumb phone (optional): call, text and little else

A parent can turn on **Dumb phone** for a child, or for their own phone. Open the person's page in the parent app,
find **Dumb phone**, and switch it on. The phone then keeps only calling, texting, contacts, sign-in-code
(authenticator) apps and any apps you add with **Choose apps**; everything else is sent back to a plain home screen.

What has to happen on the phone itself:

1. **Accessibility** must be on (it already is if limits work) - that's what sends other apps back home.
2. Open **Settings** (kid app) or **Permissions** (parent app) and find **Home screen**. Tap **Fix** and choose
   **OpenScreenTime** as the phone's home app. Until you do, apps that aren't allowed are still sent back, but the
   simple home screen won't be the one showing.

**On a child's phone** there's a **Parent unlock** button: a parent types the family passcode and picks how long
everything opens for (it goes back by itself). Children can't open the other apps without it.

**On your own phone** there's an **All apps** button. It pauses for a few breaths, then opens every app for 10 minutes and
goes back on its own. **Travel** is a switch on the same screen: it also lets through tickets, maps, mail, the camera,
calendar, translation and ride apps (and any you add under **Travel apps**), for days when you need them.
**Turn off dumb phone** is at the bottom of the screen. OpenScreenTime itself is always in the list.

**Calm notifications, in one place:** on your own phone, switch on **Hide other notifications** on the parent home
screen (it needs notification access). Everything except calls, texts, alarms and sign-in codes is taken out of the
notification shade and collected; one quiet "Calm notifications" line tells you how many are waiting. Tap it to read them.
You can also add the **Calm notifications** tile to Quick Settings (swipe down twice, tap the pencil, drag it in),
or swipe down from the top of the dumb-phone home screen. Nothing is uploaded.

## Time that doesn't count (optional)

By default every minute the screen is on counts toward the daily limit. To leave some apps out (an audiobook or reading
app, maps, a school app), open the person's page (a kid's, or your own), find **Time that doesn't count**, and tap
**Choose apps**. Time in those apps is taken off the day's total, so they don't use up the day.

- They still show in the app list, and a limit set on one of them still applies.
- **Always allow** is different: it lets an app keep working after the daily limit or bedtime. To let an excluded app
  keep working once the daily limit is reached, tick **Always allow** for it as well.
- The screen time you see for the day is the counted figure, the same one the limit uses.
- You can also set it from **Parent controls** on the kid's phone (after the family passcode), along with Dumb phone.

## Website tracking (optional): what has to happen

Besides blocking, a parent can choose to see **which websites** a phone looked up. It's off by default, it's
per phone, and it only works when all four of these are true:

1. **The website filter is on for that phone.** On a kid's phone: the kid app's **Settings** > **Website
   filter** says **Granted**. On the parent's own phone: **Permissions** (from **My screen time**) >
   **Website filter (optional)** > **Fix**, then **OK** on the "set up a VPN connection" box.
2. **A parent turns on Track websites.** In the parent app open the child (or **My screen time** for your own
   phone), find **Optional tracking**, and switch on **Track websites**. Your child sees a note in their
   Settings that it's on.
3. **The browser uses the phone's normal lookups.** In Chrome turn **Use secure DNS** off (Settings > Privacy
   and security), in Firefox turn **DNS over HTTPS** off, and in Android Settings set **Private DNS** to **Off**
   or **Automatic**. Then close the browser completely and reopen it. A browser using its own private lookups is
   invisible to this.
4. **A browser is open.** Only lookups made while a browser is in front count; an app's own background
   activity is ignored.

**What you'll see:** on the child's screen, a **Websites looked up today** list of site names with a number
beside each, refreshed about every 15 minutes. It shows *site names only* - never page addresses, searches, or
what's on the page - and the number means *more or less activity*, not a count of visits or minutes. Sites that
are only background plumbing (ad networks, content delivery, Google/Apple services) are left out.

## If a blocked website still opens

The website filter answers the phone's own website lookups. Two settings skip that, and no app can turn
them off for you:

- **The browser's "Secure DNS".** In Chrome: Settings > Privacy and security > **Use secure DNS** > turn it
  **Off**. In Firefox: Settings > Privacy > **DNS over HTTPS** > **Off**. In Samsung Internet: Settings >
  Privacy > **Use secure DNS** > Off. Then **close the browser completely** (swipe it away) and reopen it.
- **Android's "Private DNS".** Settings > search for **Private DNS** > choose **Off** (or **Automatic**).
  The kid app's Settings page shows a red warning if it sees Private DNS switched on.

Also check that the **Website filter** row says **Granted**, and that no other VPN app is running on the phone
(Android allows only one VPN at a time).

## Troubleshooting

| What you see | What to do |
|---|---|
| "App not installed" | Uninstall any older copy of OpenScreenTime first, then install again. |
| Can't tap **Install**, or nothing happens | Redo Part 2, step 3: **Allow from this source**. |
| The accessibility switch is grey | See **Allow restricted settings** above. |
| No status icon at the top of the kid's screen | Make sure **Notifications** says Granted. On Android 13+ the icon is hidden without it. On Samsung, also check Settings > Notifications > Status bar. |
| "That code isn't active" | The code is only good for 30 minutes and only once. Tap **New code** on that kid's card on the parent's phone (it copies the new code). If a brand-new code is refused too, the Firebase rules on your project are probably out of date: publish the current `firebase/firestore.rules` (see the README's Building section). |
| The parent app shows no apps for the child | It fills in about 15 minutes after the kid phone first connects. |
| **Websites looked up today** is empty | Check all four points under **Website tracking** above - most often the website filter isn't on for that phone, or the browser's Secure DNS is on. |
| Forgot the family passcode | Parent app lock screen > **Forgot passcode?** > enter your account password. |
| Forgot the account password | Parent app > **Sign in** tab > **Forgot password?** |

## Updating, moving, and removing

- **Update:** install the newer `.apk` the same way, right on top of the old one. Your accounts and limits are
  kept.
- **Remove an app:** Android Settings > Apps > OpenScreenTime > **Uninstall**. If **uninstall protection** was
  turned on, a parent must turn it off first (Parent controls on the kid's phone).
- **A new phone for your child:** in the parent app, open the child, scroll to the bottom and tap **Remove (their name)**, add them again, and
  pair the new phone.

## Your privacy

These are **local** builds. There is no account, no server, and no cloud code in them at all - not disabled,
absent. You can check that yourself rather than take anyone's word for it:

    unzip -p OpenScreenTime-Kid-0.2.0-local.apk 'classes*.dex' | grep -ac 'com/google/firebase'

It prints `0`.

**What the apps record:** which app is in front and for how long, how often the phone is unlocked, and -
only if a parent turns them on - how many notifications arrive and which sites are looked up. They never
read what's on the screen, your messages, or your photos.

**Where it goes:** the phone it was recorded on. The only thing that ever leaves is what a linked kid's
phone sends directly to its linked parent's phone, over your own Wi-Fi, encrypted, while both are on the
same network. It passes through no server on the way, and nobody - including this project - can see it.

**One exception, worth knowing about.** If a parent turns on the **website filter** (the optional
site-blocking feature), the phone's DNS lookups go to Cloudflare's public resolver at `1.1.1.1` instead of
your network's usual one - that is how the filter decides whether a site is blocked. Those are domain names
the phone was going to look up anyway, not anything about your family, and nothing identifies you to
Cloudflare beyond your IP address. Leave the website filter off and even that doesn't happen.

There is no analytics, no advertising, no crash reporting and no telemetry of any kind in a local build.
