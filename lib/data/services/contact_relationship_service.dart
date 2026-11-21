import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:uuid/uuid.dart';

import '../../domain/models/contact_relationship.dart';
import '../../domain/models/other_models.dart';
import '../../injection/locator.dart';
import '../repositories/chaty_data_store.dart';
import 'api_backend_service.dart';

class ContactRelationshipService {
  ContactRelationshipService({
    SupabaseClient? client,
    FlutterSecureStorage? secureStorage,
  }) : _client = client ?? Supabase.instance.client,
       _secureStorage = secureStorage ?? const FlutterSecureStorage();

  static const String _deviceIdStorageKey = 'chaty.installation_device_id.v1';
  final SupabaseClient _client;
  final FlutterSecureStorage _secureStorage;
  final Uuid _uuid = const Uuid();
  String? _cachedDeviceId;
  Future<String>? _fetchDeviceIdFuture;

  String? get _currentUserId {
    final authId = _client.auth.currentUser?.id;
    if (authId != null && authId.isNotEmpty) return authId;
    if (locator.isRegistered<ApiBackendService>()) {
      final api = locator<ApiBackendService>();
      if (api.isAuthenticated && api.currentUser?.id != null && api.currentUser!.id.isNotEmpty) {
        return api.currentUser!.id;
      }
    }
    if (locator.isRegistered<ChatyDataStore>()) {
      final store = locator<ChatyDataStore>();
      final u = store.currentUserOrNull;
      if (u != null && u.id.isNotEmpty) return u.id;
    }
    return null;
  }

  String get _userId {
    final id = _currentUserId;
    if (id == null || id.isEmpty) return 'anonymous';
    return id;
  }

  Future<ContactPrivacyOverride> privacyFor(String targetUserId) async {
    final currentId = _currentUserId;
    if (_client.auth.currentUser == null || currentId == null) {
      return ContactPrivacyOverride(
        ownerUserId: currentId ?? '',
        targetUserId: targetUserId,
      );
    }
    try {
      final row = await _client
          .from('contact_privacy_overrides')
          .select()
          .eq('owner_user_id', currentId)
          .eq('target_user_id', targetUserId)
          .maybeSingle();
      if (row == null) {
        return ContactPrivacyOverride(
          ownerUserId: currentId,
          targetUserId: targetUserId,
        );
      }
      return ContactPrivacyOverride.fromDatabaseMap(
        Map<String, dynamic>.from(row),
      );
    } catch (e) {
      debugPrint('[ContactRelationshipService] privacyFor error: $e');
      return ContactPrivacyOverride(
        ownerUserId: currentId,
        targetUserId: targetUserId,
      );
    }
  }

  Future<void> savePrivacy(ContactPrivacyOverride value) async {
    final currentId = _currentUserId;
    if (currentId != null && value.ownerUserId != currentId) {
      throw StateError('Cannot change another user\'s privacy settings.');
    }
    if (_client.auth.currentUser == null) return;
    try {
      await _client
          .from('contact_privacy_overrides')
          .upsert(
            value.toDatabaseMap(),
            onConflict: 'owner_user_id,target_user_id',
          );
    } catch (e) {
      debugPrint('[ContactRelationshipService] savePrivacy error: $e');
    }
  }

  Future<bool> isBlocked(String targetUserId) async {
    final currentId = _currentUserId;
    if (_client.auth.currentUser == null || currentId == null) return false;
    try {
      final row = await _client
          .from('blocked_users')
          .select('blocked_id')
          .eq('blocker_id', currentId)
          .eq('blocked_id', targetUserId)
          .maybeSingle();
      return row != null;
    } catch (e) {
      debugPrint('[ContactRelationshipService] isBlocked error: $e');
      return false;
    }
  }

  Future<void> setBlocked(String targetUserId, bool blocked) async {
    if (_client.auth.currentUser == null) return;
    try {
      await _client.rpc(
        blocked ? 'block_user' : 'unblock_user',
        params: <String, dynamic>{'p_user_id': targetUserId},
      );
    } catch (e) {
      debugPrint('[ContactRelationshipService] setBlocked error: $e');
    }
  }

  Future<ContactConnectionStatus> connectionStatus(String otherUserId) async {
    if (_client.auth.currentUser == null) {
      return const ContactConnectionStatus(
        myAccepted: true,
        otherAccepted: true,
      );
    }
    try {
      final raw = await _client.rpc(
        'get_contact_connection_status',
        params: <String, dynamic>{'p_other_user_id': otherUserId},
      );
      if (raw is List && raw.isNotEmpty && raw.first is Map) {
        return ContactConnectionStatus.fromDatabaseMap(
          Map<String, dynamic>.from(raw.first as Map),
        );
      }
      if (raw is Map) {
        return ContactConnectionStatus.fromDatabaseMap(
          Map<String, dynamic>.from(raw),
        );
      }
      return const ContactConnectionStatus(myAccepted: true, otherAccepted: true);
    } catch (e) {
      debugPrint('[ContactRelationshipService] connectionStatus error: $e');
      return const ContactConnectionStatus(myAccepted: true, otherAccepted: true);
    }
  }

