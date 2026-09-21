package org.openscreentime.kid

import android.app.Application
import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import org.openscreentime.cloud.FirebaseBootstrap
import com.google.firebase.firestore.FirebaseFirestore
import org.openscreentime.kid.data.PairingStore
import org.openscreentime.shared.repo.FamilyRepository
import org.openscreentime.shared.repo.FirebaseFamilyRepository

/**
 * The cloud flavor: this phone is paired with a parent's phone through a Firebase project, which is
 * what makes remotely set limits, remote locking and "more time" requests from elsewhere work.
 *
 * Everything Firebase in the kid app enters through here. The local flavor has its own copy of this
 * file at `src/local/java/.../Backend.kt` and pulls in no cloud code whatsoever.
 */
object Backend {

    const val IS_LOCAL = false

    fun createRepository(app: Application): FamilyRepository = FirebaseFamilyRepository()

    fun onAppCreate(app: Application) {
        // First, before anything reaches for Firebase: this module supplies its own config rather
        // than relying on the google-services plugin (see FirebaseBootstrap).
        FirebaseBootstrap.ensureInitialized(app)
        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            // Must happen before the repository's lazy init ever touches Firebase.
            FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080)
            FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)
        }
    }

    /** Null until this phone has claimed a pairing code - there is nothing to listen to before that. */
    fun profileIds(context: Context): Pair<String, String>? {
        val store = PairingStore(context)
        val parentUid = store.parentUid ?: return null
        val childId = store.childId ?: return null
        return parentUid to childId
    }
}
