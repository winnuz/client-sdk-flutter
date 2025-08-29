import 'package:flutter/services.dart';
import '../logger.dart';

class NetworkQoSManager {
  static const MethodChannel _channel = MethodChannel('livekit_client');
  static const _log = 'NetworkQoSManager';
  /// Set QoS for UDP traffic
  static Future<bool> setUDPQoS(int dscpValue) async {
    try {
      final bool result = await _channel.invokeMethod('setUDPQoS', {
        'dscpValue': dscpValue,
      });
      return result;
    } on PlatformException catch (e) {
      print('$_log Error setting UDP QoS: ${e.message}');
      return false;
    }
  }

  /// Set QoS for TCP traffic
  static Future<bool> setTCPQoS(int dscpValue) async {
    try {
      final bool result = await _channel.invokeMethod('setTCPQoS', {
        'dscpValue': dscpValue,
      });
      return result;
    } on PlatformException catch (e) {
      print('$_log Error setting TCP QoS: ${e.message}');
      return false;
    }
  }

  /// Set adaptive QoS based on network type
  static Future<bool> setAdaptiveQoS() async {
    try {
      final bool result = await _channel.invokeMethod('setAdaptiveQoS');
      return result;
    } on PlatformException catch (e) {
      print('$_log  Error setting adaptive QoS: ${e.message}');
      return false;
    }
  }

  /// Get current network type
  static Future<String> getCurrentNetworkType() async {
    try {
      final String result = await _channel.invokeMethod('getNetworkType');
      return result;
    } on PlatformException catch (e) {
      print('$_log  Error getting network type: ${e.message}');
      return 'UNKNOWN';
    }
  }

  /// Set QoS for WebRTC based on network type
  static Future<bool> setWebRTCQoS() async {
    try {
      // Get current network type
      final networkType = await getCurrentNetworkType();
      print('$_log  setWebRTCQoS Network QoS set successfully for networkType:$networkType');
      // Set appropriate QoS based on network
      switch (networkType) {
        case 'WIFI':
          return await setUDPQoS(34); // AF41 - high priority
        case 'CELLULAR':
          return await setUDPQoS(46); // EF - highest priority
        case 'ETHERNET':
          return await setUDPQoS(26); // AF31 - medium priority
        case 'BLUETOOTH':
          return await setUDPQoS(18); // AF21 - lower priority
        default:
          return await setUDPQoS(0);  // CS0 - best effort
      }
    } catch (e) {
      print('$_log  setWebRTCQoS Error setting WebRTC QoS: $e');
      return false;
    }
  }
}