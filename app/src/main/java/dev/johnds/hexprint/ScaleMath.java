package dev.johnds.hexprint;
public final class ScaleMath {
  private ScaleMath() { }
  public static double mmToPoints(double mm) { return mm * 72.0 / 25.4; }
  public static double correctionFromMeasurement(double targetMm, double measuredMm) {
    if (!(targetMm > 0) || !(measuredMm > 0) || !Double.isFinite(targetMm) || !Double.isFinite(measuredMm))
      throw new IllegalArgumentException("Measurements must be finite and positive");
    return targetMm / measuredMm;
  }
  public static double scaledDimension(double points, double documentPercent, double printerFactor) {
    return points * documentPercent / 100.0 * printerFactor;
  }
}
