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
import androidx.navigation.NavController
import java.io.File
import java.net.URLEncoder

@Composable
fun SessionBrowserScreen(
    onSessionSelected: (File) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sessions = remember { getSessionFolders(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Select Session to Process",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn {
            items(sessions) { session ->
                SessionCard(
                    session = session,
                    onSelect = { onSessionSelected(session) }
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
fun SessionCard(session: File, onSelect: () -> Unit) {
    val imageCount = session.listFiles { file ->
        file.name.endsWith(".jpg") && file.name.startsWith("BEV_")
    }?.size ?: 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        onClick = onSelect
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
                    text = "$imageCount images",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text("→", style = MaterialTheme.typography.titleLarge)
        }
    }
}

private fun getSessionFolders(context: android.content.Context): List<File> {
    val rootDir = context.getExternalFilesDir(null)
    return rootDir?.listFiles { file ->
        file.isDirectory && file.name.startsWith("Session_")
    }?.sortedByDescending { it.name } ?: emptyList()
}