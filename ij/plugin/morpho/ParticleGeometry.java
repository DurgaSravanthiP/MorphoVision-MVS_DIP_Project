package ij.plugin.morpho;

/**
 * Holds all geometric measurements for a single detected particle.
 */
public class ParticleGeometry {

    public final int    id;
    public final double area;           // units²
    public final double perimeter;      // units
    public final double circularity;    // 4π·Area/Perimeter² → [0,1]
    public final double equivDiameter;  // 2√(Area/π)
    public final double aspectRatio;    // major / minor axis
    public final double feretMax;       // max caliper distance
    public final double feretMin;       // min caliper distance
    public final double feretAngle;     // angle of max caliper

    public ParticleGeometry(int id, double area, double perimeter,
                            double circularity, double aspectRatio,
                            double feretMax, double feretMin, double feretAngle) {
        this.id            = id;
        this.area          = area;
        this.perimeter     = perimeter;
        this.circularity   = Math.max(0, Math.min(1, circularity));
        this.equivDiameter = 2.0 * Math.sqrt(area / Math.PI);
        this.aspectRatio   = aspectRatio;
        this.feretMax      = feretMax;
        this.feretMin      = feretMin;
        this.feretAngle    = feretAngle;
    }

    /** Returns a CSV row: id,area,perimeter,circularity,equivDiam,AR,feretMax,feretMin */
    public String toCSVRow() {
        return String.format("%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f",
                id, area, perimeter, circularity, equivDiameter,
                aspectRatio, feretMax, feretMin);
    }

    public static String csvHeader() {
        return "ID,Area,Perimeter,Circularity,EquivDiameter,AspectRatio,FeretMax,FeretMin";
    }

    // ── Batch statistics ──────────────────────────────────────────────────────

    public static double mean(java.util.List<ParticleGeometry> list,
                              java.util.function.ToDoubleFunction<ParticleGeometry> fn) {
        return list.stream().mapToDouble(fn).average().orElse(0);
    }

    public static double stdDev(java.util.List<ParticleGeometry> list,
                                java.util.function.ToDoubleFunction<ParticleGeometry> fn) {
        double m = mean(list, fn);
        return Math.sqrt(list.stream().mapToDouble(p -> {
            double d = fn.applyAsDouble(p) - m; return d * d;
        }).average().orElse(0));
    }

    public static double max(java.util.List<ParticleGeometry> list,
                             java.util.function.ToDoubleFunction<ParticleGeometry> fn) {
        return list.stream().mapToDouble(fn).max().orElse(0);
    }

    public static double min(java.util.List<ParticleGeometry> list,
                             java.util.function.ToDoubleFunction<ParticleGeometry> fn) {
        return list.stream().mapToDouble(fn).min().orElse(0);
    }
}
