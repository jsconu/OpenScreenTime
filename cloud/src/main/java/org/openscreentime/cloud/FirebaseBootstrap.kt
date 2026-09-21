package org.openscreentime.cloud

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Starts Firebase for a cloud build, from the `google-services.json` bundled with this module.
 *
 * The google-services Gradle plugin would normally do this by generating string resources - but it
 * only does that for application modules, and everything Firebase in this project lives in this
 * library so that a local build can leave it out entirely. Applied here it produced nothing, and a
 * cloud build died on launch with "Default FirebaseApp is not initialized". Reading the same file
 * ourselves is a few lines, keeps all the cloud config in the one module, and means a local build
 * still contains no trace of it.
 *
 * The file holds a client per app (kid and parent share one project), so the right one is picked by
 * package name at runtime.
 *
 * Put your own at `cloud/src/main/assets/google-services.json` - see docs/SELF_HOSTING.md.
 */
object FirebaseBootstrap {

    private val json = Json { ignoreUnknownKeys = true }

    /** Safe to call more than once; the second call finds Firebase already running and returns. */
    fun ensureInitialized(context: Context) {
        if (FirebaseApp.getApps(context).isNotEmpty()) return
        val options = readOptions(context)
            ?: error(
                "No usable google-services.json in the cloud module's assets for ${context.packageName}. " +
                    "A cloud build needs one - see docs/SELF_HOSTING.md. (A local build does not, and " +
                    "does not contain this code at all.)"
            )
        FirebaseApp.initializeApp(context, options)
    }

    private fun readOptions(context: Context): FirebaseOptions? = runCatching {
        val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        val root = json.parseToJsonElement(text).jsonObject
        val projectInfo = root["project_info"]!!.jsonObject
        val client = root["client"]!!.jsonArray.first { element ->
            element.jsonObject["client_info"]!!.jsonObject["android_client_info"]!!
                .jsonObject["package_name"]!!.jsonPrimitive.content == context.packageName
        }.jsonObject

        FirebaseOptions.Builder()
            .setProjectId(projectInfo["project_id"]!!.jsonPrimitive.content)
            .setApplicationId(client["client_info"]!!.jsonObject["mobilesdk_app_id"]!!.jsonPrimitive.content)
            .setApiKey(client["api_key"]!!.jsonArray.first().jsonObject["current_key"]!!.jsonPrimitive.content)
            .apply {
                projectInfo["project_number"]?.jsonPrimitive?.content?.let(::setGcmSenderId)
                projectInfo["storage_bucket"]?.jsonPrimitive?.content?.let(::setStorageBucket)
            }
            .build()
    }.getOrNull()

    private const val ASSET = "google-services.json"
}
