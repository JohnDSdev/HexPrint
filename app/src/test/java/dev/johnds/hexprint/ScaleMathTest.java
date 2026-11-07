package dev.johnds.hexprint;
import org.junit.Test;
import static org.junit.Assert.*;
public class ScaleMathTest {
  @Test public void pointsConversion() {
    assertEquals(72.0, ScaleMath.mmToPoints(25.4), 0.000001);
    assertEquals(283.4645669, ScaleMath.mmToPoints(100), 0.000001);
  }
  @Test public void correctMeasuredPrint() {
    assertEquals(100.0 / 98.5, ScaleMath.correctionFromMeasurement(100, 98.5), 1e-9);
  }
  @Test public void compoundScale() {
    assertEquals(250, ScaleMath.scaledDimension(200, 125, 1), 1e-9);
    assertEquals(202, ScaleMath.scaledDimension(200, 100, 1.01), 1e-9);
  }
  @Test(expected = IllegalArgumentException.class) public void zeroInvalid() {
    ScaleMath.correctionFromMeasurement(100, 0);
  }
}
