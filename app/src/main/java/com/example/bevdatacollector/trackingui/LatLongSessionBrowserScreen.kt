package com.example.bevdatacollector.trackingui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.bevdatacollector.projection.LatLongConverter
import java.io.File

@Composable
fun LatLongSessionBrowserScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sessions = remember { getSessionsWithTrackingResults(context) }
    var selectedSession by remember { mutableStateOf<File?>(null) }
    var isConverting by remember { mutableStateOf(false) }
    var conversionResult by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Select Session for Lat/Long Conversion",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (conversionResult != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = conversionResult!!,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        LazyColumn {
            items(sessions) { session ->
                SessionCard(
                    session = session,
                    isProcessing = isConverting && selectedSession == session,
                    onSelect = {
                        selectedSession = session
                        isConverting = true
                        conversionResult = null

                        val converter = LatLongConverter(context)
                        val outputFile = converter.convertSession(session)

                        isConverting = false
                        if (outputFile != null) {
                            conversionResult = " Converted! Saved to:\n${outputFile.name}"
                        } else {
                            conversionResult = " Conversion failed. Check if tracking_results.csv exists."
                        }
                    }
                )
            }
        }

        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Text("← Back")
        }
    }
}

@Composable
fun SessionCard(session: File, isProcessing: Boolean, onSelect: () -> Unit) {
    val trackingFile = File(session, "tracking_results.csv")
    val hasResults = trackingFile.exists()
    val imageCount = session.listFiles { file ->
        file.name.endsWith(".jpg") && file.name.startsWith("BEV_")
    }?.size ?: 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        onClick = { if (!isProcessing && hasResults) onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (!hasResults) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = session.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "$imageCount images | ${if (hasResults) " tracking results" else " no tracking results"}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else if (hasResults) {
                Text("→", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

private fun getSessionsWithTrackingResults(context: android.content.Context): List<File> {
    val rootDir = context.getExternalFilesDir(null)
    return rootDir?.listFiles { file ->
        file.isDirectory && file.name.startsWith("Session_")
    }?.filter { session ->
        File(session, "tracking_results.csv").exists()
    }?.sortedByDescending { it.name } ?: emptyList()
}