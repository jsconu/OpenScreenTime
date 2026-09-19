# Release (R8) rules for the parent app. Firebase, WorkManager, Compose and kotlinx-serialization
# each ship their own consumer rules, so this file only holds what's specific to this project.

# Readable crash reports: Crashlytics maps these back through the uploaded mapping file.
-keepattributes SourceFile,LineNumberTable,*Annotation*
-renamesourcefileattribute SourceFile

# Classes Android instantiates by name from the manifest or from WorkManager's stored class name.
-keep class org.openscreentime.** extends android.app.Application { *; }
-keep class org.openscreentime.** extends androidx.work.ListenableWorker { *; }
-keep class org.openscreentime.** extends android.accessibilityservice.AccessibilityService { *; }
-keep class org.openscreentime.** extends android.net.VpnService { *; }
-keep class org.openscreentime.** extends android.service.notification.NotificationListenerService { *; }
-keep class org.openscreentime.** extends android.telecom.CallScreeningService { *; }
-keep class org.openscreentime.** extends android.telecom.CallRedirectionService { *; }
-keep class org.openscreentime.** extends android.app.admin.DeviceAdminReceiver { *; }
-keep class org.openscreentime.** extends android.content.BroadcastReceiver { *; }

# Shared model + help-bot classes are (de)serialized by name (kotlinx-serialization and the
# Firestore field maps).
-keep class org.openscreentime.shared.model.** { *; }
-keepclassmembers class org.openscreentime.shared.** { *** Companion; }
-keep class kotlinx.serialization.** { *; }
