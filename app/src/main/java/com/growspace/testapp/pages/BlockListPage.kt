package com.growspace.testapp.pages

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.growspace.sdk.SpaceUwb

@Composable
fun BlockListPage() {
    val context = LocalContext.current as ComponentActivity
    val spaceUWB = remember { SpaceUwb(context, context) }

    val blocked = remember { mutableStateListOf<String>() }
    val showClearConfirm = remember { mutableStateOf(false) }
    val unblockTarget = remember { mutableStateOf<String?>(null) }

    fun reload() {
        blocked.clear()
        blocked.addAll(spaceUWB.getBlockedDevices().sorted())
    }

    LaunchedEffect(Unit) { reload() }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "차단 목록 (${blocked.size})",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                if (blocked.isNotEmpty()) {
                    TextButton(
                        onClick = { showClearConfirm.value = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("전체 해제")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "차단된 디바이스는 unblock 호출 전까지 자동 재연결되지 않습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Spacer(Modifier.height(12.dp))

            if (blocked.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("차단된 디바이스가 없습니다", color = Color.Gray)
                }
            } else {
                LazyColumn {
                    items(blocked) { name ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { unblockTarget.value = name }) {
                                    Text("Unblock")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm.value) {
        AlertDialog(
            onDismissRequest = { showClearConfirm.value = false },
            title = { Text("전체 해제") },
            text = { Text("${blocked.size} 개 디바이스를 모두 차단 해제할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    spaceUWB.clearBlockedDevices()
                    showClearConfirm.value = false
                    reload()
                }) { Text("해제") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm.value = false }) { Text("취소") }
            }
        )
    }

    val target = unblockTarget.value
    if (target != null) {
        AlertDialog(
            onDismissRequest = { unblockTarget.value = null },
            title = { Text(target) },
            text = { Text("차단을 해제할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    spaceUWB.unblockDevice(target)
                    unblockTarget.value = null
                    reload()
                }) { Text("Unblock") }
            },
            dismissButton = {
                TextButton(onClick = { unblockTarget.value = null }) { Text("취소") }
            }
        )
    }
}
