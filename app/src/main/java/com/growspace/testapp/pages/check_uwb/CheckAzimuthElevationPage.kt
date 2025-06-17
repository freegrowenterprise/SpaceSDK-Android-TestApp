package com.growspace.testapp.pages.check_uwb

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.growspace.sdk.SpaceUwb

@Composable
fun CheckAzimuthElevationPage() {
    val context = LocalContext.current as ComponentActivity
    val spaceUWB = remember { SpaceUwb(context, context) }

    val isAzimuthSupported = remember { mutableStateOf<Boolean?>(null) }
    val isDistanceSupported = remember { mutableStateOf<Boolean?>(null) }
    val isElevationSupported = remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        spaceUWB.checkAzimuthElevationSupport { azimuth, distance, elevation, _, _, _ ->
            isAzimuthSupported.value = azimuth
            isDistanceSupported.value = distance
            isElevationSupported.value = elevation
        }
    }


    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "UWB Feature Support",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(12.dp))

            FeatureSupportCard("Distance", isDistanceSupported.value)
            FeatureSupportCard("Azimuth", isAzimuthSupported.value)
            FeatureSupportCard("Elevation", isElevationSupported.value)
        }
    }

}

@Composable
fun FeatureSupportCard(label: String, isSupported: Boolean?) {
    val (emoji, desc, color) = when (isSupported) {
        true -> Triple("✅", "Supported", Color(0xFF2E7D32))       // 짙은 녹색
        false -> Triple("❌", "Not Supported", Color(0xFFD32F2F))   // 짙은 빨간색
        null -> Triple("⏳", "Checking...", Color(0xFF757575))      // 중간 회색
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF9F9F9) // 밝은 회색 배경
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Black,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$emoji $desc",
                color = color,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}