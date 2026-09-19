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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openscreentime.shared.model.ChildProfile
import org.openscreentime.shared.model.DailyStats
import org.openscreentime.shared.model.InstalledApp
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
        kidRepo.updateBedtimeWindow(parentUid, child.id, startMinutes = 1260, endMinutes = 420)

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

    // --- Negotiated limits (#14) ---

    @Test(timeout = TEST_TIMEOUT_MS)
    fun claimedDeviceCanProposeAndParentCanApprove() = runBlocking {
        val parentRepo = FamilyRepository()
        val parentEmail = uniqueEmail()
        val parentUid = parentRepo.signUpParent(parentEmail, "testpass123")
        val child = parentRepo.createChild(parentUid, "ProposeChild")
        parentRepo.signOut()

        val kidRepo = FamilyRepository()
        kidRepo.claimPairingCode(child.pairingCode)

        // No passcode/parent-mode needed for this - it's a suggestion, never applied on its own.
        kidRepo.proposeLimits(parentUid, child.id, proposedDailyLimitMinutes = 90)

        val afterPropose = FirebaseFirestore.getInstance()
            .document(FirestorePaths.childDoc(parentUid, child.id)).get().await()
        val proposedProfile = ChildProfile.fromMap(child.id, afterPropose.data ?: emptyMap())
        assertEquals(90, proposedProfile.proposedDailyLimitMinutes)
        assertEquals("A proposal must never touch the real limit on its own", 120, proposedProfile.dailyLimitMinutes)

        // The parent signs back in to review it. Approving copies proposed -> real and clears it.
        kidRepo.signOut()
        parentRepo.signInParent(parentEmail, "testpass123")
        parentRepo.approveProposal(parentUid, child.id, proposedProfile)

        val afterApprove = FirebaseFirestore.getInstance()
            .document(FirestorePaths.childDoc(parentUid, child.id)).get().await()
        val approvedProfile = ChildProfile.fromMap(child.id, afterApprove.data ?: emptyMap())
        assertEquals(90, approvedProfile.dailyLimitMinutes)
        assertNull("Approving must clear the pending proposal", approvedProfile.proposedDailyLimitMinutes)
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun parentCanDeclineAProposalWithoutApplyingIt() = runBlocking {
        val parentRepo = FamilyRepository()
        val parentEmail = uniqueEmail()
        val parentUid = parentRepo.signUpParent(parentEmail, "testpass123")
        val child = parentRepo.createChild(parentUid, "DeclineChild")
        parentRepo.signOut()

        val kidRepo = FamilyRepository()
        kidRepo.claimPairingCode(child.pairingCode)
        kidRepo.proposeLimits(parentUid, child.id, proposedDailyLimitMinutes = 500)
        kidRepo.signOut()

        parentRepo.signInParent(parentEmail, "testpass123")
        parentRepo.declineProposal(parentUid, child.id)

        val afterDecline = FirebaseFirestore.getInstance()
            .document(FirestorePaths.childDoc(parentUid, child.id)).get().await()
        val declinedProfile = ChildProfile.fromMap(child.id, afterDecline.data ?: emptyMap())
        assertEquals("Declining must leave the real limit untouched", 120, declinedProfile.dailyLimitMinutes)
        assertNull(declinedProfile.proposedDailyLimitMinutes)
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun claimedDeviceCannotSmuggleARealLimitChangeIntoAProposalWrite() = runBlocking {
        val parentRepo = FamilyRepository()
        val parentUid = parentRepo.signUpParent(uniqueEmail(), "testpass123")
        val child = parentRepo.createChild(parentUid, "ProposeSmuggleChild")
        parentRepo.signOut()

        val kidRepo = FamilyRepository()
        kidRepo.claimPairingCode(child.pairingCode)

        // proposedDailyLimitMinutes alone is allowed for a claimed device via the
        // narrowly-scoped proposal rule; dailyLimitMinutes is not part of that rule.
        // Bundling them must reject the whole write, not just apply the proposal half.
        var threw = false
        try {
            FirebaseFirestore.getInstance()
                .document(FirestorePaths.childDoc(parentUid, child.id))
                .update(mapOf("proposedDailyLimitMinutes" to 5, "dailyLimitMinutes" to 5))
                .await()
        } catch (e: Exception) {
            threw = true
        }
        assertTrue("A proposal write must not be able to smuggle in a real-limit change", threw)
    }

    // --- Kid sees parent's self-tracked stats (#18) ---

    @Test(timeout = TEST_TIMEOUT_MS)
    fun linkedDeviceCanReadParentsSelfProfileButUnlinkedDeviceCannot() = runBlocking {
        val parentRepo = FamilyRepository()
        val parentUid = parentRepo.signUpParent(uniqueEmail(), "testpass123")
        val child = parentRepo.createChild(parentUid, "SelfViewChild")

        // Parent opts into self-tracking and pushes a day of stats, still signed in as parent.
        val self = parentRepo.getOrCreateSelfProfile(parentUid, "Me")
        parentRepo.pushDailyStats(parentUid, self.id, DailyStats(date = "2024-01-01", totalScreenTimeMs = 1000))
        parentRepo.signOut()

        // claimPairingCode appends this device's uid to linkedDeviceUids (see #18).
        val kidRepo = FamilyRepository()
        kidRepo.claimPairingCode(child.pairingCode)

        val fetched = kidRepo.getParentSelfProfile(parentUid)
        assertNotNull("A linked device should be able to read the parent's self-tracked profile", fetched)
        assertEquals("Me", fetched?.name)
        kidRepo.signOut()

        // A device that never claimed a pairing code under this parent is not linked.
        // Must sign the linked kid out first - Firebase Auth's signInAnonymously() reuses
        // the currently-signed-in anonymous user instead of minting a new one if one is
        // already active, so without this the "stranger" would actually just be the kid.
        val strangerRepo = FamilyRepository()
        strangerRepo.signInAnonymously()
        assertNull(
            "An unrelated device must not be able to read another family's self-tracked profile",
            strangerRepo.getParentSelfProfile(parentUid)
        )
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun claimingDeviceCannotSmuggleOtherFieldsIntoTheLinkedDeviceUidsWrite() = runBlocking {
        val parentRepo = FamilyRepository()
        val parentUid = parentRepo.signUpParent(uniqueEmail(), "testpass123")
        val child = parentRepo.createChild(parentUid, "LinkSmuggleChild")
        parentRepo.signOut()

        val kidRepo = FamilyRepository()
        kidRepo.claimPairingCode(child.pairingCode) // legitimately appends its own uid

        // A follow-up write bundling linkedDeviceUids with an unrelated field must be rejected.
        var threw = false
        try {
            FirebaseFirestore.getInstance()
                .document("${FirestorePaths.PARENTS}/$parentUid")
                .update(mapOf("linkedDeviceUids" to listOf("someone"), "passcodeHash" to "hacked"))
                .await()
        } catch (e: Exception) {
            threw = true
        }
        assertTrue("A linkedDeviceUids write must not be able to smuggle in another field", threw)
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
    fun pairedKidPublishesInstalledApps_parentReadsThemBack() = runBlocking {
        val parentRepo = FamilyRepository()
        val parentEmail = uniqueEmail()
        val parentUid = parentRepo.signUpParent(parentEmail, "testpass123")
        val child = parentRepo.createChild(parentUid, "AppsChild")
        parentRepo.signOut()

        val kidRepo = FamilyRepository()
        kidRepo.claimPairingCode(child.pairingCode)
        kidRepo.pushInstalledApps(
            parentUid, child.id,
            listOf(InstalledApp("a.one", "One"), InstalledApp("b.two", "Two"))
        )
        kidRepo.signOut()

        parentRepo.signInParent(parentEmail, "testpass123")
        val raw = FirebaseFirestore.getInstance()
            .document(FirestorePaths.installedAppsDoc(parentUid, child.id))
            .get().await()
        assertEquals(2, (raw.get("apps") as List<*>).size)
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun installedAppsWriteIsRejectedWhenOversizedOrFromAStranger() = runBlocking {
        val parentRepo = FamilyRepository()
        val parentUid = parentRepo.signUpParent(uniqueEmail(), "testpass123")
        val child = parentRepo.createChild(parentUid, "AppsCapChild")
        parentRepo.signOut()

        // A stranger with just anonymous auth can't write another family's installed-apps doc.
        val strangerRepo = FamilyRepository()
        strangerRepo.signInAnonymously()
        var strangerThrew = false
        try {
            strangerRepo.pushInstalledApps(parentUid, child.id, listOf(InstalledApp("x.y", "X")))
        } catch (e: Exception) {
            strangerThrew = true
        }
        assertTrue("A stranger must not be able to publish installed apps", strangerThrew)
        strangerRepo.signOut()

        // The real paired device can, but not more than the cap allows.
        val kidRepo = FamilyRepository()
        kidRepo.claimPairingCode(child.pairingCode)
        var oversizedThrew = false
        try {
            kidRepo.pushInstalledApps(parentUid, child.id, (1..501).map { InstalledApp("p.$it", "App $it") })
        } catch (e: Exception) {
            oversizedThrew = true
        }
        assertTrue("More than 500 apps must be rejected", oversizedThrew)
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
