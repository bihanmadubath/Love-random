package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import com.example.data.UserEntity
import com.example.data.UserRepository
import com.example.health.SystemHealthMonitor
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    lateinit var database: AppDatabase
    lateinit var userRepository: UserRepository
    lateinit var healthMonitor: SystemHealthMonitor

    private var pendingPermissionRequest: PermissionRequest? = null
    private var pendingGeoCallback: Pair<String?, GeolocationPermissions.Callback?>? = null

    val permissionsLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val cameraGranted = result[Manifest.permission.CAMERA]
                ?: (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            val micGranted = result[Manifest.permission.RECORD_AUDIO]
                ?: (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)

            pendingPermissionRequest?.let { req ->
                runOnUiThread {
                    val toGrant = mutableListOf<String>()
                    if (cameraGranted && req.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                        toGrant.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                    }
                    if (micGranted && req.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                        toGrant.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                    }
                    if (toGrant.isNotEmpty()) {
                        req.grant(toGrant.toTypedArray())
                    } else {
                        req.deny()
                    }
                }
                pendingPermissionRequest = null
            }

            val locGranted = (result[Manifest.permission.ACCESS_FINE_LOCATION] ?: false) ||
                    (result[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false) ||
                    (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)

            if (locGranted) {
                pendingGeoCallback?.let { (origin, cb) ->
                    cb?.invoke(origin, true, false)
                    pendingGeoCallback = null
                }
            }
        }

    fun requestAllPermissions() {
        permissionsLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    fun hasCameraAndMicPermission(): Boolean {
        val cam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        return cam && mic
    }

    fun handleWebPermissionRequest(request: PermissionRequest?) {
        if (request == null) return
        val resources = request.resources
        val toGrant = mutableListOf<String>()
        val toAsk = mutableListOf<String>()

        if (resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                toGrant.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
            } else {
                toAsk.add(Manifest.permission.CAMERA)
            }
        }

        if (resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                toGrant.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
            } else {
                toAsk.add(Manifest.permission.RECORD_AUDIO)
            }
        }

        if (toAsk.isEmpty()) {
            runOnUiThread {
                if (toGrant.isNotEmpty()) {
                    request.grant(toGrant.toTypedArray())
                } else {
                    request.deny()
                }
            }
        } else {
            pendingPermissionRequest = request
            permissionsLauncher.launch(toAsk.toTypedArray())
        }
    }

    fun handleGeoPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
        val needLoc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        if (!needLoc) {
            callback?.invoke(origin, true, false)
        } else {
            pendingGeoCallback = Pair(origin, callback)
            permissionsLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        database = AppDatabase.getDatabase(this)
        healthMonitor = SystemHealthMonitor.getInstance(this)
        userRepository = UserRepository(database.userDao(), healthMonitor)

        // Prepopulate database with initial sample users in background if empty
        lifecycleScope.launch(Dispatchers.IO) {
            val seedUsers = listOf(
                UserEntity(
                    id = "u_bihan_admin",
                    username = "Bihan",
                    password = "Bihan@1996",
                    name = "Bihan",
                    role = "admin",
                    isVip = true,
                    age = 28,
                    gender = "Male",
                    city = "Headquarters",
                    avatar = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=120&auto=format&fit=crop&q=80",
                    status = "active",
                    isLive = true,
                    sessionState = "online"
                ),
                UserEntity(
                    id = "u_alex_walker",
                    username = "alex",
                    password = "alex123",
                    name = "Alex Walker",
                    role = "user",
                    isVip = true,
                    age = 23,
                    gender = "Male",
                    city = "Colombo, Sri Lanka",
                    avatar = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=120&auto=format&fit=crop&q=80",
                    status = "active",
                    isLive = true,
                    sessionState = "online"
                ),
                UserEntity(
                    id = "u_elena_rostova",
                    username = "elena",
                    password = "elena123",
                    name = "Elena Rostova",
                    role = "user",
                    isVip = false,
                    age = 24,
                    gender = "Female",
                    city = "Rome, Italy",
                    avatar = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=120&auto=format&fit=crop&q=80",
                    status = "active",
                    isLive = true,
                    sessionState = "seeking"
                ),
                UserEntity(
                    id = "u_bad_actor",
                    username = "troll99",
                    password = "password123",
                    name = "Jake Spammer",
                    role = "user",
                    isVip = false,
                    age = 27,
                    gender = "Male",
                    city = "Unknown",
                    avatar = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=120&auto=format&fit=crop&q=80",
                    status = "banned",
                    isLive = false,
                    sessionState = "offline"
                )
            )
            userRepository.seedInitialUsersIfEmpty(seedUsers)
        }

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_scaffold")
                        .background(Color(0xFF0B0C10)),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    LoveRandomAppScreen(
                        activity = this,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

class WebAppInterface(
    private val activity: MainActivity,
    private val userRepository: UserRepository? = null,
    private val healthMonitor: SystemHealthMonitor? = null
) {
    @JavascriptInterface
    fun requestPermissions() {
        activity.runOnUiThread {
            activity.requestAllPermissions()
        }
    }

    @JavascriptInterface
    fun hasCameraPermission(): Boolean {
        return activity.hasCameraAndMicPermission()
    }

    @JavascriptInterface
    fun isAndroidApp(): Boolean {
        return true
    }

    @JavascriptInterface
    fun getDeviceInfo(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
    }

    @JavascriptInterface
    fun getSystemHealth(): String {
        return healthMonitor?.getHealthSummaryJson()
            ?: """{"status":"HEALTHY","avgQueryDurationMs":1.2,"memoryUsagePercent":25,"totalQueriesLogged":10,"uptimeSeconds":60}"""
    }

    @JavascriptInterface
    fun recordMetric(type: String, name: String, value: Double, details: String) {
        healthMonitor?.recordCustomMetric(type, name, value, details)
    }

    @JavascriptInterface
    fun verifyAdminCredentials(passwordAttempt: String): Boolean {
        val repo = userRepository ?: return false
        val admin = runBlocking(Dispatchers.IO) {
            repo.getUserByUsername("Bihan")
        }
        return admin != null && admin.password == passwordAttempt.trim()
    }

    @JavascriptInterface
    fun getAdminUsersJson(filterStatus: String): String {
        val repo = userRepository ?: return "[]"
        val users = runBlocking(Dispatchers.IO) {
            when (filterStatus.lowercase()) {
                "live" -> repo.allUsersFlow
                "banned" -> repo.getUsersByStatus("banned")
                "active" -> repo.getUsersByStatus("active")
                else -> repo.getAllUsers()
            }
        }
        val userList = if (filterStatus.lowercase() == "live") {
            runBlocking(Dispatchers.IO) { repo.getAllUsers().filter { it.isLive } }
        } else {
            users as? List<UserEntity> ?: runBlocking(Dispatchers.IO) { repo.getAllUsers() }
        }

        val jsonArray = userList.joinToString(prefix = "[", postfix = "]") { u ->
            """{"id":"${u.id}","username":"${u.username}","name":"${u.name}","role":"${u.role}","isVip":${u.isVip},"age":${u.age},"gender":"${u.gender}","city":"${u.city}","avatar":"${u.avatar}","status":"${u.status}","isLive":${u.isLive},"sessionState":"${u.sessionState}"}"""
        }
        return jsonArray
    }

    @JavascriptInterface
    fun toggleUserStatus(userId: String, newStatus: String): Boolean {
        val repo = userRepository ?: return false
        runBlocking(Dispatchers.IO) {
            repo.updateUserStatus(userId, newStatus, if (newStatus == "banned") "offline" else "online", newStatus != "banned")
        }
        return true
    }

    @JavascriptInterface
    fun runDbBenchmark(): String {
        val repo = userRepository ?: return "Repository not attached"
        val start = System.nanoTime()
        val users = runBlocking(Dispatchers.IO) {
            repo.getAllUsers()
        }
        val durationMs = (System.nanoTime() - start) / 1_000_000.0
        healthMonitor?.recordQueryLatency("benchmark_getAllUsers", durationMs)
        return "Loaded ${users.size} indexed users in ${String.format("%.2f", durationMs)}ms"
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoveRandomAppScreen(
    activity: MainActivity,
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                webViewInstance?.stopLoading()
                webViewInstance?.destroy()
                webViewInstance = null
            } catch (e: Exception) {
                Log.w("LoveRandom", "Error disposing webView", e)
            }
        }
    }

    Box(modifier = modifier.background(Color(0xFF0B0C10)).testTag("web_container")) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mediaPlaybackRequiresUserGesture = false
                        // Smooth rendering configurations for virtualized GL environments
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }
                    // Explicitly use software layer in emulator/virtualized environments so WebView doesn't query Linux rendernodes
                    setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                    setBackgroundColor(0xFF0B0C10.toInt())
                    addJavascriptInterface(
                        WebAppInterface(
                            activity = activity,
                            userRepository = activity.userRepository,
                            healthMonitor = activity.healthMonitor
                        ),
                        "AndroidBridge"
                    )
                    webViewClient = object : WebViewClient() {
                        override fun onRenderProcessGone(
                            view: WebView?,
                            detail: android.webkit.RenderProcessGoneDetail?
                        ): Boolean {
                            Log.w("LoveRandom", "onRenderProcessGone: didCrash=${detail?.didCrash()}")
                            return true
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest?) {
                            activity.handleWebPermissionRequest(request)
                        }

                        override fun onGeolocationPermissionsShowPrompt(
                            origin: String?,
                            callback: GeolocationPermissions.Callback?
                        ) {
                            activity.handleGeoPrompt(origin, callback)
                        }

                        override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                            Log.d("LoveRandomJS", "${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                            return super.onConsoleMessage(consoleMessage)
                        }
                    }
                    loadUrl("file:///android_asset/index.html")
                    webViewInstance = this
                }
            }
        )
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme { Greeting("Android") }
}
