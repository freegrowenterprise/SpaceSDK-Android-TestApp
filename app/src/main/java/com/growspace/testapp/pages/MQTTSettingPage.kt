package com.growspace.testapp.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.growspace.testapp.MQTTManager

@Composable
fun MQTTSettingPage(navController: NavHostController) {
    val context = LocalContext.current
    val mqttManager = remember { MQTTManager(context) }

    var hostInput by remember { mutableStateOf("3.38.52.15") }
    var portInput by remember { mutableStateOf("1883") }
    var usernameInput by remember { mutableStateOf("freegrow") }
    var passwordInput by remember { mutableStateOf("gogrow!") }
    var clientIdInput by remember { mutableStateOf("") }
    var coordinateTopicInput by remember { mutableStateOf("uwb/coordinate") }
    var distanceTopicInput by remember { mutableStateOf("uwb/distance") }

    var connectionStatus by remember { mutableStateOf("Disconnected") }
    var isConnected by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "MQTT Settings",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Broker Host
        Text(
            "Broker Host",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = hostInput,
            onValueChange = { hostInput = it },
            placeholder = { Text("3.38.52.15") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Broker Port
        Text(
            "Broker Port",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = portInput,
            onValueChange = { portInput = it },
            placeholder = { Text("1883") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Username
        Text(
            "Username",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = usernameInput,
            onValueChange = { usernameInput = it },
            placeholder = { Text("freegrow") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Password
        Text(
            "Password",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = passwordInput,
            onValueChange = { passwordInput = it },
            placeholder = { Text("gogrow!") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Client ID
        Text(
            "Client ID (Optional)",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = clientIdInput,
            onValueChange = { clientIdInput = it },
            placeholder = { Text("Auto-generated if empty") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Coordinate Topic
        Text(
            "Coordinate Topic",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = coordinateTopicInput,
            onValueChange = { coordinateTopicInput = it },
            placeholder = { Text("uwb/coordinate") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Distance Topic
        Text(
            "Distance Topic",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = distanceTopicInput,
            onValueChange = { distanceTopicInput = it },
            placeholder = { Text("uwb/distance") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Connection Status
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336)
            )
        ) {
            Text(
                text = connectionStatus,
                color = Color.White,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    mqttManager.disconnect()
                    connectionStatus = "Disconnected"
                    isConnected = false
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) {
                Text("Disconnect")
            }

            Button(
                onClick = {
                    if (hostInput.isBlank()) {
                        errorMessage = "Please enter broker host"
                        showErrorDialog = true
                        return@Button
                    }

                    val port = portInput.toIntOrNull()
                    if (port == null || port !in 1..65535) {
                        errorMessage = "Please enter valid port number (1-65535)"
                        showErrorDialog = true
                        return@Button
                    }

                    // Save settings
                    mqttManager.coordinateTopic = coordinateTopicInput.ifBlank { "uwb/coordinate" }
                    mqttManager.distanceTopic = distanceTopicInput.ifBlank { "uwb/distance" }

                    val clientId = clientIdInput.ifBlank { null }
                    val username = usernameInput.ifBlank { null }
                    val password = passwordInput.ifBlank { null }

                    mqttManager.connect(
                        host = hostInput,
                        port = port,
                        clientId = clientId,
                        username = username,
                        password = password,
                        onSuccess = {
                            connectionStatus = "Connected to $hostInput:$port"
                            isConnected = true
                        },
                        onFailure = { error ->
                            connectionStatus = "Failed: ${error.message}"
                            isConnected = false
                        }
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Connect")
            }
        }
    }

    // Error Dialog
    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("MQTT Settings") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}
