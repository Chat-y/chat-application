#!/usr/bin/env bash
# Opt-in local/CI job. Requires a connected test device. Never invoked during editing.
set -euo pipefail
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.chat.AuthA11yMatrixTest,com.example.chat.AuthApprovedLoginUiTest,com.example.chat.AuthApprovedRegisterUiTest,com.example.chat.AuthApprovedOtpUiTest,com.example.chat.AuthGoldenPixelMatrixTest
