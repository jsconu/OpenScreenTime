package org.openscreentime.parent

import android.app.Application
import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.openscreentime.parent.data.SelfProfileStore
import org.openscreentime.shared.repo.FamilyRepository
import org.openscreentime.shared.repo.FirebaseFamilyRepository

/**
 * The cloud flavor: a parent account in a Firebase project, children paired to it, and this phone
 * optionally tracked alongside them.
 *
 * Everything Firebase in the parent app enters through here. The local flavor has its own copy of
 * this file at `src/local/java/.../Backend.kt` and pulls in no cloud code whatsoever.
 */
object Backend {

    const val IS_LOCAL = false

    fun createRepository(app: Application): FamilyRepository = FirebaseFamilyRepository()

    fun onAppCreate(app: Application) {
        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            // Must happen before the repository's lazy init ever touches Firebase.
            FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080)
            FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)
        }
    }

    /** Null until the parent has started tracking this phone, or before they have signed in. */
    fun profileIds(context: Context): Pair<String, String>? {
        val childId = SelfProfileStore(context).childId ?: return null
        val parentUid = (context.applicationContext as ParentApp).repository.currentUid ?: return null
        return parentUid to childId
    }
}
