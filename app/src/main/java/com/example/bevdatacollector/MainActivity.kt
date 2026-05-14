package com.example.bevdatacollector

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.bevdatacollector.calibration.CalibrationScreen
import com.example.bevdatacollector.capture.CaptureScreen
import com.example.bevdatacollector.trackingui.ProcessingScreen
import com.example.bevdatacollector.trackingui.SessionBrowserScreen
import com.example.bevdatacollector.ui.theme.BEVDataCollectorTheme
import com.example.bevdatacollector.trackingui.LatLongSessionBrowserScreen
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {

    private val viewModel by lazy { MainViewModel() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BEVDataCollectorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    AppNavigation(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val hasCamera = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val hasLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.updatePermissionsStatus(hasCamera, hasLocation)
    }

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                onNavigateToCalibration = { navController.navigate("calibration") },
                onNavigateToCapture = { navController.navigate("capture") },
                onNavigateToTracking = { navController.navigate("session_browser") },
                onNavigateToLatLong = {navController.navigate("latlong_browser")
                }
            )
        }

        composable("calibration") {
            CalibrationScreen(onCalibrationComplete = { navController.popBackStack() })
        }

        composable("capture") {
            CaptureScreen(onBack = { navController.popBackStack() })
        }

        composable("session_browser") {
            SessionBrowserScreen(
                onSessionSelected = { session ->
                    // Encode the path to safely pass in URL
                    val encodedPath = URLEncoder.encode(session.absolutePath, "UTF-8")
                    navController.navigate("processing/$encodedPath")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable("processing/{sessionPath}") { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString("sessionPath") ?: ""
            // Decode the path back to original
            val sessionPath = URLDecoder.decode(encodedPath, "UTF-8")
            val sessionFolder = File(sessionPath)
            ProcessingScreen(
                sessionFolder = sessionFolder,
                onComplete = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }
        composable("latlong_browser") {
            LatLongSessionBrowserScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}