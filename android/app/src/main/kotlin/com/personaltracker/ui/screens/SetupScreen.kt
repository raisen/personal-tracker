package com.personaltracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.personaltracker.data.AuthManager
import com.personaltracker.data.GistApi
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(onComplete: () -> Unit) {
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var connectedUser by remember { mutableStateOf<String?>(null) }
    var existingGists by remember { mutableStateOf<List<Pair<String, String?>>?>(null) }
    var isCreating by remember { mutableStateOf(false) }
    var manualGistId by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))

        Text(
            "\uD83D\uDCCA Personal Tracker",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Track anything, your way. Data stored securely in a private GitHub Gist.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        // Step 1: Token info
        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("1. Create a GitHub Token", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Create a fine-grained Personal Access Token with Gist read & write permission.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Step 2: Token input
        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("2. Enter Your Token", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Your token is stored only on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("GitHub Token") },
                    placeholder = { Text("github_pat_...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    enabled = !isConnecting && connectedUser == null
                )
                Spacer(Modifier.height(8.dp))

                if (connectedUser == null) {
                    Button(
                        onClick = {
                            scope.launch {
                                isConnecting = true
                                errorMessage = null
                                try {
                                    val user = GistApi.validateToken(token.trim())
                                    connectedUser = user.login
                                    AuthManager.setToken(token.trim())
                                    val gists = GistApi.listGists(token.trim())
                                    existingGists = gists
                                } catch (e: Exception) {
                                    errorMessage = "Connection failed: ${e.message}"
                                } finally {
                                    isConnecting = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = token.isNotBlank() && !isConnecting
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (isConnecting) "Connecting..." else "Connect")
                    }
                } else {
                    Text(
                        "Connected as $connectedUser",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                errorMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Step 3: Choose tracker
        if (connectedUser != null) {
            Spacer(Modifier.height(16.dp))
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("3. Choose Your Tracker", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))

                    val gists = existingGists
                    if (gists != null && gists.isNotEmpty()) {
                        Text("Found existing tracker(s):", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        gists.forEach { (id, desc) ->
                            OutlinedButton(
                                onClick = {
                                    AuthManager.setGistId(id)
                                    onComplete()
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Text("${desc ?: "Personal Tracker"} (${id.take(8)}...)")
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Or start fresh:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                isCreating = true
                                try {
                                    val gistId = GistApi.createTrackerGist(token.trim())
                                    AuthManager.setGistId(gistId)
                                    onComplete()
                                } catch (e: Exception) {
                                    errorMessage = "Failed to create: ${e.message}"
                                } finally {
                                    isCreating = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isCreating
                    ) {
                        Text(if (isCreating) "Creating..." else "Create New Tracker")
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Or enter a Gist ID:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = manualGistId,
                        onValueChange = { manualGistId = it },
                        label = { Text("Gist ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = {
                            if (manualGistId.isNotBlank()) {
                                AuthManager.setGistId(manualGistId.trim())
                                onComplete()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = manualGistId.isNotBlank()
                    ) {
                        Text("Use This Gist")
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}
