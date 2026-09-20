# OpenScreenTime: domain language

Terms used in the code and docs, so the two apps and their modules name things the same way.

**Parent app / kid app.** The parent app runs on an adult's phone; the kid app runs on a child's phone and is paired to a parent's account. The parent app can also track the parent's own phone (the **self profile**).

**Profile.** A person's settings and state, stored as a `ChildProfile` under the parent's account: limits, bedtime, lock, blocked sites, tracking choices, Focus choice. A child's profile and the parent's self profile share one shape.

**Device profile.** This phone's saved copy of its person's profile (`DeviceProfileState`; `LiveChildState` on a kid phone, `SelfDeviceState` on a parent phone). Read by everything that has to work without waiting for the network.

**Foreground guard.** The accessibility service that watches which app is in front (`BaseAppLimitAccessibilityService`). It attributes time, sends non-allowed apps home under Focus mode, and enforces limits, bedtime and locks. The decisions come from `decideEnforcement`, a pure function.

**Focus mode ("dumb phone").** A parent-chosen switch for a child or for the parent's own phone that keeps calls, texts, contacts, sign-in-code apps and chosen apps, and sends everything else back to a plain home screen (`FocusMode`). An adult's own phone also has a **Travel** profile and an **All apps** window; a child's phone needs a parent's passcode (**Parent unlock**).

**Calm list.** A plain, read-only, local list of today's notifications on one phone, never uploaded. **Hide other notifications** (parent app) takes everything but calls, alarms, sign-in codes and other essentials out of the shade and into the calm list (`shouldHideNotification`).

**Family passcode.** A 4 to 6 digit code a parent sets, stored as a salted hash. It is a deterrent, not a security boundary.

**Own phone.** The phone that is tracking itself: the parent app on the parent's phone.
