# Camera privacy notes

Camera preview frames, face lenses and background effects are processed on your phone. These camera bridges load local model assets; they do not send preview frames to a remote effect service.

The camera screen hands a captured photo or video to the chat or story flow only after you choose Send or Post. A handoff is not confirmation that a message was uploaded or delivered. The host flow is responsible for that status.

Unsent files tracked by the active camera screen are discarded when you leave or retake. Cleanup after a crash or process death has not been verified. Do not rely on a guaranteed 24-hour deletion window.

## Source review status

Checked CameraCaptureScreen, FaceDetectionBridge and SegmentationBridge source. This is a source-level statement, not a network trace, device privacy audit, crash-cleanup test or proof about unrelated app features. No recording or camera frames were collected for this review.
