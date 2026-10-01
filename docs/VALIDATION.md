# Validation

Developer preview 0.1.0, tested on a dedicated Android 8.0 / API 26 x86
software emulator. Target/compile SDK remains 35.

## Passed

- Standalone debug app and instrumentation APK compilation
- Eight JVM tests for parameter mapping, independent snapshots, crop-state
  retention and the actual upstream curve-model cache
- Android lint: zero errors, six English-only/dynamic-string warnings
- 37 product Android assertions against the final installed APK
- Actual system-picker import of an original synthetic PNG; centered square
  crop, quarter-turn rotation, mirroring and real filter controls
- Actual Android document-picker PNG export, followed by independent image
  decoding: 960 × 960 pixels, changed filter output, no EXIF/text chunks
- Force-stop and reopen; a second document-picker export is byte-identical
  to the first, confirming retained values and no repeated filter application

The Android suite covers actual upstream crop/rotation/mirror pixels, EXIF5/7
JPEG imports and metadata removal, genuine GLES/JNI output, PNG decode
correspondence, malformed import rejection, SAF URI guards, parameter retention
and the real launcher. No test substitutes a handwritten processing engine.

The installed developer APK SHA-256 is
`09893228956bdfb7870ad43e0d92ff8617a0743d2fd57f2dccd3522be00c7878`.
The two matching exported PNGs have SHA-256
`e2968fdec958d3db12f515e325acf962cbad38ea82ac4c24774bfb739f0375cb`.
The selected UI values were Enhance 45, Exposure 20, square crop, zero rotation
and no mirror. Rotation and mirror were exercised before returning upright.

- Replacement-picker cancellation preserved the current edit
- A tone-curve draft was changed, canceled and reopened with its original values
- An independently unpacked source archive built offline: eight tests and lint
  passed; compiled Java classes matched the tested build. Native libraries
  matched after excluding debug/build-ID sections. Whole-APK byte reproducibility
  is not claimed, because clean and incremental builds partition DEX differently

## Limits of this evidence

API 35 runtime, physical devices, vendor-specific GPU behavior and a full
TalkBack audit have not been executed. This is a debug-signed developer preview, not
a production-signed store release.

Engine-only evidence remains separate: the exact retained engine pin also passed
its own effect/curve/lifecycle suite and a separately built AAR consumer. The
optional unresolved smoothing feature is excluded from all app build inputs.
