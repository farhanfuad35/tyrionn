package com.farhan.tyrion

import android.content.Context
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
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

private data class TvButton(val glyph: String, val key: String, val color: Color, val glyphColor: Color = Color.White)

// Colour carries the meaning (she cannot read); glyphs are a second cue. Left column volume, right column channel.
private val volume = listOf(
    TvButton("▲", "KEY_VOLUP", Color(0xFFFF6D00), Color.Black),
    TvButton("🔇", "KEY_MUTE", Color(0xFFD50000)),
    TvButton("▼", "KEY_VOLDOWN", Color(0xFF6A00FF)),
)
private val channel = listOf(
    TvButton("▲", "KEY_CHUP", Color(0xFFFFD600), Color.Black),
    TvButton("▼", "KEY_CHDOWN", Color(0xFF00B8D4), Color.Black),
)

@Composable
fun TvScreen() {
    val context = LocalContext.current
    val remote = remember { TvRemote(context) }
    DisposableEffect(Unit) { onDispose { remote.close() } }
    val haptic = LocalHapticFeedback.current
    Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf(volume, channel).forEach { column ->
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                column.forEach { b ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(b.color, RoundedCornerShape(32.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                remote.press(b.key)
                            },
                        contentAlignment = Alignment.Center
                    ) { Text(b.glyph, color = b.glyphColor, fontSize = 72.sp) }
                }
            }
        }
    }
}
