package com.example.bevdatacollector

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bevdatacollector.utils.CalibrationHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToCalibration: () -> Unit,
    onNavigateToCapture: () -> Unit,
    onNavigateToTracking: () -> Unit,
    onNavigateToLatLong: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val calibrationHelper = remember { CalibrationHelper(context) }
    val isCalibrated = calibrationHelper.isCalibrated()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Title
        Text(
            text = "BEV Data Collector",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Row 1: Calibration and Capture
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onNavigateToCalibration,
                modifier = Modifier.weight(1f).height(120.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📷", style = MaterialTheme.typography.displayMedium)
                    Text("Calibration", style = MaterialTheme.typography.titleMedium)
                    Text(if (isCalibrated) " Calibrated" else "️ Required", style = MaterialTheme.typography.bodySmall)
                }
            }

            Button(
                onClick = {
                    if (isCalibrated) onNavigateToCapture()
                    else android.widget.Toast.makeText(context, "Calibration needed!", android.widget.Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.weight(1f).height(120.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎥", style = MaterialTheme.typography.displayMedium)
                    Text("Capture", style = MaterialTheme.typography.titleMedium)
                    Text("30 FPS + Sensors", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Row 2: Detection and Lat/Long
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onNavigateToTracking,
                modifier = Modifier.weight(1f).height(120.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔍", style = MaterialTheme.typography.displayMedium)
                    Text("Detection", style = MaterialTheme.typography.titleMedium)
                    Text("YOLO + DeepSORT", style = MaterialTheme.typography.bodySmall)
                }
            }

            Button(
                onClick = onNavigateToLatLong,
                modifier = Modifier.weight(1f).height(120.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🗺️", style = MaterialTheme.typography.displayMedium)
                    Text("Lat/Long", style = MaterialTheme.typography.titleMedium)
                    Text("Coming Soon", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}