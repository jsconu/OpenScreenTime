package org.openscreentime.parent.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.openscreentime.parent.ParentApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as ParentApp).repository

        setContent {
            OpenScreenTimeTheme {
                val navController = rememberNavController()
                var signedIn by remember { mutableStateOf(repository.currentUid != null) }

                NavHost(
                    navController = navController,
                    startDestination = if (signedIn) "dashboard" else "auth"
                ) {
                    composable("auth") {
                        AuthScreen(
                            repository = repository,
                            onSignedIn = {
                                signedIn = true
                                navController.navigate("dashboard") {
                                    popUpTo("auth") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("dashboard") {
                        DashboardScreen(
                            repository = repository,
                            onOpenChild = { childId -> navController.navigate("child/$childId") },
                            onSignOut = {
                                repository.signOut()
                                signedIn = false
                                navController.navigate("auth") {
                                    popUpTo("dashboard") { inclusive = true }
                                }
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
                }
            }
        }
    }
}
