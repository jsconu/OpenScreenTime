package org.openscreentime.shared.repo

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
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

    /**
     * Emails a password-reset link. A missing account deliberately looks the same as success, so the
     * sign-in screen can't be used to find out which emails have accounts (newer Firebase projects do
     * the same server-side; this keeps the behavior identical on older ones and on the emulator).
     * A malformed address or no connection still throws, since that's the user's to fix.
     */
    suspend fun sendPasswordReset(email: String) {
        try {
            auth.sendPasswordResetEmail(email.trim()).await()
        } catch (e: FirebaseAuthInvalidUserException) {
            // Same outcome as a real account: nothing to tell the caller.
        }
    }

    suspend fun signInAnonymously(): String {
        auth.currentUser?.let { return it.uid }
        val result = auth.signInAnonymously().await()
        return result.user!!.uid
    }

    fun signOut() = auth.signOut()
}
