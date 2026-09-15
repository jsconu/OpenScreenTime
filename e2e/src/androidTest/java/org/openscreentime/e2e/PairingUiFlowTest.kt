package org.openscreentime.e2e

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val FIND_TIMEOUT_MS = 20_000L
private const val TEST_TIMEOUT_MS = 90_000L
private const val PARENT_PKG = "org.openscreentime.parent"
private const val KID_PKG = "org.openscreentime.kid"

/**
 * Drives the two real, already-installed apps through a full pairing flow via
 * UiAutomator - a single-app-under-test instrumented test can't do this, since
 * pairing genuinely spans two separate app processes.
 *
 * Requires org.openscreentime.parent and org.openscreentime.kid debug APKs to
 * already be installed on the device/emulator this runs on, both built
 * against the Firebase Local Emulator Suite (see .github/workflows/e2e.yml).
 *
 * Selectors use Modifier.testTag(...) (surfaced as a resource-id via
 * testTagsAsResourceId, set at each app's Compose root in its MainActivity)
 * rather than matching visible text, since Compose's floating labels make
 * text-based matching ambiguous once a field has been typed into.
 *
 * Note: unlike a native Android view's R.id-based resource name, Compose's
 * testTagsAsResourceId bridge surfaces the bare tag as the resource-id (e.g.
 * "auth_email"), not package-qualified ("pkg:id/auth_email") - confirmed via
 * an on-failure UiAutomator hierarchy dump. So matching uses By.res(tag)
 * (single-arg, exact string) rather than By.res(packageName, tag).
 */
@RunWith(AndroidJUnit4::class)
class PairingUiFlowTest {

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test(timeout = TEST_TIMEOUT_MS)
    fun pairKidDeviceWithParentAccount() {
        val email = "ui-e2e-${System.currentTimeMillis()}@example.com"
        val password = "testpass123"
        val childName = "UiTestChild"

        launch(PARENT_PKG)

        find(PARENT_PKG, "auth_email").text = email
        find(PARENT_PKG, "auth_password").text = password
        find(PARENT_PKG, "auth_submit").click()

        find(PARENT_PKG, "dashboard_add_child").click()
        find(PARENT_PKG, "add_child_name").text = childName
        find(PARENT_PKG, "add_child_create").click()

        val code = find(PARENT_PKG, "pairing_code_value").text
        assertTrue("Pairing code should be a 6-digit number, was '$code'", code.matches(Regex("\\d{6}")))
        find(PARENT_PKG, "pairing_code_done").click()

        launch(KID_PKG)
        find(KID_PKG, "pairing_code_input").text = code
        find(KID_PKG, "pairing_submit").click()

        val greeting = find(KID_PKG, "status_greeting").text
        assertTrue("Kid app should greet the paired child by name, was '$greeting'", greeting.contains(childName))

        // Back on the parent app, the live Firestore listener should now show this
        // child as paired - the lock toggle only renders once child.paired == true.
        launch(PARENT_PKG)
        find(PARENT_PKG, "dashboard_child_lock_toggle")
    }

    private fun launch(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: error("$packageName is not installed on this device")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), FIND_TIMEOUT_MS)
    }

    private fun find(packageName: String, tag: String): UiObject2 {
        val found = device.wait(Until.findObject(By.res(tag)), FIND_TIMEOUT_MS)
        if (found == null) {
            dumpHierarchy("timed_out_waiting_for_${packageName}_$tag")
            error("Timed out waiting for $packageName:$tag")
        }
        return found
    }

    /** Logs the current on-screen accessibility tree so a failure shows what was
     * actually rendered, not just that the expected element never appeared. */
    private fun dumpHierarchy(label: String) {
        try {
            val out = java.io.ByteArrayOutputStream()
            device.dumpWindowHierarchy(out)
            val xml = out.toString("UTF-8")
            android.util.Log.i("E2E_HIERARCHY_DUMP", "----- BEGIN $label (${xml.length} chars) -----")
            xml.chunked(3500).forEach { android.util.Log.i("E2E_HIERARCHY_DUMP", it) }
            android.util.Log.i("E2E_HIERARCHY_DUMP", "----- END $label -----")
        } catch (e: Exception) {
            android.util.Log.w("E2E_HIERARCHY_DUMP", "Failed to dump hierarchy for $label", e)
        }
    }
}