  Future<String> acceptConnection(String otherUserId) async {
    if (_client.auth.currentUser == null) return otherUserId;
    try {
      final raw = await _client.rpc(
        'accept_contact_connection',
        params: <String, dynamic>{'p_other_user_id': otherUserId},
      );
      final id = raw?.toString() ?? '';
      return id.isNotEmpty ? id : otherUserId;
    } catch (e) {
      debugPrint('[ContactRelationshipService] acceptConnection error: $e');
      return otherUserId;
    }
  }
    return id;
  }

  Future<String> currentDeviceId() {
    final cached = _cachedDeviceId;
    if (cached != null && cached.isNotEmpty) {
      return Future<String>.value(cached);
    }
    return _fetchDeviceIdFuture ??= _performCurrentDeviceId();
  }

  Future<String> _performCurrentDeviceId() async {
    try {
      var value = await _secureStorage.read(key: _deviceIdStorageKey);
      if (value == null || value.isEmpty) {
        value = _uuid.v4();
        await _secureStorage.write(key: _deviceIdStorageKey, value: value);
      }
      _cachedDeviceId = value;
      return value;
    } finally {
      _fetchDeviceIdFuture = null;
    }
  }

  String get _platformLabel {
    if (kIsWeb) return 'Web';
    switch (defaultTargetPlatform) {
      case TargetPlatform.android:
        return 'Android';
      case TargetPlatform.iOS:
        return 'iOS';
      case TargetPlatform.macOS:
        return 'macOS';
      case TargetPlatform.windows:
        return 'Windows';
      case TargetPlatform.linux:
        return 'Linux';
      case TargetPlatform.fuchsia:
        return 'Fuchsia';
    }
  }

  Future<void> registerCurrentDevice() async {
    final currentId = _currentUserId;
    if (_client.auth.currentUser == null || currentId == null) return;
    try {
      final deviceId = await currentDeviceId();
      final platform = _platformLabel;
      await _client.from('linked_devices').upsert(<String, dynamic>{
        'user_id': currentId,
        'device_id': deviceId,
        'device_name': '$platform Chaty device',
        'platform': platform,
        'location': '',
        'last_active_at': DateTime.now().toUtc().toIso8601String(),
        'revoked_at': null,
      }, onConflict: 'user_id,device_id');
    } catch (e) {
      debugPrint('[ContactRelationshipService] registerCurrentDevice error: $e');
    }
  }

  Future<List<LinkedDevice>> linkedDevices() async {
    final currentId = _currentUserId;
    if (_client.auth.currentUser == null || currentId == null) return const [];
    try {
      final current = await currentDeviceId();
      final rows = await _client
          .from('linked_devices')
          .select()
          .eq('user_id', currentId)
          .isFilter('revoked_at', null)
          .order('last_active_at', ascending: false);
      return (rows as List)
          .whereType<Map>()
          .map((raw) {
            final row = Map<String, dynamic>.from(raw);
            final id = row['device_id']?.toString() ?? '';
            return LinkedDevice(
              id: id,
              deviceName: row['device_name']?.toString() ?? 'Chaty device',
              platform: row['platform']?.toString() ?? 'Unknown',
              location: row['location']?.toString() ?? '',
              lastActiveAt:
                  DateTime.tryParse(
                    row['last_active_at']?.toString() ?? '',
                  )?.toLocal() ??
                  DateTime.now(),
              isCurrentDevice: id == current,
            );
          })
          .toList(growable: false);
    } catch (e) {
      debugPrint('[ContactRelationshipService] linkedDevices error: $e');
      return const [];
    }
  }

  Future<void> revokeDevice(String deviceId) async {
    final currentId = _currentUserId;
    if (_client.auth.currentUser == null || currentId == null) return;
    final current = await currentDeviceId();
    if (deviceId == current)
      throw StateError(
        'The current device cannot revoke itself from this screen.',
      );
    try {
      final revoked = await _client
          .from('linked_devices')
          .update(<String, dynamic>{
            'revoked_at': DateTime.now().toUtc().toIso8601String(),
          })
          .eq('user_id', currentId)
          .eq('device_id', deviceId)
          .isFilter('revoked_at', null)
          .select('device_id')
          .maybeSingle();

      if (revoked == null) {
        throw StateError(
          'This linked device is no longer active or cannot be revoked.',
        );
      }
    } catch (e) {
      debugPrint('[ContactRelationshipService] revokeDevice error: $e');
      rethrow;
    }
  }
}
