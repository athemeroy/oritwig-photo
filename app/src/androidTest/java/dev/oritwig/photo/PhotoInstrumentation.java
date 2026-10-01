/* Original integration assertions, GPL-2.0-or-later. Dedicated disposable emulator only. */
package dev.oritwig.photo;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import dev.oritwig.crop.*;
import dev.oritwig.telegram.photo.*;
import java.io.*;
import java.util.concurrent.TimeUnit;

public final class PhotoInstrumentation extends Instrumentation {
  private int passed;

  private void check(boolean value, String what) {
    if (!value) throw new AssertionError(what);
    passed++;
  }

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    start();
  }

  @Override
  public void onStart() {
    Bundle result = new Bundle();
    try {
      exercise();
      result.putInt("passed", passed);
      result.putString("stream", passed + " photo product assertions passed\n");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable t) {
      result.putString("stream", android.util.Log.getStackTraceString(t));
      result.putInt("passed", passed);
      finish(Activity.RESULT_CANCELED, result);
    }
  }

  private void exercise() throws Exception {
    Context c = getTargetContext();
    Bitmap input = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888);
    for (int y = 0; y < 240; y++)
      for (int x = 0; x < 320; x++)
        input.setPixel(x, y, Color.rgb(x * 255 / 319, y * 255 / 239, 80));
    input.setPixel(0, 0, Color.RED);
    input.setPixel(319, 0, Color.GREEN);
    input.setPixel(0, 239, Color.BLUE);
    input.setPixel(319, 239, Color.YELLOW);
    Bitmap original = TelegramCrop.createCroppedBitmap(input, new CropState(), null, true);
    check(original.sameAs(input), "upstream identity crop exact pixels");
    original.recycle();
    EditValues edit = new EditValues();
    edit.turns = 1;
    Bitmap rotated = LocalPhoto.crop(input, edit);
    check(
        rotated.getWidth() == 240 && rotated.getHeight() == 320,
        "upstream quarter-turn output dimensions");
    check(rotated.getPixel(239, 0) == Color.RED, "upstream rotation corner mapping");
    rotated.recycle();
    edit.turns = 0;
    edit.crop = 1;
    Bitmap square = LocalPhoto.crop(input, edit);
    check(square.getWidth() == 240 && square.getHeight() == 240, "upstream square crop dimensions");
    check(square.getPixel(0, 100) == input.getPixel(40, 100), "upstream centered crop samples");
    square.recycle();
    edit.crop = 0;
    edit.mirror = true;
    Bitmap mirrored = LocalPhoto.crop(input, edit);
    check(mirrored.getPixel(319, 0) == Color.RED, "upstream mirror corner mapping");
    mirrored.recycle();
    Bitmap five =
        TelegramCrop.createCroppedBitmap(
            input, new CropState(), LocalPhoto.exifTransform(5), false);
    check(five.getWidth() == 240 && five.getHeight() == 320, "EXIF5 transpose dimensions");
    check(
        five.getPixel(0, 0) == Color.RED && five.getPixel(239, 319) == Color.YELLOW,
        "EXIF5 correct corners");
    five.recycle();
    Bitmap seven =
        TelegramCrop.createCroppedBitmap(
            input, new CropState(), LocalPhoto.exifTransform(7), false);
    check(
        seven.getPixel(0, 0) == Color.YELLOW && seven.getPixel(239, 319) == Color.RED,
        "EXIF7 correct corners");
    seven.recycle();
    edit = new EditValues();
    check(edit.originalFilters(), "new state is exact original bypass");
    edit.values[0] = 45;
    edit.values[1] = 35;
    edit.curves[0][2] = 65;
    edit.turns = 3;
    edit.crop = 2;
    edit.mirror = true;
    check(!edit.originalFilters(), "adjusted state reaches renderer");
    android.content.SharedPreferences p = c.getSharedPreferences("test-only-session", 0);
    android.content.SharedPreferences.Editor pe = p.edit();
    edit.write(pe);
    check(pe.commit(), "upstream value snapshot persisted by platform preferences");
    EditValues restored = EditValues.read(p);
    check(
        restored.values[0] == 45 && restored.values[1] == 35 && restored.curves[0][2] == 65,
        "filter and curve values restore");
    check(restored.turns == 3 && restored.crop == 2 && restored.mirror, "crop parameters restore");
    p.edit().clear().commit();
    try (PhotoEngine engine = new PhotoEngine(input, 0, 320)) {
      Bitmap plain = engine.renderOriginal().get(60, TimeUnit.SECONDS);
      Bitmap changed = engine.render(edit.upstreamState()).get(60, TimeUnit.SECONDS);
      check(plain.getWidth() == 320 && changed.getHeight() == 240, "real GLES readback dimensions");
      check(!changed.sameAs(plain), "real upstream filter/native pipeline changes pixels");
      File file = new File(c.getCacheDir(), "photo-instrumentation.png");
      try (FileOutputStream out = new FileOutputStream(file)) {
        check(changed.compress(Bitmap.CompressFormat.PNG, 100, out), "PNG encode succeeds");
      }
      Bitmap decoded = BitmapFactory.decodeFile(file.toString());
      check(changed.sameAs(decoded), "exported PNG decodes to actual upstream rendered pixels");
      File throughAdapter = new File(c.getCacheDir(), "destination-adapter.png");
      LocalPhoto.export(c, Uri.fromFile(throughAdapter), changed);
      Bitmap adapterOutput = BitmapFactory.decodeFile(throughAdapter.toString());
      check(
          changed.sameAs(adapterOutput),
          "actual platform destination adapter writes complete upstream pixels");
      adapterOutput.recycle();
      throughAdapter.delete();
      decoded.recycle();
      plain.recycle();
      changed.recycle();
      file.delete();
    }
    for (int orientation : new int[] {5, 7}) {
      File jpeg = new File(c.getCacheDir(), "orientation-" + orientation + ".jpg");
      try (InputStream in = getContext().getAssets().open(jpeg.getName());
          FileOutputStream out = new FileOutputStream(jpeg)) {
        byte[] bytes = new byte[8192];
        int n;
        while ((n = in.read(bytes)) != -1) out.write(bytes, 0, n);
      }
      String imported = LocalPhoto.importPhoto(c, Uri.fromFile(jpeg));
      File normalized = LocalPhoto.source(c, imported);
      Bitmap result = LocalPhoto.decode(normalized);
      check(
          result.getWidth() == 120 && result.getHeight() == 160,
          "actual EXIF"
              + orientation
              + " import dimensions: "
              + result.getWidth()
              + "x"
              + result.getHeight()
              + "; platform tag="
              + new androidx.exifinterface.media.ExifInterface(jpeg.toString())
                  .getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, -1));
      int top = result.getPixel(8, 8), bottom = result.getPixel(111, 151);
      check(
          orientation == 5
              ? Color.red(top) > 220
                  && Color.green(top) < 40
                  && Color.red(bottom) > 220
                  && Color.green(bottom) > 220
              : Color.red(top) > 220
                  && Color.green(top) > 220
                  && Color.red(bottom) > 220
                  && Color.green(bottom) < 40,
          "actual EXIF" + orientation + " imported corner mapping");
      try (DataInputStream in = new DataInputStream(new FileInputStream(normalized))) {
        check(in.readLong() == 0x89504e470d0a1a0aL, "normalized import is PNG");
        boolean hidden = false;
        while (in.available() > 0) {
          int length = in.readInt();
          byte[] type = new byte[4];
          in.readFully(type);
          String tag = new String(type, java.nio.charset.StandardCharsets.US_ASCII);
          if (tag.equals("eXIf") || tag.equals("tEXt") || tag.equals("iTXt") || tag.equals("zTXt"))
            hidden = true;
          in.skipBytes(length + 4);
          if (tag.equals("IEND")) break;
        }
        check(!hidden, "normalized PNG omits original EXIF and text metadata");
      }
      result.recycle();
      normalized.delete();
      jpeg.delete();
    }
    check(
        MainActivity.safeResult(Uri.parse("content://example.provider/image/1")),
        "valid SAF result accepted");
    check(
        !MainActivity.safeResult(
            Uri.parse("file:///data/data/dev.oritwig.photo/shared_prefs/photo-session.xml")),
        "private-file URI attack rejected");
    check(!MainActivity.safeResult(Uri.parse("content:///bad")), "missing authority rejected");
    File fixture = new File(c.getCacheDir(), "source-fixture.png");
    try (FileOutputStream out = new FileOutputStream(fixture)) {
      input.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    String name = LocalPhoto.importPhoto(c, Uri.fromFile(fixture));
    Bitmap saved = LocalPhoto.decode(LocalPhoto.source(c, name));
    check(
        saved.getWidth() == 320 && saved.getHeight() == 240, "normalized private import persists");
    check(fixture.isFile(), "import leaves provider source unchanged");
    saved.recycle();
    LocalPhoto.source(c, name).delete();
    fixture.delete();
    File bad = new File(c.getCacheDir(), "bad-fixture.png");
    try (FileOutputStream out = new FileOutputStream(bad)) {
      out.write(new byte[] {1, 2, 3});
    }
    boolean rejected = false;
    try {
      LocalPhoto.importPhoto(c, Uri.fromFile(bad));
    } catch (IOException expected) {
      rejected = true;
    }
    check(rejected, "malformed import has actionable failure");
    bad.delete();
    input.recycle();
    Activity activity =
        startActivitySync(
            new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    waitForIdleSync();
    runOnMainSync(
        () -> {
          ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
          check(
              find(
                  decor,
                  c.getSharedPreferences("photo-session", 0).getString("source", null) == null
                      ? "Open photo"
                      : "Replace photo"),
              "real app presents its import action for current session");
          check(find(decor, "Export PNG"), "real product has export action");
          check(hasScroll(decor), "whole screen remains scroll reachable");
          activity.finish();
        });
    check(
        c.getPackageManager()
                .getPackageInfo(
                    c.getPackageName(), android.content.pm.PackageManager.GET_PERMISSIONS)
                .requestedPermissions
            == null,
        "actual app declares no permissions");
  }

  private boolean find(View v, String label) {
    if (v instanceof TextView && ((TextView) v).getText().toString().equals(label)) return true;
    if (v instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++)
        if (find(((ViewGroup) v).getChildAt(i), label)) return true;
    return false;
  }

  private boolean hasScroll(View v) {
    if (v instanceof ScrollView) return true;
    if (v instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++)
        if (hasScroll(((ViewGroup) v).getChildAt(i))) return true;
    return false;
  }
}
