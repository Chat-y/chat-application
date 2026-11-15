# Temporary setup - not final legal policy

Configuration version: temp-0.4
Company: [Company name not configured]
Contact: [Contact email not configured]

These are deliberate placeholders, not real contact details. This local temporary setup is not a final published policy, and reading it does not record legal acceptance. The previous review draft is retained below.

# Chaty Privacy Policy

REVIEW DRAFT - version 0.3-draft, 15 November 2026.
Not legally reviewed or published. This draft describes app features and identifies the business and server practices that still need confirmation. It does not certify the deployed service, every dependency or the contents of a released app package. The legal data controller and a verified privacy contact must be added before publication.

## 1. Account and workspace information
Account features use identifiers such as an email address, username, display name and contact number. Profiles may include pictures and other details you choose to add. A supplied phone number is not necessarily verified; verification depends on the flow used. Workspaces may keep membership and account-approval information. Confirm the final server-side data fields and administrator access before publication.

## 2. Messages, media and calls
Messaging features process the text, files, images, voice messages and other content you send, together with delivery and conversation information. Calls involve connection and participant information and microphone or camera access when you choose those features. Do not infer that service-side call recording is absent merely because the app has no obvious recording control; that practice still needs confirmation.

Standard chats are not end-to-end encrypted in the current routing configuration. App Lock protects local access to the app; it is not a claim that the service cannot read stored messages. A final encryption statement must match the deployed architecture and every enabled message mode.

## 3. Device permissions and location
Camera, microphone, media and contact permissions support the features that ask for them. Location features can process coordinates you choose to share. Review Android permissions to change access. The final policy must confirm background collection, permission triggers and retention for each shipped feature instead of assuming that a granted permission is always being used.

## 4. Passwords and sign-in codes
The auth forms keep entered passwords and verification codes in memory, not in ordinary form-restoration preferences. The app stores session credentials through its secure-storage paths so accounts can remain signed in. Selected Android password providers may save or retrieve a password after you choose their prompt. Retrieval fills the form and does not itself submit a sign-in. The provider's own account and privacy settings also apply.

## 5. Optional cross-app island
If you turn this feature on and grant Android notification access, Chaty can read notification text and actions and media-session information to show the island and perform actions you choose. The optional accessibility host places a tappable island above other apps; its current configuration does not retrieve screen content or inject gestures. Overlay access lets the island float above other apps. Turn the island off in Chaty and revoke special access in Android Settings. Battery settings are optional troubleshooting controls, not a promise of always-on delivery. Ordinary messaging does not require the cross-app island.

Storage or transmission of notification text, app inventory and media metadata, including SDK, logging and crash-report paths, needs a final audit before publication. No blanket never-collected or never-uploaded claim is made.

## 6. Third-party requests
A translation or link-preview feature can send content or make a request to another provider when that feature is enabled. The recipient may receive the requested text or URL, network identifiers and its normal request metadata. Final provider names, automatic-versus-user-triggered behavior, processing locations and controls must be checked against the shipped implementation. Opening an external website or mail app is also subject to that service's privacy practices.

## 7. Diagnostics and notifications
Auth analytics collection and automatic Firebase messaging initialization are disabled in the local configuration; messaging setup is enabled for authenticated use. This is a description of the local configuration, not a full dependency audit. The optional crash-report setting is off by default and currently has no reporting service connected; turning it on alone sends nothing. A future reporting integration needs its own clear disclosure and consent handling.

## 8. Use, sharing and processing locations
The service needs data to deliver messages and calls, manage accounts and workspaces, synchronize devices and protect against abuse. The operator must confirm the final legal bases, processors, international transfers, advertising and sale/sharing practices. This draft makes no unverified promise that data is never sold, that advertising is absent everywhere or that all processing occurs in one country.

## 9. Retention, logout and deletion
Signing out clears active session state and private in-memory views. It is not the same as deleting an account, every local file, queued message, recipient copy, server record or backup. Some account-scoped queued work is retained so it can recover after the same account signs in again. Exact retention periods, secure-deletion behavior, backups, legally required records and deletion-request procedures must be verified and stated before publication.

## 10. Your controls and rights
Use profile, privacy, device, permission, block and notification controls available in your version. A final policy must explain applicable access, correction, deletion, portability, objection and complaint rights and how to exercise them. Do not send passwords or verification codes in a support request. A verified privacy contact and the responsible legal entity are still required.

## 11. Review and changes
This text is for local review. Material changes in data use or new third-party integrations need an updated disclosure before rollout. Publication remains blocked on the legal and technical confirmation checklist. No external policy URL is presented as final.
