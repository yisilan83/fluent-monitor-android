package com.fluent.monitor.data.remote

import android.util.Log
import com.fluent.monitor.data.model.FluentMessage
import com.squareup.moshi.Moshi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WebSocketManager"

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

@Singleton
class WebSocketManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi
) {
    private var webSocket: WebSocket? = null
    private val adapter = moshi.adapter(FluentMessage::class.java)

    private val _connectionState = MutableSharedFlow<ConnectionState>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val connectionState: SharedFlow<ConnectionState> = _connectionState

    private val _messages = MutableSharedFlow<FluentMessage>(
        replay = 0,
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messages: SharedFlow<FluentMessage> = _messages

    init {
        _connectionState.tryEmit(ConnectionState.Disconnected)
    }

    fun connect(url: String) {
        disconnect()
        _connectionState.tryEmit(ConnectionState.Connecting)

        val request = Request.Builder().url(url).build()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected: ${response.message}")
                _connectionState.tryEmit(ConnectionState.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val message = adapter.fromJson(text)
                    if (message != null) {
                        _messages.tryEmit(message)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse message: ${text.take(120)}", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                _connectionState.tryEmit(ConnectionState.Disconnected)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure", t)
                _connectionState.tryEmit(ConnectionState.Error(t.message ?: "Unknown error"))
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connectionState.tryEmit(ConnectionState.Disconnected)
    }
}
