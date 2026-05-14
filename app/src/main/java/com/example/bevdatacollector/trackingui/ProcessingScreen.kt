package com.example.bevdatacollector.trackingui

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.bevdatacollector.tracking.Track
import com.example.bevdatacollector.tracking.TrackInfo
import com.example.bevdatacollector.tracking.TrackingService
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "ProcessingScreen"

@Composable
fun ProcessingScreen(
    sessionFolder: File,
    onComplete: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(true) }
    var currentFrame by remember { mutableStateOf(0) }
    var totalFrames by remember { mutableStateOf(0) }
    var tracksInfo by remember { mutableStateOf<List<TrackInfo>>(emptyList()) }
    var statusMessage by remember { mutableStateOf("Initializing...") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isProcessing) {
            Text(
                text = "Processing Session",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LinearProgressIndicator(
                progress = if (totalFrames > 0) currentFrame.toFloat() / totalFrames else 0f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            )

            Text(
                text = "Frame $currentFrame / $totalFrames",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )

            Text(
                text = "Active Tracks: ${tracksInfo.size}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )

            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp)
            ) {
                Text("Cancel")
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = " Processing Complete!",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Session: ${sessionFolder.name}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Total Unique Tracks: ${tracksInfo.size}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(onClick = onBack) {
                            Text("Back to Sessions")
                        }
                        Button(
                            onClick = { onComplete(sessionFolder.absolutePath) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Export Results")
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                Log.d(TAG, " Processing Started ")
                Log.d(TAG, "Session folder: ${sessionFolder.absolutePath}")

                val trackingService = TrackingService(context)
                trackingService.initialize()

                statusMessage = "Loading images..."

                val imageFiles = sessionFolder.listFiles { file ->
                    file.name.endsWith(".jpg") && file.name.startsWith("BEV_")
                }?.sorted() ?: emptyList()

                totalFrames = imageFiles.size
                Log.d(TAG, "Found ${totalFrames} images to process")

                if (totalFrames == 0) {
                    statusMessage = "No images found in session!"
                    isProcessing = false
                    return@launch
                }

                statusMessage = "Processing $totalFrames frames..."
                Log.d(TAG, "Starting tracking service...")

                // Collect all track info across frames
                val allTrackInfo = mutableListOf<TrackInfo>()

                val results = trackingService.processSession(
                    sessionFolder,
                    { current, total, tracks ->
                        currentFrame = current
                        totalFrames = total
                        // Convert Track list to TrackInfo list for display
                        tracksInfo = tracks.map { track ->
                            val tlwh = track.toTlwh()
                            val bottomCenterX = tlwh[0] + tlwh[2] / 2
                            val bottomCenterY = tlwh[1] + tlwh[3]
                            TrackInfo(
                                trackId = track.trackId,
                                objType = track.objType,
                                bbox = floatArrayOf(tlwh[0], tlwh[1], tlwh[0] + tlwh[2], tlwh[1] + tlwh[3]),
                                bottomCenter = floatArrayOf(bottomCenterX, bottomCenterY),
                                confidence = track.hits.toFloat() / (track.age)
                            )
                        }
                        allTrackInfo.addAll(tracksInfo)
                        statusMessage = "Frame $current of $total (${tracks.size} active tracks)"
                        Log.d(TAG, "Progress: $current/$total - ${tracks.size} active tracks")
                    }
                )

                Log.d(TAG, "Processing complete. Total frames processed: ${results.size}")

                statusMessage = "Saving results..."
                Log.d(TAG, "Saving tracking results to CSV...")

                // Save tracking results to CSV
                saveTrackingResults(sessionFolder, results)

                // Update final track count
                tracksInfo = allTrackInfo.distinctBy { it.trackId }
                Log.d(TAG, "Total unique tracks: ${tracksInfo.size}")

                trackingService.close()
                isProcessing = false
                statusMessage = "Complete! Processed ${results.size} frames"

            } catch (e: Exception) {
                Log.e(TAG, "Error during processing: ${e.message}", e)
                statusMessage = "Error: ${e.message}"
                isProcessing = false
            }
        }
    }
}

private fun saveTrackingResults(sessionFolder: File, results: List<com.example.bevdatacollector.tracking.TrackResult>) {
    val outputFile = File(sessionFolder, "tracking_results.csv")
    outputFile.printWriter().use { writer ->
        writer.println("frameIndex,trackId,objType,bbox_x1,bbox_y1,bbox_x2,bbox_y2,bottom_center_x,bottom_center_y,confidence")

        for (result in results) {
            for (track in result.tracks) {
                val bottomCenterX = (track.bbox[0] + track.bbox[2]) / 2
                val bottomCenterY = track.bbox[3]
                writer.println("${result.frameIndex},${track.trackId},${track.objType},${track.bbox[0]},${track.bbox[1]},${track.bbox[2]},${track.bbox[3]},$bottomCenterX,$bottomCenterY,${track.confidence}")
            }
        }
    }
    Log.d(TAG, "Results saved to: ${outputFile.absolutePath}")
}