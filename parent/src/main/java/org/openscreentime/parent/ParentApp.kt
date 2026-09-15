package org.openscreentime.parent

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.openscreentime.shared.repo.FamilyRepository

class ParentApp : Application() {
    val repository by lazy { FamilyRepository() }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            // Must happen before FamilyRepository's lazy init ever touches Firebase.
            FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080)
            FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)
        }
    }
}
