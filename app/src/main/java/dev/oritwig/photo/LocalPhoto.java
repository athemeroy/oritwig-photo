/* Original Android file-picker/private-session adapter. GPL-2.0-or-later. */
package dev.oritwig.photo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.exifinterface.media.ExifInterface;
import android.net.Uri;
import dev.oritwig.crop.CropState;
import dev.oritwig.crop.TelegramCrop;
import java.io.*;
import java.util.UUID;

final class LocalPhoto {
  static final long MAX_BYTES = 25L * 1024 * 1024, MAX_PIXELS = 32L * 1024 * 1024;

  private LocalPhoto() {}

  static File source(Context c, String name) throws IOException {
    if (name == null || !name.matches("photo-[0-9a-f-]+\\.png"))
      throw new IOException("No saved photo. Import a local image to begin.");
    return new File(c.getFilesDir(), name);
  }

  static Bitmap decode(File file) throws IOException {
    BitmapFactory.Options o = new BitmapFactory.Options();
    o.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(file.toString(), o);
    if (!"image/png".equals(o.outMimeType)
        && !"image/jpeg".equals(o.outMimeType)
        && !"image/webp".equals(o.outMimeType))
      throw new IOException(
          "Choose a decodable PNG, JPEG or WebP image. Animated and other formats are not"
              + " supported.");
    if (o.outWidth < 8 || o.outHeight < 8 || (long) o.outWidth * o.outHeight > MAX_PIXELS)
      throw new IOException(
          "Choose an image with at least 8 pixels per side and no more than 32 megapixels.");
    o.inJustDecodeBounds = false;
    o.inSampleSize = 1;
    o.inPreferredConfig = Bitmap.Config.ARGB_8888;
    while (Math.max(o.outWidth, o.outHeight) / o.inSampleSize > 2048) {
      o.inSampleSize *= 2;
    }
    if (Math.min(o.outWidth, o.outHeight) / o.inSampleSize < 8)
      throw new IOException(
          "This image is too narrow at the working resolution. Choose an image with less extreme"
              + " proportions.");
    Bitmap b = BitmapFactory.decodeFile(file.toString(), o);
    if (b == null) throw new IOException("This image cannot be decoded. Choose PNG, JPEG or WebP.");
    return b;
  }

  static String importPhoto(Context c, Uri uri) throws IOException {
    File raw = File.createTempFile("photo-import-", ".tmp", c.getCacheDir());
    File output = new File(c.getFilesDir(), "photo-" + UUID.randomUUID() + ".png");
    Bitmap b = null, oriented = null, limited = null;
    boolean ok = false;
    try {
      try (InputStream in = c.getContentResolver().openInputStream(uri);
          FileOutputStream out = new FileOutputStream(raw)) {
        if (in == null) throw new IOException("The selected provider could not open this image.");
        byte[] buffer = new byte[32768];
        long total = 0;
        int n;
        while ((n = in.read(buffer)) != -1) {
          total += n;
          if (total > MAX_BYTES) throw new IOException("Image exceeds the 25 MiB import limit.");
          out.write(buffer, 0, n);
        }
      }
      b = decode(raw);
      int orientation = ExifInterface.ORIENTATION_NORMAL;
      try {
        orientation =
            new ExifInterface(raw.toString())
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
      } catch (IOException unsupported) {
        /* PNG/WebP may have no EXIF; platform decoded pixels remain valid. */
      }
      oriented =
          TelegramCrop.createCroppedBitmap(b, new CropState(), exifTransform(orientation), false);
      if (oriented == null) throw new IOException("Image orientation could not be read.");
      // Scaling is performed by the retained Telegram engine, not a substitute renderer.
      try (dev.oritwig.telegram.photo.PhotoEngine engine =
          new dev.oritwig.telegram.photo.PhotoEngine(oriented, 0, 2048)) {
        limited = engine.renderOriginal().get(60, java.util.concurrent.TimeUnit.SECONDS);
      } catch (Exception problem) {
        throw new IOException("Photo engine could not prepare this image.", problem);
      }
      try (FileOutputStream out = new FileOutputStream(output)) {
        if (!limited.compress(Bitmap.CompressFormat.PNG, 100, out))
          throw new IOException("Could not store the image.");
        out.getFD().sync();
      }
      ok = true;
      return output.getName();
    } catch (OutOfMemoryError memory) {
      throw new IOException("Not enough memory. Choose a smaller image.");
    } finally {
      raw.delete();
      if (b != null) b.recycle();
      if (oriented != null) oriented.recycle();
      if (limited != null) limited.recycle();
      if (!ok) output.delete();
    }
  }

  static int[] exifTransform(int orientation) {
    switch (orientation) {
      case 2:
        return new int[] {0, 1};
      case 3:
        return new int[] {180, 0};
      case 4:
        return new int[] {0, 2};
      case 5:
        return new int[] {90, 2};
      case 6:
        return new int[] {90, 0};
      case 7:
        return new int[] {90, 1};
      case 8:
        return new int[] {270, 0};
      default:
        return new int[] {0, 0};
    }
  }

  static Bitmap crop(Bitmap original, EditValues e) throws IOException {
    CropState s = new CropState();
    s.transformRotation = e.turns * 90;
    s.mirrored = e.mirror;
    int w = (e.turns % 2 == 0) ? original.getWidth() : original.getHeight(),
        h = (e.turns % 2 == 0) ? original.getHeight() : original.getWidth();
    // These fractions select upstream crop-state parameters. All geometry is Telegram's method.
    if (e.crop != 0) {
      float target = e.crop == 1 ? 1f : e.crop == 2 ? 4f / 3f : 3f / 4f;
      float aspect = (float) w / h;
      if (aspect > target) s.cropPw = target / aspect;
      else s.cropPh = aspect / target;
    }
    if ((int) (w * s.cropPw) < 8 || (int) (h * s.cropPh) < 8)
      throw new IOException("This crop is too narrow for the photo engine. Select Original.");
    Bitmap result = TelegramCrop.createCroppedBitmap(original, s, null, true);
    if (result == null) throw new IOException("The crop could not be rendered.");
    return result;
  }

  static void export(Context c, Uri uri, Bitmap bitmap) throws IOException {
    File encoded = File.createTempFile("photo-export-", ".tmp", c.getCacheDir());
    try {
      // Encode completely before opening a provider destination. This avoids truncating
      // a chosen file if local encoding or cache-space preparation fails.
      try (FileOutputStream out = new FileOutputStream(encoded)) {
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
          throw new IOException("PNG encoding failed.");
        out.getFD().sync();
      }
      try (InputStream in = new FileInputStream(encoded);
          OutputStream out = c.getContentResolver().openOutputStream(uri, "wt")) {
        if (out == null) throw new IOException("Destination is unavailable.");
        byte[] data = new byte[32768];
        int n;
        while ((n = in.read(data)) != -1) out.write(data, 0, n);
        out.flush();
      } catch (IOException | SecurityException error) {
        throw new IOException(
            "PNG could not be fully written. The destination may contain an incomplete file; your"
                + " local session is still available. Choose a writable destination and retry.",
            error);
      }
    } finally {
      encoded.delete();
    }
  }
}
