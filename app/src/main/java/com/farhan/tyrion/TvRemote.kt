package com.farhan.tyrion

import android.content.Context
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

// ponytail: fixed IP (reserve it in the router), no discovery. Add SSDP if the address ever changes.
private const val TV_HOST = "192.168.0.110"

// The TV serves a self-signed cert, so trust everything. Acceptable only because it is a LAN-only call to one fixed host.
private val trustAll = object : X509TrustManager {
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

private val client = OkHttpClient.Builder()
    .sslSocketFactory(SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), SecureRandom()) }.socketFactory, trustAll)
    .hostnameVerifier { _, _ -> true }
    .build()

/** Samsung Tizen remote over WebSocket. The first connect shows an Allow popup on the TV; the token it returns is saved. */
class TvRemote(context: Context) {
    private val prefs = context.getSharedPreferences("tv", Context.MODE_PRIVATE)
    private var ws: WebSocket? = null
    private var ready = false
    private var pending: String? = null

    @Synchronized
    fun press(key: String) {
        if (ready) ws?.send(keyJson(key)) else {
            pending = key
            if (ws == null) connect()
        }
    }

    @Synchronized
    fun close() {
        ws?.close(1000, null)
        ws = null
        ready = false
    }

    private fun connect() {
        val name = Base64.encodeToString("Tyrion".toByteArray(), Base64.NO_WRAP)
        val token = prefs.getString("token", null)?.let { "&token=$it" } ?: ""
        val url = "wss://$TV_HOST:8002/api/v2/channels/samsung.remote.control?name=$name$token"
        ws = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = JSONObject(text)
                if (msg.optString("event") != "ms.channel.connect") return
                msg.optJSONObject("data")?.optString("token")?.takeIf { it.isNotEmpty() }
                    ?.let { prefs.edit().putString("token", it).apply() }
                synchronized(this@TvRemote) {
                    ready = true
                    pending?.let { webSocket.send(keyJson(it)) }
                    pending = null
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = dropped(webSocket)
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = dropped(webSocket)
        })
    }

    @Synchronized
    private fun dropped(socket: WebSocket) {
        if (socket != ws) return
        ws = null
        ready = false
    }

    private fun keyJson(key: String) = JSONObject(
        mapOf(
            "method" to "ms.remote.control",
            "params" to mapOf("Cmd" to "Click", "DataOfCmd" to key, "Option" to "false", "TypeOfRemote" to "SendRemoteKey")
        )
    ).toString()
}

private data class TvButton(val glyph: String, val label: String, val key: String, val color: Color, val glyphColor: Color = Color.White)

// Colour carries the meaning (she cannot read); glyphs are a second cue; the small label is for the caregiver.
@Composable
private fun Glyph(b: TvButton, modifier: Modifier = Modifier, size: TextUnit = 40.sp) =
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(b.glyph, color = b.glyphColor, fontSize = size)
        Text(b.label, color = b.glyphColor, fontSize = 11.sp)
    }

private val up = TvButton("▲", "UP", "KEY_UP", Color(0xFFFF6D00), Color.Black)
private val down = TvButton("▼", "DOWN", "KEY_DOWN", Color(0xFFFFD600), Color.Black)
private val left = TvButton("◀", "LEFT", "KEY_LEFT", Color(0xFF6A00FF))
private val right = TvButton("▶", "RIGHT", "KEY_RIGHT", Color(0xFF00B8D4), Color.Black)
private val enter = TvButton("●", "OK", "KEY_ENTER", Color(0xFFD50000))
private val volDown = TvButton("−", "VOL DOWN", "KEY_VOLDOWN", Color(0xFFE91E63))
private val volUp = TvButton("+", "VOL UP", "KEY_VOLUP", Color(0xFF76FF03), Color.Black)

// Tap = one step on release (nothing if the finger slides off). Hold past HOLD_DELAY_MS = repeat every REPEAT_MS, capped at
// MAX_REPEATS so a stuck or accidental long touch cannot run the volume away (tremor).
private const val HOLD_DELAY_MS = 500L
private const val REPEAT_MS = 200L
private const val MAX_REPEATS = 15

private fun Modifier.repeatOnHold(label: String, action: () -> Unit) = semantics { onClick(label) { action(); true } }
    .pointerInput(Unit) {
        detectTapGestures(onPress = {
            coroutineScope {
                var held = false
                val repeater = launch {
                    delay(HOLD_DELAY_MS)
                    held = true
                    repeat(MAX_REPEATS) { action(); delay(REPEAT_MS) }
                }
                val released = tryAwaitRelease()
                repeater.cancel()
                if (released && !held) action()
            }
        })
    }

private const val HOLE = 0.41f // centre button radius as a fraction of the pad radius

// Round d-pad like the physical remote: a ring of four direction segments around the centre button.
@Composable
private fun DPad(diameter: Dp, onPress: (TvButton) -> Unit) {
    // button, ring segment start angle (degrees, clockwise from 3 o'clock), glyph direction
    val segments = listOf(
        Triple(right, -45f, 1 to 0), Triple(down, 45f, 0 to 1), Triple(left, 135f, -1 to 0), Triple(up, 225f, 0 to -1),
    )
    val glyphRadius = diameter / 2 * ((1 + HOLE) / 2)
    Box(
        Modifier.size(diameter).pointerInput(Unit) {
            // Fires on release and not at all if the finger drags, which absorbs tremor.
            detectTapGestures { p ->
                val c = this.size.width / 2f
                val dx = p.x - c
                val dy = p.y - c
                val r = hypot(dx, dy)
                if (r <= c) onPress(
                    when {
                        r < HOLE * c -> enter
                        abs(dx) > abs(dy) -> if (dx > 0) right else left
                        else -> if (dy > 0) down else up
                    }
                )
            }
        },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = this.size.width / 2
            val ringMid = c * (1 + HOLE) / 2
            segments.forEach { (b, start, _) ->
                drawArc(
                    b.color, start + 2, 86f, false,
                    Offset(c - ringMid, c - ringMid), Size(2 * ringMid, 2 * ringMid),
                    style = Stroke(c * (1 - HOLE))
                )
            }
            drawCircle(enter.color, c * HOLE * 0.96f)
        }
        segments.forEach { (b, _, d) ->
            Glyph(b, Modifier.offset(glyphRadius * d.first, glyphRadius * d.second))
        }
        Glyph(enter)
    }
}

@Composable
fun TvScreen() {
    val context = LocalContext.current
    val remote = remember { TvRemote(context) }
    DisposableEffect(Unit) { onDispose { remote.close() } }
    val haptic = LocalHapticFeedback.current
    val press = { b: TvButton ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        remote.press(b.key)
    }
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 32.dp, vertical = 48.dp)) {
        // Square pad no wider than the screen, always leaving room for the volume row.
        val diameter = minOf(maxWidth, maxHeight - 240.dp)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(48.dp)) {
            DPad(diameter, press)
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                listOf(volDown, volUp).forEach { b ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(b.color, RoundedCornerShape(32.dp))
                            .repeatOnHold(b.label) { press(b) },
                        contentAlignment = Alignment.Center
                    ) { Glyph(b, size = 56.sp) }
                }
            }
        }
    }
}
