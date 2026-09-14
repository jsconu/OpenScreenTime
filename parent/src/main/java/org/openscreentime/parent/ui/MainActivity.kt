package org.openscreentime.parent.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.openscreentime.parent.AppLockState
import org.openscreentime.parent.ParentApp
import org.openscreentime.shared.model.PasscodeInfo

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as ParentApp).repository

        setContent {
            OpenScreenTimeTheme {
                val navController = rememberNavController()
                var signedIn by remember { mutableStateOf(repository.currentUid != null) }

                if (!signedIn) {
                    AuthScreen(repository = repository, onSignedIn = { signedIn = true })
                } else {
                    var passcodeChecked by remember { mutableStateOf(false) }
                    var passcode by remember { mutableStateOf<PasscodeInfo?>(null) }
                    var locked by remember { mutableStateOf(!AppLockState.unlockedThisSession) }

                    LaunchedEffect(signedIn) {
                        passcode = repository.getParentPasscode(repository.currentUid!!)
                        if (passcode == null) {
                            AppLockState.unlockedThisSession = true
                            locked = false
                        }
                        passcodeChecked = true
                    }

                    val currentPasscode = passcode
                    if (passcodeChecked && locked && currentPasscode != null) {
                        PasscodeUnlockScreen(
                            passcode = currentPasscode,
                            onUnlocked = {
                                AppLockState.unlockedThisSession = true
                                locked = false
                            }
                        )
                    } else if (passcodeChecked) {
                        NavHost(navController = navController, startDestination = "dashboard") {
                            composable("dashboard") {
                                DashboardScreen(
                                    repository = repository,
                                    onOpenChild = { childId -> navController.navigate("child/$childId") },
                                    onOpenSettings = { navController.navigate("settings") },
                                    onSignOut = {
                                        repository.signOut()
                                        AppLockState.unlockedThisSession = false
                                        signedIn = false
                                    }
                                )
                            }
                            composable(
                                "child/{childId}",
                                arguments = listOf(navArgument("childId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val childId = backStackEntry.arguments?.getString("childId")!!
                                ChildDetailScreen(
                                    repository = repository,
                                    childId = childId,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable("settings") {
                                PasscodeSettingsScreen(
                                    repository = repository,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
