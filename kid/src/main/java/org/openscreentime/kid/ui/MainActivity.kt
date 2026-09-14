package org.openscreentime.kid.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.openscreentime.kid.data.PairingStore
import org.openscreentime.kid.monitor.ScreenMonitorService
import org.openscreentime.kid.util.checkPermissions

class MainActivity : ComponentActivity() {

    private lateinit var pairingStore: PairingStore

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pairingStore = PairingStore(this)

        setContent {
            OpenScreenTimeTheme {
                var paired by remember { mutableStateOf(pairingStore.isPaired) }
                var permissions by remember { mutableStateOf(checkPermissions(this)) }

                DisposableEffect(Unit) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            permissions = checkPermissions(this@MainActivity)
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }

                if (!paired) {
                    PairingScreen(
                        onPaired = { name ->
                            pairingStore.childName = name
                            paired = true
                            ContextCompat.startForegroundService(
                                this,
                                Intent(this, ScreenMonitorService::class.java)
                            )
                        }
                    )
                } else {
                    StatusScreen(
                        childName = pairingStore.childName ?: "",
                        permissions = permissions,
                        onRequestOverlay = {
                            startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                            )
                        },
                        onRequestAccessibility = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onRequestNotifications = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onUnpair = {
                            pairingStore.clear()
                            paired = false
                        }
                    )
                }
            }
        }
    }
}
