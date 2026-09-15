package org.openscreentime.shared

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openscreentime.shared.repo.FamilyRepository
import org.openscreentime.shared.repo.FirestorePaths

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
    fun deletingAChildRemovesItsProfileAndDailyStats() = runBlocking {
        val parentRepo = FamilyRepository()
        val parentUid = parentRepo.signUpParent(uniqueEmail(), "testpass123")
        val child = parentRepo.createChild(parentUid, "DoomedChild")

        val db = FirebaseFirestore.getInstance()
        db.document(FirestorePaths.dailyStatsDoc(parentUid, child.id, "2024-01-01"))
            .set(mapOf("totalScreenTimeMs" to 1000L)).await()

        parentRepo.deleteChild(parentUid, child.id)

        val childSnap = db.document(FirestorePaths.childDoc(parentUid, child.id)).get().await()
        assertTrue("The child doc should be gone after deletion", !childSnap.exists())

        val statsSnap = db.collection(FirestorePaths.dailyStatsCollection(parentUid, child.id)).get().await()
        assertTrue("dailyStats should be cleaned up along with the child, not orphaned", statsSnap.isEmpty)
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
        // Set up an unrelated family first, while it's convenient to be signed in
        // as its own parent, then sign out - all FamilyRepository() instances in
        // this test share one process-wide FirebaseAuth session, so whichever
        // identity is signed in *last* is the one active for the write below.
        val otherParentRepo = FamilyRepository()
        val otherParentUid = otherParentRepo.signUpParent(uniqueEmail(), "testpass123")
        val otherChild = otherParentRepo.createChild(otherParentUid, "UnrelatedChild")
        otherParentRepo.signOut()

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
        kidRepo.updateDailyUnlockGoal(parentUid, child.id, 30)

        // But not another family's child. The kid's session is still the active
        // auth identity here (never signed out), so this is a genuine
        // authenticated-but-unauthorized write attempt, not an unauthenticated one.
        var threw = false
        try {
            kidRepo.updateDailyLimit(otherParentUid, otherChild.id, 10)
        } catch (e: Exception) {
            threw = true
        }
        assertTrue("A device must not be able to write another family's child doc", threw)
    }

    // --- Adversarial checks for #2 ---

    @Test(timeout = TEST_TIMEOUT_MS)
    fun concurrentClaimsOnlyOneWins() = runBlocking {
        val parentRepo = FamilyRepository()
        val parentUid = parentRepo.signUpParent(uniqueEmail(), "testpass123")
        val child = parentRepo.createChild(parentUid, "RaceChild")
        parentRepo.signOut()

        // Two genuinely independent Firebase identities (separate FirebaseApp instances,
        // not just separate FamilyRepository objects sharing one auth session), so this
        // is a real "two different devices" race, not a same-uid retry.
        val secondaryApp = secondaryFirebaseApp()
        val deviceA = FamilyRepository()
        val deviceB = FamilyRepository(
            auth = FirebaseAuth.getInstance(secondaryApp),
            db = FirebaseFirestore.getInstance(secondaryApp)
        )

        val resultA = async { runCatching { deviceA.claimPairingCode(child.pairingCode) } }
        val resultB = async { runCatching { deviceB.claimPairingCode(child.pairingCode) } }
        val successes = awaitAll(resultA, resultB).count { it.isSuccess }

        assertEquals("Exactly one of two simultaneous claims on the same code should win", 1, successes)
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun claimedDeviceCannotSmugglePasscodeFieldIntoAnAllowedUpdate() = runBlocking {
        val parentRepo = FamilyRepository()
        val parentUid = parentRepo.signUpParent(uniqueEmail(), "testpass123")
        val child = parentRepo.createChild(parentUid, "SmuggleChild")
        parentRepo.signOut()

        val kidRepo = FamilyRepository()
        kidRepo.claimPairingCode(child.pairingCode)

        // appLimits alone is allowed for a claimed device; parentPasscodeHash never is.
        // Bundling them in one write must reject the whole write, not just ignore the bad field.
        var threw = false
        try {
            FirebaseFirestore.getInstance()
                .document(FirestorePaths.childDoc(parentUid, child.id))
                .update(mapOf("appLimits" to mapOf("com.example" to 30), "parentPasscodeHash" to "hacked"))
                .await()
        } catch (e: Exception) {
            threw = true
        }
        assertTrue("A write mixing an allowed field with a forbidden one must be rejected entirely", threw)
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun unrelatedAnonymousDeviceCannotReadParentAccountDoc() = runBlocking {
        val parentRepo = FamilyRepository()
        val parentUid = parentRepo.signUpParent(uniqueEmail(), "testpass123")
        parentRepo.setParentPasscode(parentUid, "some-hash", "some-salt")
        parentRepo.signOut()

        // A stranger who has never seen a pairing code - just anonymous auth, same as
        // any kid app install would get before pairing.
        val strangerRepo = FamilyRepository()
        strangerRepo.signInAnonymously()

        var threw = false
        try {
            FirebaseFirestore.getInstance()
                .document("${FirestorePaths.PARENTS}/$parentUid")
                .get()
                .await()
        } catch (e: Exception) {
            threw = true
        }
        assertTrue(
            "An unrelated anonymous session must not be able to read a parent's account doc " +
                "(it holds the family passcode hash)",
            threw
        )
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun claimedDeviceCannotListSiblingChildren() = runBlocking {
        val parentRepo = FamilyRepository()
        val parentUid = parentRepo.signUpParent(uniqueEmail(), "testpass123")
        val child = parentRepo.createChild(parentUid, "ListChildA")
        parentRepo.createChild(parentUid, "ListChildB")
        parentRepo.signOut()

        val kidRepo = FamilyRepository()
        kidRepo.claimPairingCode(child.pairingCode)

        var threw = false
        try {
            FirebaseFirestore.getInstance()
                .collection(FirestorePaths.childrenCollection(parentUid))
                .get()
                .await()
        } catch (e: Exception) {
            threw = true
        }
        assertTrue(
            "A claimed device must not be able to list its parent's whole children collection " +
                "(only get its own child doc by id)",
            threw
        )
    }

    private fun secondaryFirebaseApp(): FirebaseApp {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        FirebaseApp.getApps(context).find { it.name == "secondary" }?.let { return it }

        val options = FirebaseOptions.Builder()
            .setProjectId(TEST_PROJECT_ID)
            .setApplicationId("1:000000000000:android:0000000000000001")
            .setApiKey("test-api-key")
            .build()
        val app = FirebaseApp.initializeApp(context, options, "secondary")
        FirebaseFirestore.getInstance(app).useEmulator("10.0.2.2", 8080)
        FirebaseAuth.getInstance(app).useEmulator("10.0.2.2", 9099)
        return app
    }

    private fun uniqueEmail() = "e2e-${System.currentTimeMillis()}-${(0..9999).random()}@example.com"

    companion object {
        private const val TEST_PROJECT_ID = "openscreentime-e2e"
        private const val TEST_TIMEOUT_MS = 45_000L
    }
}
