/*
 * Copyright 2024 LiveKit, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.livekit.plugin

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.annotation.SuppressLint
import androidx.annotation.NonNull
import io.flutter.plugin.common.EventChannel
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

import com.cloudwebrtc.webrtc.FlutterWebRTCPlugin
import com.cloudwebrtc.webrtc.audio.LocalAudioTrack
import io.flutter.plugin.common.BinaryMessenger
import org.webrtc.AudioTrack
import java.net.DatagramSocket
import java.net.Socket
import java.util.UUID
/** LiveKitPlugin */
class LiveKitPlugin: FlutterPlugin, MethodCallHandler, EventChannel.StreamHandler {
  private var processors = mutableMapOf<String, Visualizer>()
  private var flutterWebRTCPlugin = FlutterWebRTCPlugin.sharedSingleton
  private var binaryMessenger: BinaryMessenger? = null
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
    private lateinit var channel : MethodChannel
    private lateinit var eventChannel: EventChannel
    private lateinit var context: Context
    private lateinit var audioManager: AudioManager
    private lateinit var networkQoSManager: NetworkQoSManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioFocusChangeListener: AudioManager.OnAudioFocusChangeListener? = null
    private var eventSink: EventChannel.EventSink? = null

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "livekit_client")
    channel.setMethodCallHandler(this)

    eventChannel = EventChannel(flutterPluginBinding.binaryMessenger, "livekit_plugin_events")
    eventChannel.setStreamHandler(this)
    
    context = flutterPluginBinding.applicationContext
    audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    networkQoSManager = NetworkQoSManager(context)
    binaryMessenger = flutterPluginBinding.binaryMessenger
  }

    private fun registerSocket(call: MethodCall, result: Result) {
        try {
            val socketType = call.argument<String>("socketType") ?: ""
            val socketId = call.argument<String>("socketId") ?: UUID.randomUUID().toString()

            // In a real implementation, you'd create the actual socket here
            // For now, we'll create a placeholder and register it
            val socket: Any = when (socketType.lowercase()) {
                "udp" -> DatagramSocket()
                "tcp" -> Socket()
                else -> {
                    result.error("INVALID_SOCKET_TYPE", "Invalid socket type: $socketType", null)
                    return
                }
            }

            val success = networkQoSManager.registerSocket(socketId, socket)
            if (success) {
                result.success(socketId)
            } else {
                result.error("SOCKET_REGISTRATION_FAILED", "Failed to register socket", null)
            }
        } catch (e: Exception) {
            result.error("SOCKET_REGISTRATION_ERROR", "Error registering socket: ${e.message}", null)
        }
    }

    private fun unregisterSocket(call: MethodCall, result: Result) {
        try {
            val socketId = call.argument<String>("socketId") ?: ""
            if (socketId.isNotEmpty()) {
                val success = networkQoSManager.unregisterSocket(socketId)
                result.success(success)
            } else {
                result.error("INVALID_SOCKET_ID", "Invalid socket ID", null)
            }
        } catch (e: Exception) {
            result.error("SOCKET_UNREGISTRATION_ERROR", "Error unregistering socket: ${e.message}", null)
        }
    }

    private fun setUDPQoS(call: MethodCall, result: Result) {
        try {
            val dscpValue = call.argument<Int>("dscpValue") ?: NetworkQoSManager.DSCP_CS0
            val socketId = call.argument<String>("socketId") ?: ""

            if (socketId.isNotEmpty()) {
                val success = networkQoSManager.setUDPQoS(socketId, dscpValue)
                result.success(success)
            } else {
                result.error("INVALID_SOCKET_ID", "Invalid socket ID", null)
            }
        } catch (e: Exception) {
            result.error("UDP_QOS_ERROR", "Failed to set UDP QoS", e.message)
        }
    }

    private fun setTCPQoS(call: MethodCall, result: Result) {
        try {
            val dscpValue = call.argument<Int>("dscpValue") ?: NetworkQoSManager.DSCP_CS0
            val socketId = call.argument<String>("socketId") ?: ""

            if (socketId.isNotEmpty()) {
                val success = networkQoSManager.setTCPQoS(socketId, dscpValue)
                result.success(success)
            } else {
                result.error("INVALID_SOCKET_ID", "Invalid socket ID", null)
            }
        } catch (e: Exception) {
            result.error("TCP_QOS_ERROR", "Failed to set TCP QoS", e.message)
        }
    }

    private fun setAdaptiveQoS(call: MethodCall, result: Result) {
        try {
            val socketId = call.argument<String>("socketId") ?: ""

            if (socketId.isNotEmpty()) {
                val success = networkQoSManager.setAdaptiveQoS(socketId)
                result.success(success)
            } else {
                result.error("INVALID_SOCKET_ID", "Invalid socket ID", null)
            }
        } catch (e: Exception) {
            result.error("ADAPTIVE_QOS_ERROR", "Failed to set adaptive QoS", e.message)
        }
    }

    private fun getNetworkType(result: Result) {
        try {
            val networkType = networkQoSManager.getCurrentNetworkType()
            result.success(networkType)
        } catch (e: Exception) {
            result.error("NETWORK_TYPE_ERROR", "Failed to get network type", e.message)
        }
    }

    private fun getRegisteredSocketIds(result: Result) {
        try {
            val socketIds = networkQoSManager.getRegisteredSocketIds()
            result.success(socketIds)
        } catch (e: Exception) {
            result.error("SOCKET_IDS_ERROR", "Failed to get registered socket IDs", e.message)
        }
    }


  @SuppressLint("SuspiciousIndentation")
  private fun handleStartVisualizer(@NonNull call: MethodCall, @NonNull result: Result) {
    val trackId = call.argument<String>("trackId")
    val visualizerId = call.argument<String>("visualizerId")
    if (trackId == null || visualizerId == null) {
      result.error("INVALID_ARGUMENT", "trackId and visualizerId is required", null)
      return
    }
    var audioTrack: LKAudioTrack? = null
    val barCount = call.argument<Int>("barCount") ?: 7
    val isCentered = call.argument<Boolean>("isCentered") ?: true
    var smoothTransition = call.argument<Boolean>("smoothTransition") ?: true

    val track = flutterWebRTCPlugin.getLocalTrack(trackId)
    if (track != null) {
      audioTrack = LKLocalAudioTrack(track as LocalAudioTrack)
    } else {
      val remoteTrack = flutterWebRTCPlugin.getRemoteTrack(trackId)
        if (remoteTrack != null) {
            audioTrack = LKRemoteAudioTrack(remoteTrack as AudioTrack)
        }
    }

    if(audioTrack == null) {
      result.error("INVALID_ARGUMENT", "track not found", null)
      return
    }

    val visualizer = Visualizer(
      barCount = barCount, isCentered = isCentered, 
      smoothTransition = smoothTransition,
      audioTrack = audioTrack, binaryMessenger = binaryMessenger!!,
      visualizerId = visualizerId)

    processors[visualizerId] = visualizer
    result.success(null)
  }

    // EventChannel.StreamHandler implementation
    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
    }

    override fun onCancel(arguments: Any?) {
        eventSink = null
    }

  private fun handleStopVisualizer(@NonNull call: MethodCall, @NonNull result: Result) {
    val trackId = call.argument<String>("trackId")
    val visualizerId = call.argument<String>("visualizerId")
    if (trackId == null || visualizerId == null) {
      result.error("INVALID_ARGUMENT", "trackId and visualizerId is required", null)
      return
    }
    processors.forEach { (k, visualizer) ->
      if(k == visualizerId) {
        visualizer.stop()
      }
    }
    processors.entries.removeAll { (k, v) -> k == visualizerId }
    result.success(null)
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    if(call.method == "startVisualizer") {
      handleStartVisualizer(call, result)
      return
    } else if(call.method == "stopVisualizer") {
      handleStopVisualizer(call, result)
      return
    }
      when (call.method) {
          "registerSocket" -> {
              registerSocket(call, result)
          }
          "unregisterSocket" -> {
              unregisterSocket(call, result)
          }
          "setUDPQoS" -> {
              setUDPQoS(call, result)
          }
          "setTCPQoS" -> {
              setTCPQoS(call, result)
          }
          "setAdaptiveQoS" -> {
              setAdaptiveQoS(call, result)
          }
          "getNetworkType" -> {
              getNetworkType(result)
          }
          "getRegisteredSocketIds" -> {
              getRegisteredSocketIds(result)
          }
          else -> {
              result.notImplemented()
          }
      }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    eventChannel.setStreamHandler(null)
      networkQoSManager.cleanup()
  }
}
