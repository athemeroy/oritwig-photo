/* Original thin Android product shell. GPL-2.0-or-later.
 * Pixels are rendered only by the retained Telegram engine and crop implementation. */
package dev.oritwig.photo;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import dev.oritwig.telegram.photo.PhotoEngine;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
  private static final int OPEN = 10, EXPORT = 11;
  private static final Object SESSION_LOCK = new Object();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final SharedPreferences.OnSharedPreferenceChangeListener sessionListener =
      (p, key) -> {
        if ("source".equals(key)) handler.post(this::reconcileSession);
      };
  private final ExecutorService work = Executors.newSingleThreadExecutor();
  private SharedPreferences prefs;
  private EditValues edit;
  private String source;
  private ImageView preview;
  private TextView status, dimension;
  private Button export, importButton, more, reset, mirrorButton;
  private LinearLayout root, controls;
  private Bitmap displayed;
  private PhotoEngine cachedEngine;
  private String cachedKey;
  private volatile boolean dead;
  private volatile int revision;
  private boolean busy, dark, retained = true;
  private int ink, muted, bg, surface, accent;
  private final Runnable renderSoon = () -> render(false);

  @Override
  public void onCreate(Bundle state) {
    prefs = getSharedPreferences("photo-session", MODE_PRIVATE);
    int theme = prefs.getInt("theme", 0);
    dark = theme == 2 || (theme == 0 && (getResources().getConfiguration().uiMode & 48) == 32);
    setTheme(
        dark
            ? android.R.style.Theme_Material_NoActionBar
            : android.R.style.Theme_Material_Light_NoActionBar);
    super.onCreate(state);
    ink = Color.parseColor(dark ? "#F1F1E7" : "#183832");
    muted = Color.parseColor(dark ? "#BCCEC2" : "#526E65");
    bg = Color.parseColor(dark ? "#132620" : "#F3F5EC");
    surface = Color.parseColor(dark ? "#21392F" : "#FFFFFF");
    accent = Color.parseColor(dark ? "#8FD2AC" : "#236F4A");
    getWindow().setStatusBarColor(bg);
    getWindow().setNavigationBarColor(bg);
    if (!dark)
      getWindow()
          .getDecorView()
          .setSystemUiVisibility(
              View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    source = prefs.getString("source", null);
    edit = EditValues.read(prefs);
    prefs.registerOnSharedPreferenceChangeListener(sessionListener);
    show();
    if (source != null) render(false);
  }

  private int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private LinearLayout column() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  private TextView text(String s, int size, boolean bold) {
    TextView v = new TextView(this);
    v.setText(s);
    v.setTextSize(size);
    v.setTextColor(ink);
    if (bold) v.setTypeface(null, Typeface.BOLD);
    v.setPadding(0, dp(5), 0, dp(5));
    return v;
  }

  private GradientDrawable rounded(int color) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(18));
    return d;
  }

  private LinearLayout card() {
    LinearLayout l = column();
    l.setPadding(dp(16), dp(10), dp(16), dp(14));
    l.setBackground(rounded(surface));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.setMargins(0, dp(12), 0, 0);
    root.addView(l, p);
    return l;
  }

  private Button button(String title, Runnable action) {
    Button b = new Button(this);
    b.setText(title);
    b.setAllCaps(false);
    b.setTextColor(ink);
    b.setMinHeight(dp(48));
    b.setOnClickListener(
        v -> {
          if (!busy) action.run();
        });
    return b;
  }

  private void row(LinearLayout parent, Button... buttons) {
    LinearLayout row = new LinearLayout(this);
    for (Button b : buttons) {
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
      p.setMargins(dp(2), 0, dp(2), 0);
      row.addView(b, p);
    }
    parent.addView(row);
  }

  private void show() {
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    if (Build.VERSION.SDK_INT >= 35) {
      scroll.setOnApplyWindowInsetsListener(
          (view, insets) -> {
            android.graphics.Insets bars =
                insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsets.CONSUMED;
          });
    }
    scroll.setBackgroundColor(bg);
    root = column();
    root.setPadding(dp(18), dp(12), dp(18), dp(30));
    scroll.addView(root);
    setContentView(scroll);
    root.addView(text("ORITWIG  /  PHOTO", 12, true));
    root.addView(text("Make the light yours", 27, true));
    TextView sub = text("Local photos. Telegram’s real editing engine.", 14, false);
    sub.setTextColor(muted);
    root.addView(sub);
    importButton =
        button(
            source == null ? "Open photo" : "Replace photo",
            () -> {
              if (source == null) pick();
              else
                new AlertDialog.Builder(this)
                    .setTitle("Replace this photo?")
                    .setMessage(
                        "Your current adjustments will be replaced after a new image imports"
                            + " successfully. Existing exported images stay unchanged.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Choose photo", (d, w) -> pick())
                    .show();
            });
    export = button("Export PNG", this::chooseExport);
    more = button("More", this::more);
    row(root, importButton, export, more);
    status =
        text(
            source == null
                ? "Open a PNG, JPEG or WebP to begin. No sign-in needed."
                : "Restoring your last session…",
            13,
            false);
    status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    status.setTextColor(muted);
    root.addView(status);
    LinearLayout photo = card();
    preview = new ImageView(this);
    preview.setContentDescription("Edited photo preview");
    preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
    preview.setAdjustViewBounds(true);
    photo.addView(preview, new LinearLayout.LayoutParams(-1, dp(280)));
    dimension = text(source == null ? "Import never changes your selected file." : "", 12, false);
    dimension.setTextColor(muted);
    photo.addView(dimension);
    if (displayed != null) preview.setImageBitmap(displayed);
    controls = card();
    controls.addView(text("Frame", 19, true));
    row(
        controls,
        button(
            "Rotate 90°",
            () -> {
              edit.turns = (edit.turns + 1) % 4;
              changed();
            }),
        mirrorButton =
            button(
                edit.mirror ? "Unmirror" : "Mirror",
                () -> {
                  edit.mirror = !edit.mirror;
                  mirrorButton.setText(edit.mirror ? "Unmirror" : "Mirror");
                  changed();
                }));
    Spinner crops = new Spinner(this);
    crops.setContentDescription("Centered crop aspect ratio");
    crops.setAdapter(
        new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            new String[] {
              "Original proportions", "Square · 1:1", "Landscape · 4:3", "Portrait · 3:4"
            }));
    crops.setSelection(edit.crop);
    crops.setMinimumHeight(dp(48));
    controls.addView(crops);
    crops.setOnItemSelectedListener(
        new android.widget.AdapterView.OnItemSelectedListener() {
          public void onNothingSelected(AdapterView<?> p) {}

          public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
            if (pos != edit.crop && !busy) {
              edit.crop = pos;
              changed();
            }
          }
        });
    controls.addView(text("Light & color", 19, true));
    for (int i = 0; i < EditValues.LABELS.length; i++) {
      final int index = i;
      addSlider(
          controls,
          EditValues.LABELS[i],
          EditValues.minimum(i),
          100,
          edit.values[i],
          value -> {
            edit.values[index] = value;
            changed();
          });
    }
    row(
        controls,
        button("Tone curves", this::curves),
        reset =
            button(
                "Reset all",
                () ->
                    new AlertDialog.Builder(this)
                        .setTitle("Reset adjustments?")
                        .setMessage("Return to the imported photo, including its original framing.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton(
                            "Reset",
                            (d, w) -> {
                              edit = new EditValues();
                              persist();
                              show();
                              render(false);
                            })
                        .show()));
    TextView foot =
        text(
            "Edits stay on this device. Exported PNGs contain rendered pixels, without the source"
                + " EXIF/GPS metadata.",
            12,
            false);
    foot.setTextColor(muted);
    root.addView(foot);
    updateButtons();
  }

  private interface IntValue {
    void changed(int value);
  }

  private void addSlider(
      LinearLayout parent, String label, int min, int max, int initial, IntValue change) {
    TextView title = text(label + "  " + initial, 14, true);
    parent.addView(title);
    SeekBar slider = new SeekBar(this);
    slider.setMax(max - min);
    slider.setProgress(initial - min);
    slider.setContentDescription(label);
    slider.setMinimumHeight(dp(48));
    slider.setProgressTintList(ColorStateList.valueOf(accent));
    parent.addView(slider, new LinearLayout.LayoutParams(-1, dp(48)));
    slider.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onStartTrackingTouch(SeekBar s) {}

          public void onStopTrackingTouch(SeekBar s) {}

          public void onProgressChanged(SeekBar s, int value, boolean user) {
            title.setText(label + "  " + (value + min));
            s.setContentDescription(label + ", " + (value + min));
            if (user && !busy) change.changed(value + min);
          }
        });
  }

  private void curves() {
    EditValues draft = edit.copy();
    LinearLayout body = column();
    body.setPadding(dp(20), dp(8), dp(20), dp(12));
    Spinner channels = new Spinner(this);
    channels.setAdapter(
        new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            new String[] {"Luminance", "Red", "Green", "Blue"}));
    channels.setContentDescription("Curve channel");
    body.addView(channels);
    LinearLayout sliders = column();
    body.addView(sliders);
    channels.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onNothingSelected(AdapterView<?> p) {}

          public void onItemSelected(AdapterView<?> p, View v, int channel, long id) {
            sliders.removeAllViews();
            String[] names = {"Blacks", "Shadows", "Midtones", "Highlights", "Whites"};
            for (int k = 0; k < 5; k++) {
              final int point = k;
              addSlider(
                  sliders,
                  names[k],
                  0,
                  100,
                  draft.curves[channel][k],
                  x -> draft.curves[channel][point] = x);
            }
          }
        });
    ScrollView sc = new ScrollView(this);
    sc.addView(body);
    new AlertDialog.Builder(this)
        .setTitle("Telegram tone curves")
        .setView(sc)
        .setNegativeButton("Cancel", null)
        .setPositiveButton(
            "Apply",
            (d, w) -> {
              edit = draft;
              changed();
            })
        .show();
  }

  private void reconcileSession() {
    if (dead || busy || prefs == null || Objects.equals(source, prefs.getString("source", null)))
      return;
    revision++;
    source = prefs.getString("source", null);
    edit = EditValues.read(prefs);
    if (displayed != null) {
      preview.setImageDrawable(null);
      displayed.recycle();
      displayed = null;
    }
    show();
    if (source != null) render(false);
  }

  private boolean persist() {
    synchronized (SESSION_LOCK) {
      if (!Objects.equals(source, prefs.getString("source", null))) {
        reconcileSession();
        return false;
      }
      SharedPreferences.Editor p = prefs.edit();
      edit.write(p);
      boolean ok = p.commit();
      retained = ok;
      if (!ok && status != null)
        status.setText("Could not retain edits. Free some device space, then try again.");
      return ok;
    }
  }

  private void changed() {
    if (source == null) return;
    if (!persist()) return;
    handler.removeCallbacks(renderSoon);
    handler.postDelayed(renderSoon, 250);
  }

  private void updateButtons() {
    export.setEnabled(source != null && !busy);
    importButton.setEnabled(!busy);
    more.setEnabled(!busy);
    controls.setVisibility(source == null ? View.GONE : View.VISIBLE);
    setEnabled(controls, source != null && !busy);
  }

  private void setEnabled(View v, boolean enabled) {
    v.setEnabled(enabled);
    if (v instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++)
        setEnabled(((ViewGroup) v).getChildAt(i), enabled);
  }

  private void setBusy(boolean value, String message) {
    busy = value;
    status.setText(message);
    updateButtons();
  }

  private void pick() {
    Intent i =
        new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("image/*")
            .addCategory(Intent.CATEGORY_OPENABLE);
    try {
      startActivityForResult(i, OPEN);
    } catch (ActivityNotFoundException e) {
      error("No system document picker is installed.");
    }
  }

  private void chooseExport() {
    handler.removeCallbacks(renderSoon);
    if (!persist()) return;
    Intent i =
        new Intent(Intent.ACTION_CREATE_DOCUMENT)
            .setType("image/png")
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_TITLE, "Oritwig-Photo-" + System.currentTimeMillis() + ".png");
    try {
      startActivityForResult(i, EXPORT);
    } catch (ActivityNotFoundException e) {
      error("No system document destination is installed.");
    }
  }

  static boolean safeResult(Uri u) {
    return u != null
        && "content".equals(u.getScheme())
        && u.getAuthority() != null
        && !u.getAuthority().isEmpty();
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (result != RESULT_OK) return;
    Uri uri = data == null ? null : data.getData();
    if (!safeResult(uri)) {
      error(
          "The document provider returned an unsupported location. Please use the system picker.");
      return;
    }
    if (request == OPEN) importPhoto(uri);
    else if (request == EXPORT) exportPhoto(uri);
  }

  private void importPhoto(Uri uri) {
    revision++;
    setBusy(true, "Importing and preparing your photo…");
    final String old = source;
    work.execute(
        () -> {
          String name = null;
          try {
            name = LocalPhoto.importPhoto(this, uri);
            EditValues fresh = new EditValues();
            synchronized (SESSION_LOCK) {
              if (!Objects.equals(old, prefs.getString("source", null)))
                throw new IOException(
                    "Another photo was opened while this import was preparing. The newer session"
                        + " was kept.");
              SharedPreferences.Editor p = prefs.edit().putString("source", name);
              fresh.write(p);
              if (!p.commit())
                throw new IOException(
                    "Could not retain the imported photo. Free device space and retry.");
            }
            if (old != null && !old.equals(name)) LocalPhoto.source(this, old).delete();
            final String ready = name;
            runOnUiThread(
                () -> {
                  if (dead) return;
                  source = ready;
                  edit = fresh;
                  busy = false;
                  show();
                  render(false);
                });
          } catch (Throwable e) {
            if (name != null && !name.equals(prefs.getString("source", null)))
              try {
                LocalPhoto.source(this, name).delete();
              } catch (IOException ignored) {
              }
            failure(e);
          }
        });
  }

  private Bitmap renderPixels(String name, EditValues state, int maxSide, boolean useCache)
      throws Exception {
    String key = name + ":" + state.turns + ":" + state.crop + ":" + state.mirror + ":" + maxSide;
    PhotoEngine engine = null;
    if (useCache && key.equals(cachedKey)) engine = cachedEngine;
    if (engine == null) {
      Bitmap original = LocalPhoto.decode(LocalPhoto.source(this, name)), cropped = null;
      try {
        cropped = LocalPhoto.crop(original, state);
        engine = new PhotoEngine(cropped, 0, maxSide);
      } finally {
        original.recycle();
        if (cropped != null) cropped.recycle();
      }
      if (useCache) {
        if (cachedEngine != null) cachedEngine.closeAsync().get(30, TimeUnit.SECONDS);
        cachedEngine = engine;
        cachedKey = key;
      }
    }
    try {
      return (state.originalFilters()
              ? engine.renderOriginal()
              : engine.render(state.upstreamState()))
          .get(60, TimeUnit.SECONDS);
    } finally {
      if (!useCache) engine.closeAsync().get(30, TimeUnit.SECONDS);
    }
  }

  private void render(boolean ignored) {
    if (source == null || dead) return;
    final int token = ++revision;
    final String name = source;
    final EditValues snapshot = edit.copy();
    status.setText("Rendering with Telegram’s photo engine…");
    work.execute(
        () -> {
          try {
            if (token != revision || dead) return;
            Bitmap image = renderPixels(name, snapshot, 720, true);
            runOnUiThread(
                () -> {
                  if (dead || token != revision) {
                    image.recycle();
                    return;
                  }
                  Bitmap prior = displayed;
                  displayed = image;
                  preview.setImageBitmap(image);
                  if (prior != null && prior != image) prior.recycle();
                  dimension.setText(
                      image.getWidth()
                          + " × "
                          + image.getHeight()
                          + " preview · centered crop · PNG export");
                  status.setText(
                      retained
                          ? "Ready · adjustments retained on this device"
                          : "Preview ready · could not retain edits; free device space and retry");
                });
          } catch (Throwable e) {
            if (token == revision) failure(e);
          }
        });
  }

  private void exportPhoto(Uri destination) {
    if (source == null) return;
    final String name = source;
    final EditValues snapshot = edit.copy();
    final int size = prefs.getInt("exportSize", 2048);
    revision++;
    setBusy(true, "Rendering and writing PNG…");
    work.execute(
        () -> {
          Bitmap output = null;
          try {
            output = renderPixels(name, snapshot, size, false);
            LocalPhoto.export(this, destination, output);
            String message =
                "PNG saved · " + output.getWidth() + " × " + output.getHeight() + " pixels";
            runOnUiThread(
                () -> {
                  if (!dead) setBusy(false, message);
                });
          } catch (Throwable e) {
            failure(e);
          } finally {
            if (output != null) output.recycle();
          }
        });
  }

  private void failure(Throwable error) {
    Throwable e = error;
    while ((e instanceof ExecutionException || e instanceof CompletionException)
        && e.getCause() != null) e = e.getCause();
    final String msg =
        e instanceof OutOfMemoryError
            ? "Not enough memory. Try a smaller photo."
            : e instanceof SecurityException
                ? "Access was denied. Choose the file again or a different destination."
                : e.getMessage() == null
                    ? "The operation failed. Your previous saved photo is unchanged."
                    : e.getMessage();
    runOnUiThread(
        () -> {
          if (!dead) {
            setBusy(false, "Could not finish: " + msg);
            new AlertDialog.Builder(this)
                .setTitle("Photo needs attention")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
          }
        });
  }

  private void error(String message) {
    failure(new IOException(message));
  }

  private void more() {
    new AlertDialog.Builder(this)
        .setTitle("Oritwig Photo")
        .setItems(
            new String[] {
              "Settings", "How it works & privacy", "Source & licenses", "Remove current photo"
            },
            (d, w) -> {
              if (w == 0) settings();
              if (w == 1) info();
              if (w == 2) licenses();
              if (w == 3) remove();
            })
        .show();
  }

  private void settings() {
    LinearLayout box = column();
    box.setPadding(dp(20), dp(8), dp(20), dp(8));
    box.addView(text("Appearance", 16, true));
    Spinner theme = new Spinner(this);
    theme.setAdapter(
        new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            new String[] {"System", "Light", "Dark"}));
    theme.setSelection(prefs.getInt("theme", 0));
    theme.setContentDescription("Appearance");
    box.addView(theme);
    box.addView(text("Maximum exported long edge", 16, true));
    Spinner size = new Spinner(this);
    size.setAdapter(
        new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            new String[] {"1024 pixels", "2048 pixels"}));
    size.setSelection(prefs.getInt("exportSize", 2048) == 1024 ? 0 : 1);
    size.setContentDescription("Maximum exported long edge");
    box.addView(size);
    box.addView(
        text(
            "Images are never enlarged. Larger originals are normalized to at most 2048 pixels on"
                + " import.",
            13,
            false));
    new AlertDialog.Builder(this)
        .setTitle("Settings")
        .setView(box)
        .setNegativeButton("Cancel", null)
        .setPositiveButton(
            "Save",
            (d, w) -> {
              if (!prefs
                  .edit()
                  .putInt("theme", theme.getSelectedItemPosition())
                  .putInt("exportSize", size.getSelectedItemPosition() == 0 ? 1024 : 2048)
                  .commit()) {
                error("Settings could not be retained.");
                return;
              }
              recreate();
            })
        .show();
  }

  private void info() {
    new AlertDialog.Builder(this)
        .setTitle("A small, local photo finisher")
        .setMessage(
            "Open a PNG, JPEG or WebP, choose a centered crop, rotate or mirror, adjust light and"
                + " color, then export a PNG. Your last photo and adjustments resume after"
                + " reopening.\n\n"
                + "Imports: up to 25 MiB and 32 megapixels, at least 8 pixels per side. Prepared"
                + " images are at most 2048 pixels. No freeform crop, layers, paint, HDR or video"
                + " is promised here.\n\n"
                + "No accounts, network permission, analytics or uploads. This app keeps a"
                + " normalized private image and local settings; automatic cloud/device-transfer"
                + " backup is disabled. Uninstalling removes them. Export destinations can sync to"
                + " a cloud provider you choose. Exports do not contain source EXIF/GPS; deleting"
                + " the local session does not retract exported files.\n\n"
                + "Rendering uses Telegram’s original GLES filter chain and crop geometry. Adjusted"
                + " images retain its baseline sharpening and effect-dependent alpha behavior."
                + " Reset bypasses filters. This product is not affiliated with Telegram.")
        .setPositiveButton("OK", null)
        .show();
  }

  private void licenses() {
    LinearLayout box = column();
    box.setPadding(dp(18), dp(12), dp(18), dp(12));
    box.addView(text("Primary engine: Telegram Android", 18, true));
    box.addView(text("Oritwig Photo 0.1.0", 14, false));
    box.addView(
        button(
            "Open Oritwig Photo source",
            () -> {
              try {
                startActivity(
                    new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/athemeroy/oritwig-photo")));
              } catch (ActivityNotFoundException error) {
                error("No browser is installed. Source: github.com/athemeroy/oritwig-photo");
              }
            }));
    box.addView(
        text(
            "Pinned revision f2908b14133bbffbf7ab04f641ecb5bfaf533242. Retained GLSL/filter passes,"
                + " native calcCDT and crop geometry. New work: Android navigation, picker/file"
                + " destinations, settings and last-session adapter. No Telegram service or account"
                + " code.",
            14,
            false));
    box.addView(
        button(
            "Open upstream source",
            () -> {
              try {
                startActivity(
                    new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(
                            "https://github.com/DrKLO/Telegram/tree/f2908b14133bbffbf7ab04f641ecb5bfaf533242")));
              } catch (ActivityNotFoundException e) {
                error("No browser is installed. Source: github.com/DrKLO/Telegram");
              }
            }));
    box.addView(button("GNU GPL version 3", () -> asset("GPL-3.0.txt")));
    box.addView(button("App and engine notices", () -> asset("APP-NOTICE.txt")));
    box.addView(button("AndroidX dependency notices", () -> asset("ANDROIDX-NOTICE.txt")));
    box.addView(button("Apache License 2.0", () -> asset("Apache-2.0.txt")));
    ScrollView sc = new ScrollView(this);
    sc.addView(box);
    new AlertDialog.Builder(this)
        .setTitle("Source & licenses")
        .setView(sc)
        .setPositiveButton("Close", null)
        .show();
  }

  private void asset(String path) {
    try (InputStream in = getAssets().open(path)) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] b = new byte[8192];
      int n;
      while ((n = in.read(b)) != -1) out.write(b, 0, n);
      TextView t = text(new String(out.toByteArray(), StandardCharsets.UTF_8), 12, false);
      t.setTextIsSelectable(true);
      t.setPadding(dp(18), dp(10), dp(18), dp(10));
      ScrollView sc = new ScrollView(this);
      sc.addView(t);
      new AlertDialog.Builder(this)
          .setTitle(path)
          .setView(sc)
          .setPositiveButton("Close", null)
          .show();
    } catch (IOException e) {
      error("License text is unavailable.");
    }
  }

  private void remove() {
    if (source == null) return;
    new AlertDialog.Builder(this)
        .setTitle("Remove this local photo?")
        .setMessage(
            "The current photo and adjustments will be removed from this app. Your original file"
                + " and exported images are unaffected. This cannot be undone.")
        .setNegativeButton("Cancel", null)
        .setPositiveButton(
            "Remove",
            (d, w) -> {
              String old = source;
              SharedPreferences.Editor p = prefs.edit().remove("source");
              new EditValues().write(p);
              if (!p.commit()) {
                error("Could not remove the session.");
                return;
              }
              revision++;
              source = null;
              edit = new EditValues();
              preview.setImageDrawable(null);
              if (displayed != null) {
                displayed.recycle();
                displayed = null;
              }
              work.execute(
                  () -> {
                    try {
                      LocalPhoto.source(this, old).delete();
                    } catch (IOException ignored) {
                    }
                  });
              show();
            })
        .show();
  }

  @Override
  protected void onDestroy() {
    dead = true;
    prefs.unregisterOnSharedPreferenceChangeListener(sessionListener);
    revision++;
    handler.removeCallbacks(renderSoon);
    work.execute(
        () -> {
          if (cachedEngine != null) cachedEngine.close();
        });
    work.shutdown();
    super.onDestroy();
  }
}
