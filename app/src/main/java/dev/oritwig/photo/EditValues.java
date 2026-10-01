/* Original UI/state adapter. GPL-2.0-or-later. No image-processing algorithm. */
package dev.oritwig.photo;

import android.content.SharedPreferences;
import dev.oritwig.telegram.photo.CurvesValue;
import dev.oritwig.telegram.photo.SavedFilterState;
import java.util.Arrays;

final class EditValues {
  static final String[] LABELS = {
    "Enhance",
    "Exposure",
    "Contrast",
    "Saturation",
    "Warmth",
    "Shadows",
    "Highlights",
    "Sharpen",
    "Fade",
    "Grain",
    "Vignette"
  };
  static final String[] KEYS = {
    "enhanceValue",
    "exposureValue",
    "contrastValue",
    "saturationValue",
    "warmthValue",
    "shadowsValue",
    "highlightsValue",
    "sharpenValue",
    "fadeValue",
    "grainValue",
    "vignetteValue"
  };
  static final int[] DEFAULT_CURVE = {0, 25, 50, 75, 100};
  final int[] values = new int[KEYS.length];
  final int[][] curves = {
    DEFAULT_CURVE.clone(), DEFAULT_CURVE.clone(), DEFAULT_CURVE.clone(), DEFAULT_CURVE.clone()
  };
  int turns, crop;
  boolean mirror;

  static int minimum(int i) {
    return i >= 1 && i <= 6 ? -100 : 0;
  }

  EditValues copy() {
    EditValues e = new EditValues();
    System.arraycopy(values, 0, e.values, 0, values.length);
    for (int c = 0; c < 4; c++) System.arraycopy(curves[c], 0, e.curves[c], 0, 5);
    e.turns = turns;
    e.crop = crop;
    e.mirror = mirror;
    return e;
  }

  boolean originalFilters() {
    for (int v : values) if (v != 0) return false;
    for (int[] c : curves) if (!Arrays.equals(c, DEFAULT_CURVE)) return false;
    return true;
  }

  SavedFilterState upstreamState() {
    SavedFilterState s = new SavedFilterState();
    s.enhanceValue = values[0];
    s.exposureValue = values[1];
    s.contrastValue = values[2];
    s.saturationValue = values[3];
    s.warmthValue = values[4];
    s.shadowsValue = values[5];
    s.highlightsValue = values[6];
    s.sharpenValue = values[7];
    s.fadeValue = values[8];
    s.grainValue = values[9];
    s.vignetteValue = values[10];
    CurvesValue[] channels = {
      s.curvesToolValue.luminanceCurve,
      s.curvesToolValue.redCurve,
      s.curvesToolValue.greenCurve,
      s.curvesToolValue.blueCurve
    };
    for (int c = 0; c < 4; c++) {
      CurvesValue x = channels[c];
      x.blacksLevel = curves[c][0];
      x.shadowsLevel = curves[c][1];
      x.midtonesLevel = curves[c][2];
      x.highlightsLevel = curves[c][3];
      x.whitesLevel = curves[c][4];
      x.interpolateCurve();
    }
    return s;
  }

  void write(SharedPreferences.Editor p) {
    for (int i = 0; i < values.length; i++) p.putInt(KEYS[i], values[i]);
    for (int c = 0; c < 4; c++)
      for (int j = 0; j < 5; j++) p.putInt("curve_" + c + "_" + j, curves[c][j]);
    p.putInt("turns", turns).putInt("crop", crop).putBoolean("mirror", mirror);
  }

  static EditValues read(SharedPreferences p) {
    EditValues e = new EditValues();
    try {
      for (int i = 0; i < e.values.length; i++)
        e.values[i] = Math.max(minimum(i), Math.min(100, p.getInt(KEYS[i], 0)));
      for (int c = 0; c < 4; c++)
        for (int j = 0; j < 5; j++)
          e.curves[c][j] =
              Math.max(0, Math.min(100, p.getInt("curve_" + c + "_" + j, DEFAULT_CURVE[j])));
      e.turns = Math.floorMod(p.getInt("turns", 0), 4);
      e.crop = Math.max(0, Math.min(3, p.getInt("crop", 0)));
      e.mirror = p.getBoolean("mirror", false);
    } catch (ClassCastException bad) {
      return new EditValues();
    }
    return e;
  }
}
