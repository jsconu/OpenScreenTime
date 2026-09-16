package org.openscreentime.shared.repo

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/** Firebase Auth only: parent sign-up/sign-in/sign-out and the kid device's anonymous identity. */
internal class SessionRepository(private val auth: FirebaseAuth) {
    val currentUid: String? get() = auth.currentUser?.uid

    suspend fun signUpParent(email: String, password: String): String {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        return result.user!!.uid
    }

    suspend fun signInParent(email: String, password: String): String {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        return result.user!!.uid
    }

    suspend fun signInAnonymously(): String {
        auth.currentUser?.let { return it.uid }
        val result = auth.signInAnonymously().await()
        return result.user!!.uid
    }

    fun signOut() = auth.signOut()
}
