# Primary engine and necessary shell

One primary product: Telegram Android at
[f2908b14133bbffbf7ab04f641ecb5bfaf533242](https://github.com/DrKLO/Telegram/tree/f2908b14133bbffbf7ab04f641ecb5bfaf533242).

## Retained core

`engine` retains FilterGLThread, FilterShaders, saved-filter/curve models and the
native calcCDT enhancement routine. The real chain is parameters → upstream GLES
passes → upstream JNI enhancement → framebuffer readback. Account/protocol and
video/HDR branches are removed explicitly. Retained source hashes and URL/pin/blob/patch-digest metadata are in
`vendor/telegram-engine`; the engine owner's extraction ledger is retained.
Optional skin smoothing and its private tone mapper are entirely omitted because
the immediate Java port lineage was not established; no replacement is provided.
Separate GPL-noticed user adjustment curves remain included.

`crop` mechanically extracts PhotoViewer.createCroppedBitmap and
MediaController.CropState from the same revision. It preserves bitmap matrix
order, rotation, inversion, mirror and crop fractions. The method only changes
its model qualification and logging. State protocol serialization is removed;
this is a local media value object, not a fake messenger/account controller.
`tools/extract-crop.py`, exact upstream URL/hash metadata, a focused method diff and
`docs/crop-provenance.json` make the extraction reproducible.

## Original code, and why it exists

- MainActivity: Android screens, accessible controls, navigation, error messages,
  system picker/export intents, theme and lifecycle calls. Telegram's original
  PhotoViewer is a messenger screen with account dependencies; this shell is local
- EditValues: UI control snapshots and Android preference keys holding upstream
  parameter values. It does not change filter units, curve interpolation or render
  behavior. This retains one session because the upstream subsystem's enclosing
  draft storage is account-dependent; it is not a new project/database format
- LocalPhoto: bounded ContentResolver file access, platform image decoding and AndroidX EXIF
  metadata reading, private import copies and PNG encoding to user-chosen output.
  Orientation and centered crop pixels use the retained Telegram geometry; scaling
  uses PhotoEngine. Preset aspect ratios merely set upstream crop-state values
- PhotoApplication: process-start cleanup of uncommitted private import copies,
  retaining the active image and skipping deletion if its identity is uncertain
- Tests: independent behavioral assertions and synthetic fixtures. Tests do not
  replace a production algorithm

The reusable engine and its independent consumers have no Telegram login, API,
backend or account-shaped stub.

Paint and freeform crop gestures are outside this app's scope. The complete
workflow is focused local photo finishing, with actual rendered output rather
than a new composition/project format.

## License and metadata helper

The combined app selects GPLv3 under Telegram Android's officially stated
[GPLv2-or-later project grant](https://telegram.org/apps#source-code). Retained
original per-file headers and the original GPL2 text remain intact. This is not
a blanket relicensing of third-party files.

The Android26 platform EXIF reader missed valid orientation tags in an independently
checked fixture. The app therefore uses AndroidX ExifInterface1.4.2 (Apache-2.0),
an explicit update of the1.3.6 metadata dependency used by the pinned Telegram
source. [Official release notes](https://developer.android.com/jetpack/androidx/releases/exifinterface)
document its maintained parsing fixes. It reads metadata only; the actual image
transform remains the retained Telegram implementation. Exact official artifact
checksums, source archives and transitive notices are under
`third_party/androidx-exifinterface`.
