package com.farhan.tyrion

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import com.farhan.tyrion.ui.theme.TyrionTheme

class MainActivity : ComponentActivity() {
    private var pendingNumber: String? = null
    private lateinit var callPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        callPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                pendingNumber?.let { number -> startCall(number) }
            } else {
                // Fallback: open dialer without placing call
                pendingNumber?.let { number -> startActivity(Intent(Intent.ACTION_DIAL, "tel:$number".toUri())) }
            }
            pendingNumber = null
        }
        enableEdgeToEdge()
        setContent {
            TyrionTheme { DialerGrid(onDial = { dialNumber(it) }) }
        }
    }

    private fun dialNumber(number: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            startCall(number)
        } else {
            pendingNumber = number
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    private fun startCall(number: String) {
        startActivity(Intent(Intent.ACTION_CALL, "tel:$number".toUri()))
    }
}

private data class DialerTile(val label: String, val number: String, val color: Color)

@Composable
fun DialerGrid(
    onDial: (String) -> Unit = {}
) {
    val tiles = listOf(
        DialerTile("Baba", "01713031557", Color(0xFF752978)),
        DialerTile("Amma", "01773657785", Color(0xFF55C951)),
        DialerTile("Niloy", "01763185363", Color(0xFF87BBE8)),
        DialerTile("Kaku", "01844484110", Color(0xFFDB6A07)),
        DialerTile("Tondra", "01797766544", Color(0xFFFA6EFF)),
        DialerTile("Riad", "01779487290", Color(0xFFC9080F))
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 24.dp, horizontal = 8.dp)
    ) {
        Text(
            text = "Hello, Niroty!",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.Black,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            textAlign = TextAlign.Center
        )
        // Grid area fills remaining space
        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
            tiles.chunked(2).forEach { rowTiles ->
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    rowTiles.forEach { tile ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(4.dp)
                                .background(tile.color, shape = MaterialTheme.shapes.medium)
                                .clickable { onDial(tile.number) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tile.label,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    if (rowTiles.size == 1) {
                        Spacer(modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DialerGridPreview() {
    TyrionTheme { DialerGrid() }
}