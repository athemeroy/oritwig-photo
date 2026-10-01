package dev.oritwig.photo;

import static org.junit.Assert.*;

import org.junit.Test;

public class EditValuesTest {
  @Test
  public void initialUsesOriginalBypass() {
    assertTrue(new EditValues().originalFilters());
  }

  @Test
  public void adjustmentsDisableBypass() {
    EditValues e = new EditValues();
    for (int i = 0; i < e.values.length; i++) {
      e.values[i] = 1;
      assertFalse(e.originalFilters());
      e.values[i] = 0;
    }
    assertTrue(e.originalFilters());
  }

  @Test
  public void everyCurveChannelIsObserved() {
    EditValues e = new EditValues();
    for (int i = 0; i < 4; i++) {
      e.curves[i][2] = 70;
      assertFalse(e.originalFilters());
      e.curves[i][2] = 50;
    }
    assertTrue(e.originalFilters());
  }

  @Test
  public void copyDoesNotShareMutableArrays() {
    EditValues a = new EditValues();
    a.values[0] = 60;
    a.curves[2][1] = 45;
    EditValues b = a.copy();
    b.values[0] = 10;
    b.curves[2][1] = 20;
    assertEquals(60, a.values[0]);
    assertEquals(45, a.curves[2][1]);
  }

  @Test
  public void snapshotRetainsFrame() {
    EditValues a = new EditValues();
    a.turns = 3;
    a.crop = 2;
    a.mirror = true;
    EditValues b = a.copy();
    assertEquals(3, b.turns);
    assertEquals(2, b.crop);
    assertTrue(b.mirror);
  }

  @Test
  public void upstreamParametersKeepTheirUnits() {
    EditValues e = new EditValues();
    for (int i = 0; i < e.values.length; i++) e.values[i] = i + 10;
    dev.oritwig.telegram.photo.SavedFilterState s = e.upstreamState();
    assertEquals(10, s.enhanceValue, 0);
    assertEquals(11, s.exposureValue, 0);
    assertEquals(20, s.vignetteValue, 0);
  }

  @Test
  public void curveAdapterRefreshesActualUpstreamCache() {
    EditValues e = new EditValues();
    e.curves[0][2] = 77;
    dev.oritwig.telegram.photo.SavedFilterState s = e.upstreamState();
    assertEquals(77, s.curvesToolValue.luminanceCurve.midtonesLevel, 0);
    assertEquals(200, s.curvesToolValue.luminanceCurve.getDataPoints().length);
  }

  @Test
  public void onlySignedControlsHaveNegativeMinima() {
    for (int i = 0; i < EditValues.KEYS.length; i++)
      assertEquals(i >= 1 && i <= 6 ? -100 : 0, EditValues.minimum(i));
  }
}
