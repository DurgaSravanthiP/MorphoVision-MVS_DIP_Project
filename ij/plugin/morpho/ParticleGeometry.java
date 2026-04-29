package ij.plugin.morpho;

import java.util.*;
import java.util.function.ToDoubleFunction;

/**
 * Holds ALL geometric and morphological measurements for one detected particle.
 *
 * Standard metrics (from ImageJ):
 *   Area, Perimeter, Circularity, Equivalent Diameter, Aspect Ratio, Feret Max/Min
 *
 * Advanced metrics (computed here — unique to MorphoVision):
 *   Solidity          = Area / Convex-Hull Area  → 1=fully convex, <1=concave/agglomerate
 *   Roundness         = 4·Area / (π·Major²)       → 1=circle, <1=elongated
 *   Heywood Factor    = Perimeter / (π·EquivDiam) → 1=circle, >1=irregular (used in pharma)
 *   Elongation Index  = (Major−Minor)/(Major+Minor) → 0=circle, 1=line
 *   PA Fractal Dim    = 2·log(P/4) / log(A)       → 1=smooth, →2=rough/fractal boundary
 *   Morpho Class      = auto-label: Spherical / Near-Spherical / Elongated / Irregular / Agglomerate
 */
public class ParticleGeometry {

    // ── Core fields ──────────────────────────────────────────────────────────
    public final int    id;
    public final double area;             // scaled units²
    public final double perimeter;        // scaled units
    public final double circularity;      // 4π·A/P²  → [0,1]
    public final double equivDiameter;    // 2√(A/π)
    public final double aspectRatio;      // major/minor axis
    public final double feretMax;         // max caliper
    public final double feretMin;         // min caliper
    public final double feretAngle;

    // ── Advanced / unique metrics ─────────────────────────────────────────────
    public final double solidity;         // Area / ConvexHullArea
    public final double roundness;        // 4·A / (π·major²)
    public final double heywoodFactor;    // P / (π·equivDiam)  [Heywood Circularity Factor]
    public final double elongationIndex;  // (major−minor)/(major+minor)
    public final double paFractalDim;     // Perimeter-Area fractal dimension
    public final String morphClass;       // automatic classification label

    // ── Constructor ───────────────────────────────────────────────────────────
    public ParticleGeometry(int id,
                            double area, double perimeter, double circularity,
                            double aspectRatio, double feretMax, double feretMin,
                            double feretAngle, double solidity, double roundness,
                            double majorAxis, double minorAxis) {
        this.id           = id;
        this.area         = area;
        this.perimeter    = perimeter;
        this.circularity  = clamp(circularity, 0, 1);
        this.equivDiameter = 2.0 * Math.sqrt(Math.max(area, 1e-12) / Math.PI);
        this.aspectRatio  = aspectRatio;
        this.feretMax     = feretMax;
        this.feretMin     = feretMin;
        this.feretAngle   = feretAngle;
        this.solidity     = clamp(solidity, 0, 1);
        this.roundness    = clamp(roundness, 0, 1);

        // Heywood factor: P / (π × equivDiam)
        this.heywoodFactor = (equivDiameter > 0)
                ? perimeter / (Math.PI * equivDiameter) : 1.0;

        // Elongation index: (major − minor) / (major + minor)
        double sum = majorAxis + minorAxis;
        this.elongationIndex = (sum > 0) ? (majorAxis - minorAxis) / sum : 0.0;

        // Perimeter-Area fractal dimension: 2·log(P/4) / log(A)
        // = 1.0 for perfect circle, approaches 2.0 for highly fractal boundary
        double logP = Math.log(Math.max(perimeter, 1e-6) / 4.0);
        double logA = Math.log(Math.max(area, 1e-12));
        this.paFractalDim = (logA != 0 && logP > 0)
                ? clamp(2.0 * logP / logA, 1.0, 2.0) : 1.0;

        // Morphological classification
        this.morphClass = classify(this.circularity, this.aspectRatio,
                this.solidity, this.elongationIndex);
    }

    // ── Morphological classifier ──────────────────────────────────────────────
    /**
     * Automatically classifies each particle into 5 categories.
     * Rules based on established particle morphology literature.
     *
     * Spherical:      Circ>0.85, AR<1.3, Solid>0.90
     * Near-Spherical: Circ>0.70, AR<1.6, Solid>0.85
     * Elongated:      AR>2.0  OR Elon>0.35
     * Agglomerate:    Solid<0.80 (concave outline = fused particles)
     * Irregular:      everything else
     */
    private static String classify(double circ, double ar, double solid, double elon) {
        if (circ >= 0.85 && ar < 1.3  && solid >= 0.90) return "Spherical";
        if (circ >= 0.70 && ar < 1.60 && solid >= 0.85) return "Near-Spherical";
        if (ar >= 2.0    || elon >= 0.35)                return "Elongated";
        if (solid < 0.80)                                return "Agglomerate";
        return "Irregular";
    }

    // ── CSV ────────────────────────────────────────────────────────────────────
    public static String csvHeader() {
        return "ID,Area,Perimeter,Circularity,EquivDiameter,AspectRatio," +
               "FeretMax,FeretMin,Solidity,Roundness,HeywoodFactor," +
               "ElongationIndex,PA_FractalDim,MorphClass";
    }

    public String toCSVRow() {
        return String.format("%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f," +
                             "%.4f,%.4f,%.4f,%.4f,%.4f,%s",
                id, area, perimeter, circularity, equivDiameter, aspectRatio,
                feretMax, feretMin, solidity, roundness, heywoodFactor,
                elongationIndex, paFractalDim, morphClass);
    }

    // ── Batch statistics ──────────────────────────────────────────────────────
    public static double mean(List<ParticleGeometry> list, ToDoubleFunction<ParticleGeometry> fn) {
        return list.stream().mapToDouble(fn).average().orElse(0);
    }

    public static double stdDev(List<ParticleGeometry> list, ToDoubleFunction<ParticleGeometry> fn) {
        double m = mean(list, fn);
        return Math.sqrt(list.stream().mapToDouble(p -> {
            double d = fn.applyAsDouble(p) - m; return d * d;
        }).average().orElse(0));
    }

    public static double max(List<ParticleGeometry> list, ToDoubleFunction<ParticleGeometry> fn) {
        return list.stream().mapToDouble(fn).max().orElse(0);
    }

    public static double min(List<ParticleGeometry> list, ToDoubleFunction<ParticleGeometry> fn) {
        return list.stream().mapToDouble(fn).min().orElse(0);
    }

    /** Returns count of each morphological class as a Map. */
    public static Map<String, Integer> morphClassCounts(List<ParticleGeometry> list) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String c : new String[]{"Spherical","Near-Spherical","Elongated","Irregular","Agglomerate"})
            counts.put(c, 0);
        for (ParticleGeometry p : list)
            counts.merge(p.morphClass, 1, Integer::sum);
        return counts;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
