/* Original process-start housekeeping for the one local session. GPL-2.0-or-later. */
package dev.oritwig.photo;

import android.app.Application;
import java.io.File;

public final class PhotoApplication extends Application {
  @Override
  public void onCreate() {
    super.onCreate();
    // At process start no import worker exists. Never run this while an import is active.
    final String current;
    try {
      current = getSharedPreferences("photo-session", MODE_PRIVATE).getString("source", null);
    } catch (ClassCastException uncertain) {
      return;
    }
    if (current != null && !current.matches("photo-[0-9a-f-]+\\.png")) return;
    File[] copies = getFilesDir().listFiles();
    if (current != null && new File(getFilesDir(), current).isFile() && copies != null)
      for (File file : copies)
        if (file.isFile()
            && file.getName().matches("photo-[0-9a-f-]+\\.png")
            && !file.getName().equals(current)) file.delete();
    File[] staging = getCacheDir().listFiles();
    if (staging != null)
      for (File file : staging)
        if (file.isFile()
            && (file.getName().startsWith("photo-import-")
                || file.getName().startsWith("photo-export-"))
            && file.getName().endsWith(".tmp")) file.delete();
  }
}
