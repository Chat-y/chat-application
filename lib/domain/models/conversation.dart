enum ConversationType { direct, group }

enum EncryptionStatus { encrypted, verificationNeeded, demoMode }

class Conversation {
  final String id;
  final ConversationType type;
  final String title;
  final List<String> participantIds;
  final List<String> adminIds;
  final String? avatarInitials;
  final String? avatarColorHex;
  final String lastMessageText;
  final DateTime lastMessageTime;
  final String lastMessageSenderId;
  final int unreadCount;
  final bool isPinned;
  final bool isArchived;
  final bool isMuted;
  final String? wallpaperId;
  final Duration? disappearingDuration;
  final EncryptionStatus encryptionStatus;
  final String draftText;

  const Conversation({
    required this.id,
    required this.type,
    required this.title,
    required this.participantIds,
    this.adminIds = const [],
    this.avatarInitials,
    this.avatarColorHex,
    required this.lastMessageText,
    required this.lastMessageTime,
    required this.lastMessageSenderId,
    this.unreadCount = 0,
    this.isPinned = false,
    this.isArchived = false,
    this.isMuted = false,
    this.wallpaperId,
    this.disappearingDuration,
    // Chaty must not claim E2EE until audited device-to-device encryption is
    // actually implemented and verified. The option remains in the UI.
    this.encryptionStatus = EncryptionStatus.verificationNeeded,
    this.draftText = '',
  });

  Conversation copyWith({
    String? id,
    ConversationType? type,
    String? title,
    List<String>? participantIds,
    List<String>? adminIds,
    String? avatarInitials,
    String? avatarColorHex,
    String? lastMessageText,
    DateTime? lastMessageTime,
    String? lastMessageSenderId,
    int? unreadCount,
    bool? isPinned,
    bool? isArchived,
    bool? isMuted,
    String? wallpaperId,
    Duration? disappearingDuration,
    EncryptionStatus? encryptionStatus,
    String? draftText,
  }) {
    return Conversation(
      id: id ?? this.id,
      type: type ?? this.type,
      title: title ?? this.title,
      participantIds: participantIds ?? this.participantIds,
      adminIds: adminIds ?? this.adminIds,
      avatarInitials: avatarInitials ?? this.avatarInitials,
      avatarColorHex: avatarColorHex ?? this.avatarColorHex,
      lastMessageText: lastMessageText ?? this.lastMessageText,
      lastMessageTime: lastMessageTime ?? this.lastMessageTime,
      lastMessageSenderId: lastMessageSenderId ?? this.lastMessageSenderId,
      unreadCount: unreadCount ?? this.unreadCount,
      isPinned: isPinned ?? this.isPinned,
      isArchived: isArchived ?? this.isArchived,
      isMuted: isMuted ?? this.isMuted,
      wallpaperId: wallpaperId ?? this.wallpaperId,
      disappearingDuration: disappearingDuration ?? this.disappearingDuration,
      encryptionStatus: encryptionStatus ?? this.encryptionStatus,
      draftText: draftText ?? this.draftText,
    );
  }

  factory Conversation.fromApi(Map<String, dynamic> json) {
    String lastText = '';
    DateTime lastTime = json['updated_at'] != null
        ? DateTime.tryParse(json['updated_at'].toString()) ?? DateTime.now()
        : DateTime.now();
    String lastSender = '';

    final lastMsg = json['last_message'];
    if (lastMsg is Map<String, dynamic>) {
      final raw = lastMsg['content']?.toString() ?? '';
      if (raw.startsWith('{') && raw.contains('"image"')) {
        lastText = '📷 Photo';
      } else if (raw.startsWith('{') && raw.contains('"type"')) {
        lastText = '📎 Attachment';
      } else if (raw.startsWith('http') && (raw.endsWith('.jpg') || raw.endsWith('.png') || raw.endsWith('.jpeg') || raw.endsWith('.webp'))) {
        lastText = '📷 Photo';
      } else {
        lastText = raw;
      }
      if (lastMsg['created_at'] != null) {
        lastTime = DateTime.tryParse(lastMsg['created_at'].toString()) ?? lastTime;
      }
      lastSender = lastMsg['sender_name']?.toString() ?? '';
    }

    final otherUser = json['other_user'];
    final participantIds = <String>[];
    final creatorId = (json['created_by_id'] ?? json['created_by_uid'] ?? json['created_by'])?.toString();
    if (creatorId != null && creatorId.isNotEmpty) {
      participantIds.add(creatorId);
    }
    if (otherUser is Map) {
      final otherId = (otherUser['uid'] ?? otherUser['id'])?.toString();
      if (otherId != null && otherId.isNotEmpty && !participantIds.contains(otherId)) {
        participantIds.add(otherId);
      }
    }
    final participantsList = json['participants'] ?? json['members'];
    if (participantsList is List) {
      for (final p in participantsList) {
        if (p is Map) {
          final pid = (p['uid'] ?? p['id'])?.toString();
          if (pid != null && pid.isNotEmpty && !participantIds.contains(pid)) {
            participantIds.add(pid);
          }
        } else if (p != null) {
          final pid = p.toString();
          if (pid.isNotEmpty && !participantIds.contains(pid)) {
            participantIds.add(pid);
          }
        }
      }
    }

    final adminIds = <String>[];
    if (creatorId != null && creatorId.isNotEmpty) {
      adminIds.add(creatorId);
    }
    final rawAdmins = json['admin_ids'] ?? json['admins'];
    if (rawAdmins is List) {
      for (final a in rawAdmins) {
        final aid = (a is Map ? (a['uid'] ?? a['id']) : a)?.toString();
        if (aid != null && aid.isNotEmpty && !adminIds.contains(aid)) {
          adminIds.add(aid);
        }
      }
    }

    final rawTitle = json['display_name']?.toString() ?? json['name']?.toString() ?? 'Chat';
    final effectiveTitle = rawTitle.trim().isNotEmpty ? rawTitle.trim() : 'Chat';
    final compact = effectiveTitle.replaceAll(RegExp(r'[^A-Za-z0-9]'), '');
    final initials = compact.isEmpty
        ? 'C'
        : compact.substring(0, compact.length.clamp(1, 2)).toUpperCase();

    return Conversation(
      id: json['uid']?.toString() ?? json['id']?.toString() ?? '',
      type: json['room_type'] == 'group' ? ConversationType.group : ConversationType.direct,
      title: effectiveTitle,
      participantIds: participantIds,
      adminIds: adminIds,
      avatarInitials: initials,
      avatarColorHex: '0xFF6366F1',
      lastMessageText: lastText,
      lastMessageTime: lastTime,
      lastMessageSenderId: lastSender,
    );
  }
}

