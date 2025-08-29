import 'package:flutter/services.dart';
import '../logger.dart';


class NetworkQoSManager {
  static const MethodChannel _channel = MethodChannel('livekit_client');

  /// Register a socket with a unique ID
  static Future<String> registerSocket(String socketType) async {
    try {
      final String socketId = await _channel.invokeMethod('registerSocket', {
        'socketType': socketType,
      });
      return socketId;
    } on PlatformException catch (e) {
      print('Error registering socket: ${e.message}');
      rethrow;
    }
  }

  /// Unregister a socket
  static Future<bool> unregisterSocket(String socketId) async {
    try {
      final bool result = await _channel.invokeMethod('unregisterSocket', {
        'socketId': socketId,
      });
      return result;
    } on PlatformException catch (e) {
      print('Error unregistering socket: ${e.message}');
      return false;
    }
  }

  /// Set QoS for UDP socket by ID
  static Future<bool> setUDPQoS(String socketId, int dscpValue) async {
    try {
      final bool result = await _channel.invokeMethod('setUDPQoS', {
        'socketId': socketId,
        'dscpValue': dscpValue,
      });
      return result;
    } on PlatformException catch (e) {
      print('Error setting UDP QoS: ${e.message}');
      return false;
    }
  }

  /// Set QoS for TCP socket by ID
  static Future<bool> setTCPQoS(String socketId, int dscpValue) async {
    try {
      final bool result = await _channel.invokeMethod('setTCPQoS', {
        'socketId': socketId,
        'dscpValue': dscpValue,
      });
      return result;
    } on PlatformException catch (e) {
      print('Error setting TCP QoS: ${e.message}');
      return false;
    }
  }

  /// Set adaptive QoS for socket by ID
  static Future<bool> setAdaptiveQoS(String socketId) async {
    try {
      final bool result = await _channel.invokeMethod('setAdaptiveQoS', {
        'socketId': socketId,
      });
      return result;
    } on PlatformException catch (e) {
      print('Error setting adaptive QoS: ${e.message}');
      return false;
    }
  }

  /// Get current network type
  static Future<String> getCurrentNetworkType() async {
    try {
      final String result = await _channel.invokeMethod('getNetworkType');
      return result;
    } on PlatformException catch (e) {
      print('Error getting network type: ${e.message}');
      return 'UNKNOWN';
    }
  }

  /// Get all registered socket IDs
  static Future<List<String>> getRegisteredSocketIds() async {
    try {
      final List<dynamic> result = await _channel.invokeMethod('getRegisteredSocketIds');
      return result.cast<String>();
    } on PlatformException catch (e) {
      print('Error getting registered socket IDs: ${e.message}');
      return [];
    }
  }

  /// Set QoS for WebRTC based on network type
  static Future<bool> setWebRTCQoS(String socketId) async {
    try {
      // Get current network type
      final networkType = await getCurrentNetworkType();

      // Set appropriate QoS based on network
      switch (networkType) {
        case 'WIFI':
          return await setUDPQoS(socketId, 34); // AF41 - high priority
        case 'CELLULAR':
          return await setUDPQoS(socketId, 46); // EF - highest priority
        case 'ETHERNET':
          return await setUDPQoS(socketId, 26); // AF31 - medium priority
        case 'BLUETOOTH':
          return await setUDPQoS(socketId, 18); // AF21 - lower priority
        default:
          return await setUDPQoS(socketId, 0);  // CS0 - best effort
      }
    } catch (e) {
      print('Error setting WebRTC QoS: $e');
      return false;
    }
  }
}