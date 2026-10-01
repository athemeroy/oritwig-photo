# Oritwig Photo

An offline Android photo finisher powered by Telegram's actual photo engine.

Developer preview0.1.0. The complete import/edit/export/reopen workflow and
37 native assertions pass on Android8/API26. Physical-device and Android35
runtime coverage remain pending. [Validation scope](docs/VALIDATION.md).

## A complete, bounded photo workflow

Open a local PNG/JPEG/WebP → choose a centered crop, rotate or mirror → adjust
light/color or RGB/luminance curves → export an ordinary PNG. The most recent
photo and its adjustments resume when reopening the app. Settings choose system,
light or dark appearance and a 1024/2048-pixel maximum exported edge.

No account, Telegram installation, API key, server, network permission, analytics
or model service. Paint, layers, editable project collections, HDR, video and
freeform crop are not part of this version.

The retained upstream implementation performs every image adjustment, crop
transformation and pixel readback. See [provenance](docs/PROVENANCE.md) for the retained implementation and its thin adapters.

## Telegram and Oritwig Photo: what differs?

Primary upstream: [Telegram Android at f2908b14](https://github.com/DrKLO/Telegram/tree/f2908b14133bbffbf7ab04f641ecb5bfaf533242).

| | Telegram's original editor | Oritwig Photo |
| --- | --- | --- |
| Product | Media editing inside the full messaging client | A standalone local photo-finishing app |
| Core | GLES filter passes, curves, native enhancement and crop geometry | Those real implementations retained; no newly invented filters |
| Dependencies | Editor integrated with client/account utilities and media workflows | A separately buildable media library; no account, API, server or messaging code |
| File flow | Integrated Telegram media-edit/send flow | Android document picker → private working copy → user-selected PNG destination |
| Included UI | Rich photo/video, paint and media tools | Focused light/color controls, curves, centered crop presets, rotate/mirror, reset and settings |
| Deliberate limits | Broader editing and messaging features | No messaging, HDR, video, paint, stickers, layers or freeform crop; optional skin smoothing and its private tone mapper are omitted because their immediate port provenance was not established |

The useful difference is access to the mature editing engine as an independent,
account-free tool and reusable module. This is not a claim of novel processing
algorithms. Our new work is the local UI, platform file adapters, error/lifecycle
handling and one-session preference storage; upstream account-backed drafts are
not reused or replaced with a new database. [Exact source changes](docs/PROVENANCE.md)
identify retained code, removals and each necessary addition.

## Build

Use JDK21, SDK35/build-tools35.0.0, NDK27.2.12479018 and CMake3.22.1.
Gradle8.11.1 and AGP8.9.3 are pinned, with strict dependency verification.

```sh
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.oritwig.photo/.MainActivity
```

Native product assertions:

```sh
./gradlew :app:assembleDebugAndroidTest
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r dev.oritwig.photo.test/dev.oritwig.photo.PhotoInstrumentation
```

Use a dedicated test device. Compilation and JVM assertions are not a native test
pass. This directory includes the engine and crop source; no sibling checkout is
needed. Exact engine source hashes are recorded in vendor/telegram-engine.

The metadata helper is updated from Telegram's AndroidX ExifInterface1.3.6 to
1.4.2 for maintained JPEG/EXIF parsing on older Android versions. It is not an
image-processing replacement. [Dependency versions, sources and hashes](third_party/androidx-exifinterface/dependencies.json) are retained.

## Local files and limits

- Up to 25MiB and 32 megapixels per import; at least eight pixels per side
  after preparation. Extremely narrow images can be rejected
- Platform-decoded still images only; animation is not retained
- Import prepares a private, metadata-free PNG at at most 2048 pixels; no upscaling
- Import leaves original files untouched. Export writes only to the destination
  you choose; PNGs omit source EXIF/GPS metadata
- Last-session values use Android preferences, with no custom database or archive
- No automatic Android cloud or device-transfer backup. Uninstalling removes the
  private session; keep exports you want to retain
- User-selected document providers can sync exports to their own cloud. Removing
  the local session cannot retract those files and is not secure erasure

The renderer preserves upstream effect-dependent alpha behavior. Reset bypasses
filters; an adjusted state uses the upstream baseline sharpening. No independent
claim of HDR/color-management equivalence is made.

## License

The combined app is GNU GPL version3, selected under Telegram Android's
[GPLv2-or-later project grant](https://telegram.org/apps#source-code). Original
file notices and the upstream GPL2 text are preserved. AndroidX ExifInterface
1.4.2 and its dependency sources/notices retain their Apache-2.0 terms.
The optional unverified smoothing port is omitted. Retained implementation,
licenses and exact source pin are documented in the provenance ledger.
No Telegram affiliation or endorsement is implied. All compiled engine/crop source, upstream notices, origin hashes and the crop
extraction script accompany this prototype. Excluded upstream code is not bundled.
