package com.example.data.supabase

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

enum class SupabaseRealtimeConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

data class SupabaseRealtimeStatus(
    val state: SupabaseRealtimeConnectionState = SupabaseRealtimeConnectionState.DISCONNECTED,
    val detail: String = "Realtime idle"
)

data class SupabaseRealtimeEvent(
    val table: String,
    val eventType: String,
    val recordJson: JSONObject? = null,
    val recordFields: Map<String, String> = emptyMap(),
    val oldRecordJson: JSONObject? = null,
    val oldRecordFields: Map<String, String> = emptyMap()
)

class SupabaseRealtimeClient(
    private val rawBaseUrl: String = BuildConfig.SUPABASE_URL,
    private val rawAnonKey: String = BuildConfig.SUPABASE_ANON_KEY,
    private val httpClient: OkHttpClient = defaultWebSocketClient
) {
    private val _connectionState = MutableStateFlow(SupabaseRealtimeStatus())
    val connectionState: StateFlow<SupabaseRealtimeStatus> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<SupabaseRealtimeEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<SupabaseRealtimeEvent> = _events.asSharedFlow()

    private val refCounter = AtomicInteger(1)
    @Volatile private var activeScope: CoroutineScope? = null
    @Volatile private var tokenProvider: () -> String? = { MomiSupabaseClient.activeSession?.accessToken }
    @Volatile private var activeSocket: WebSocket? = null
    @Volatile private var heartbeatJob: Job? = null
    @Volatile private var reconnectJob: Job? = null
    @Volatile private var stopped = true
    @Volatile private var reconnectAttempts = 0
    @Volatile private var usedWildcardFallback = false

    fun isConfigured(): Boolean = MomiSupabaseSchema.isConfigured(rawBaseUrl, rawAnonKey)

    fun connect(
        scope: CoroutineScope,
        forceReconnect: Boolean = false,
        accessTokenProvider: () -> String? = { MomiSupabaseClient.activeSession?.accessToken }
    ) {
        if (!isConfigured()) {
            _connectionState.value = SupabaseRealtimeStatus(
                state = SupabaseRealtimeConnectionState.DISCONNECTED,
                detail = "Supabase credentials not configured"
            )
            return
        }
        val alreadyRunning = !stopped && activeSocket != null &&
            _connectionState.value.state != SupabaseRealtimeConnectionState.DISCONNECTED
        activeScope = scope
        tokenProvider = accessTokenProvider
        if (alreadyRunning && !forceReconnect) {
            updateAccessToken(accessTokenProvider())
            return
        }
        stopped = false
        reconnectAttempts = 0
        usedWildcardFallback = false
        openSocket()
    }

    /**
     * Registers a coroutine collector for specific Postgres tables (e.g. posts, comments,
     * user_interactions / likes) upon user login, ensuring instant UI synchronization.
     */
    fun registerTableListeners(
        scope: CoroutineScope,
        tables: Collection<String>,
        onEvent: (SupabaseRealtimeEvent) -> Unit
    ): Job {
        val watched = tables.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        return scope.launch {
            events.collect { event ->
                if (watched.isEmpty() || event.table.trim().lowercase() in watched) {
                    onEvent(event)
                }
            }
        }
    }

    fun updateAccessToken(accessToken: String?) {
        val clean = accessToken?.trim().orEmpty()
        val socket = activeSocket ?: return
        if (clean.isEmpty()) return
        val frame = JSONObject().apply {
            put("topic", REALTIME_TOPIC)
            put("event", "access_token")
            put("payload", JSONObject().put("access_token", clean))
            put("ref", refCounter.getAndIncrement().toString())
        }
        socket.send(frame.toString())
    }

    fun disconnect() {
        stopped = true
        reconnectJob?.cancel()
        reconnectJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        activeSocket?.close(1000, "Client closing")
        activeSocket = null
        _connectionState.value = SupabaseRealtimeStatus(
            state = SupabaseRealtimeConnectionState.DISCONNECTED,
            detail = "Realtime disconnected"
        )
    }

    private fun openSocket() {
        if (stopped) return
        val wsUrl = buildRealtimeWebSocketUrl(rawBaseUrl, rawAnonKey)
        if (wsUrl.isEmpty()) return

        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        activeSocket?.cancel()
        activeSocket = null

        _connectionState.value = SupabaseRealtimeStatus(
            state = if (reconnectAttempts > 0) {
                SupabaseRealtimeConnectionState.RECONNECTING
            } else {
                SupabaseRealtimeConnectionState.CONNECTING
            },
            detail = if (reconnectAttempts > 0) {
                "Reconnecting Realtime WebSocket..."
            } else {
                "Connecting Supabase Realtime..."
            }
        )

        val request = Request.Builder().url(wsUrl).build()
        activeSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempts = 0
                usedWildcardFallback = false
                val token = tokenProvider()?.trim().takeUnless { it.isNullOrEmpty() }
                    ?: rawAnonKey.trim().takeIf { MomiSupabaseSchema.isLegacyJwtKey(it) }
                val joinMsg = buildJoinPayload(
                    topic = REALTIME_TOPIC,
                    ref = refCounter.getAndIncrement().toString(),
                    accessToken = token,
                    tables = MomiSupabaseSchema.TABLES.toList()
                )
                webSocket.send(joinMsg)
                startHeartbeat(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(webSocket, text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!stopped && webSocket == activeSocket) {
                    scheduleReconnect("Socket closed ($code)")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Supabase Realtime WebSocket error: ${t.message}")
                if (!stopped && webSocket == activeSocket) {
                    scheduleReconnect(t.message ?: "WebSocket connection interrupted")
                }
            }
        })
    }

    private fun startHeartbeat(webSocket: WebSocket) {
        heartbeatJob?.cancel()
        val scope = activeScope ?: return
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive && !stopped && activeSocket == webSocket) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (stopped || activeSocket != webSocket) break
                val hb = JSONObject().apply {
                    put("topic", "phoenix")
                    put("event", "heartbeat")
                    put("payload", JSONObject())
                    put("ref", refCounter.getAndIncrement().toString())
                }
                if (!webSocket.send(hb.toString())) {
                    break
                }
            }
        }
    }

    private fun scheduleReconnect(reason: String) {
        heartbeatJob?.cancel()
        activeSocket = null
        if (stopped) return
        val scope = activeScope ?: return
        reconnectJob?.cancel()
        reconnectAttempts += 1
        val backoffMs = minOf(20_000L, 1_500L * (1L shl minOf(4, reconnectAttempts - 1)))
        _connectionState.value = SupabaseRealtimeStatus(
            state = SupabaseRealtimeConnectionState.RECONNECTING,
            detail = "Reconnecting Realtime ($reason)..."
        )
        reconnectJob = scope.launch(Dispatchers.IO) {
            delay(backoffMs)
            if (!stopped) {
                openSocket()
            }
        }
    }

    private fun handleIncomingMessage(webSocket: WebSocket, rawText: String) {
        val json = runCatching { JSONObject(rawText) }.getOrNull() ?: return
        val eventName = json.optString("event")
        when (eventName) {
            "phx_reply" -> {
                val topic = json.optString("topic")
                val status = json.optJSONObject("payload")?.optString("status")
                if (topic == REALTIME_TOPIC) {
                    if (status == "ok") {
                        _connectionState.value = SupabaseRealtimeStatus(
                            state = SupabaseRealtimeConnectionState.CONNECTED,
                            detail = "Realtime WebSocket Live (Posts, Comments & Likes)"
                        )
                    } else if (!usedWildcardFallback && !stopped && webSocket == activeSocket) {
                        usedWildcardFallback = true
                        val token = tokenProvider()?.trim().takeUnless { it.isNullOrEmpty() }
                            ?: rawAnonKey.trim().takeIf { MomiSupabaseSchema.isLegacyJwtKey(it) }
                        webSocket.send(
                            buildJoinPayload(
                                topic = REALTIME_TOPIC,
                                ref = refCounter.getAndIncrement().toString(),
                                accessToken = token,
                                tables = emptyList()
                            )
                        )
                    }
                }
            }
            "postgres_changes" -> {
                parseRealtimeChangeEvent(json)?.let { ev ->
                    _events.tryEmit(ev)
                }
            }
            "phx_error" -> {
                if (!stopped) scheduleReconnect("Channel error")
            }
        }
    }

    companion object {
        private const val TAG = "SupabaseRealtime"
        const val REALTIME_TOPIC = "realtime:public"
        private const val HEARTBEAT_INTERVAL_MS = 30_000L

        val SOCIAL_ENGAGEMENT_TABLES: List<String> = listOf(
            "posts",
            "comments",
            "user_interactions",
            "likes"
        )

        private val defaultWebSocketClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .pingInterval(25, TimeUnit.SECONDS)
                .build()
        }

        fun buildRealtimeWebSocketUrl(rawBaseUrl: String?, rawAnonKey: String?): String {
            val base = MomiSupabaseSchema.normalizeSupabaseBaseUrl(rawBaseUrl)
            val key = rawAnonKey?.trim().orEmpty()
            if (base.isEmpty() || !MomiSupabaseSchema.isSafeClientAnonKey(key)) return ""
            val wssBase = base.replaceFirst("https://", "wss://")
            val encodedKey = URLEncoder.encode(key, "UTF-8")
            return "$wssBase/realtime/v1/websocket?apikey=$encodedKey&vsn=1.0.0"
        }

        private fun escapeJsonString(raw: String): String {
            val sb = StringBuilder(raw.length + 8)
            for (ch in raw) {
                when (ch) {
                    '\\' -> sb.append("\\\\")
                    '"' -> sb.append("\\\"")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else -> sb.append(ch)
                }
            }
            return sb.toString()
        }

        fun buildJoinPayload(
            topic: String = REALTIME_TOPIC,
            ref: String = "1",
            accessToken: String? = null,
            tables: List<String> = MomiSupabaseSchema.TABLES.toList()
        ): String {
            val cleanToken = accessToken?.trim()?.takeIf { it.isNotEmpty() }
            val tokenPart = if (cleanToken != null) {
                ""","access_token":"${escapeJsonString(cleanToken)}""""
            } else {
                ""
            }
            val cleanTables = tables.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            val postgresChangesJson = if (cleanTables.isEmpty()) {
                """[{"event":"*","schema":"public"}]"""
            } else {
                cleanTables.joinToString(prefix = "[", postfix = "]", separator = ",") { tbl ->
                    """{"event":"*","schema":"public","table":"${escapeJsonString(tbl)}"}"""
                }
            }
            return """{"topic":"${escapeJsonString(topic)}","event":"phx_join","payload":{"config":{"broadcast":{"self":false},"presence":{"key":""},"postgres_changes":$postgresChangesJson}$tokenPart},"ref":"${escapeJsonString(ref)}"}"""
        }

        private fun extractRecordFields(obj: JSONObject?): Map<String, String> = buildMap {
            if (obj != null) {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = obj.opt(k)
                    if (v != null && v != JSONObject.NULL) {
                        put(k, v.toString())
                    }
                }
            }
        }

        fun parseRealtimeChangeEvent(json: JSONObject): SupabaseRealtimeEvent? {
            if (json.optString("event") != "postgres_changes") return null
            val payload = json.optJSONObject("payload") ?: return null
            val data = payload.optJSONObject("data") ?: payload
            val table = data.optString("table").trim()
            if (table.isEmpty()) return null
            val type = data.optString("type")
                .ifBlank { data.optString("eventType") }
                .ifBlank { "UPDATE" }
                .uppercase()
            val record = data.optJSONObject("record") ?: data.optJSONObject("new")
            val oldRecord = data.optJSONObject("old_record") ?: data.optJSONObject("old")
            return SupabaseRealtimeEvent(
                table = table,
                eventType = type,
                recordJson = record,
                recordFields = extractRecordFields(record),
                oldRecordJson = oldRecord,
                oldRecordFields = extractRecordFields(oldRecord)
            )
        }

        fun parseRealtimeChangeMessage(rawText: String): SupabaseRealtimeEvent? {
            val jsonAttempt = runCatching {
                val obj = JSONObject(rawText)
                parseRealtimeChangeEvent(obj)
            }
            if (jsonAttempt.isSuccess) return jsonAttempt.getOrNull()

            // Fallback parser for local JVM unit tests where android.jar's org.json throws Stub!
            if (!Regex(""""event"\s*:\s*"postgres_changes"""").containsMatchIn(rawText)) return null
            val table = Regex(""""table"\s*:\s*"([^"]+)"""").find(rawText)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            if (table.isEmpty()) return null
            val type = (
                Regex(""""type"\s*:\s*"([^"]+)"""").find(rawText)?.groupValues?.getOrNull(1)
                    ?: Regex(""""eventType"\s*:\s*"([^"]+)"""").find(rawText)?.groupValues?.getOrNull(1)
                    ?: "UPDATE"
                ).trim().uppercase()
            fun parseFieldsFor(pattern: String): Map<String, String> = buildMap {
                val match = Regex(pattern).find(rawText)
                val body = match?.groupValues?.getOrNull(1).orEmpty()
                Regex(""""([^"]+)"\s*:\s*"([^"]*)"""").findAll(body).forEach { m ->
                    put(m.groupValues[1], m.groupValues[2])
                }
            }
            return SupabaseRealtimeEvent(
                table = table,
                eventType = type,
                recordJson = null,
                recordFields = parseFieldsFor(""""(?:record|new)"\s*:\s*\{([^}]*)\}"""),
                oldRecordJson = null,
                oldRecordFields = parseFieldsFor(""""(?:old_record|old)"\s*:\s*\{([^}]*)\}""")
            )
        }
    }
}
