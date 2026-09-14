package org.openscreentime.shared

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openscreentime.shared.repo.FamilyRepository

/**
 * Exercises the real pairing flow (FamilyRepository.createChild -> claimPairingCode)
 * against the Firebase Local Emulator Suite (see .github/workflows/e2e.yml), which
 * loads the actual firebase/firestore.rules from this repo - so this is a genuine
 * end-to-end check of both the pairing transaction and the security rules, running
 * on a real Android emulator against a real (local, ephemeral) Firestore + Auth
 * backend, not a simulation.
 *
 * This module has no google-services.json (only the kid/parent apps do), so Firebase
 * is initialized here by hand with throwaway options - fine, since useEmulator()
 * means none of it ever reaches real Google servers.
 *
 * Requires the Firestore + Auth emulators to already be running and reachable at
 * 10.0.2.2:8080 / 10.0.2.2:9099 (the host machine, from the Android emulator's view).
 */
@RunWith(AndroidJUnit4::class)
class PairingFlowEmulatorTest {

    @Before
    fun connectToEmulators() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        if (FirebaseApp.getApps(context).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setProjectId(TEST_PROJECT_ID)
                .setApplicationId("1:000000000000:android:0000000000000000")
                .setApiKey("test-api-key")
                .build()
            FirebaseApp.initializeApp(context, options)
            FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080)
            FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)
        }
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun parentCreatesChild_kidClaimsCode_pairingSucceeds() = runBlocking {
        val parentRepo = FamilyRepository()
        val parentUid = parentRepo.signUpParent(uniqueEmail(), "testpass123")

        val child = parentRepo.createChild(parentUid, "TestChild")
        assertEquals("TestChild", child.name)
        assertTrue("A freshly created child should not be paired yet", !child.paired)
        parentRepo.signOut()

        val kidRepo = FamilyRepository()
        val (claimedParentUid, claimedChild) = kidRepo.claimPairingCode(child.pairingCode)

        assertEquals(parentUid, claimedParentUid)
        assertEquals(child.id, claimedChild.id)
        assertTrue("Claiming the code should mark the child as paired", claimedChild.paired)
        assertNotNull("Claiming the code should stamp this device's uid onto the child", claimedChild.deviceUid)
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun pairingCodeCannotBeClaimedTwice() = runBlocking {
        val parentRepo = FamilyRepository()
        val parentUid = parentRepo.signUpParent(uniqueEmail(), "testpass123")
        val child = parentRepo.createChild(parentUid, "OtherChild")
        parentRepo.signOut()

        val firstKid = FamilyRepository()
        firstKid.claimPairingCode(child.pairingCode)
        firstKid.signOut()

        val secondKid = FamilyRepository()
        var threw = false
        try {
            secondKid.claimPairingCode(child.pairingCode)
        } catch (e: Exception) {
            threw = true
        }
        assertTrue("A second device should not be able to claim an already-used pairing code", threw)
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun claimingDeviceCanEditItsOwnLimitsButNotAnotherFamilys() = runBlocking {
        val parentRepo = FamilyRepository()
        val parentUid = parentRepo.signUpParent(uniqueEmail(), "testpass123")
        val child = parentRepo.createChild(parentUid, "ThirdChild")
        parentRepo.signOut()

        val kidRepo = FamilyRepository()
        kidRepo.claimPairingCode(child.pairingCode)

        // The rules explicitly allow the linked device to change its own limits/lock -
        // this is the write path the kid app's "Parent controls" screen relies on.
        kidRepo.updateDailyLimit(parentUid, child.id, 45)
        kidRepo.setLocked(parentUid, child.id, true)

        // But not another family's child, even one it doesn't know the id of directly -
        // simulate a guess by reusing a real, but unrelated, parent/child pair.
        val otherParentRepo = FamilyRepository()
        val otherParentUid = otherParentRepo.signUpParent(uniqueEmail(), "testpass123")
        val otherChild = otherParentRepo.createChild(otherParentUid, "UnrelatedChild")
        otherParentRepo.signOut()

        var threw = false
        try {
            kidRepo.updateDailyLimit(otherParentUid, otherChild.id, 10)
        } catch (e: Exception) {
            threw = true
        }
        assertTrue("A device must not be able to write another family's child doc", threw)
    }

    private fun uniqueEmail() = "e2e-${System.currentTimeMillis()}-${(0..9999).random()}@example.com"

    companion object {
        private const val TEST_PROJECT_ID = "openscreentime-e2e"
        private const val TEST_TIMEOUT_MS = 20_000L
    }
}
