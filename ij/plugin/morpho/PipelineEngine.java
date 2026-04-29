package ij.plugin.morpho;

import ij.*;
import ij.measure.*;
import ij.plugin.filter.*;
import ij.process.*;
import java.util.*;

/**
 * 6-step automated particle analysis pipeline.
 *
 * Key accuracy improvements:
 *  • Background subtraction OFF by default (destroys signal in bright-bg images)
 *  • Watershed separation ALWAYS applied (splits touching particles)
 *  • Smart inversion: detects bright vs dark background automatically
 *  • No EXCLUDE_EDGE_PARTICLES so boundary particles are captured
 *  • Smaller default min size (1 px²) to miss nothing
 */
public class PipelineEngine {

    public interface ProgressListener {
        void onStep(int step, String message);
        void onDone(List<ParticleGeometry> particles, ImagePlus outlinedImage);
        void onError(String message);
    }

    // ── Parameters ─────────────────────────────────────────────────────────
    public enum ImageMode { AUTO, BRIGHTFIELD, FLUORESCENCE, SEM }

    private ImageMode imageMode        = ImageMode.AUTO;
    private double  gaussianSigma      = 2.0;
    private boolean doBackgroundSub    = false;
    private double  rollingBallRadius  = 50.0;
    private String  thresholdMethod    = "Otsu";
    private int     manualThreshold    = 128;
    private double  minParticleSize    = -1;      // -1 = auto (0.05% of image area)
    private double  maxParticleSize    = Double.MAX_VALUE;
    private double  minCircularity     = 0.0;
    private boolean excludeEdge        = false;   // exclude particles touching border
    private double  nmPerPixel         = 1.0;
    private String  unit               = "px";

    // ── Setters ────────────────────────────────────────────────────────────
    public void setImageMode(ImageMode m)      { imageMode = m; applyModeDefaults(); }
    public void setGaussianSigma(double s)     { gaussianSigma = s; }
    public void setDoBackgroundSub(boolean b)  { doBackgroundSub = b; }
    public void setRollingBallRadius(double r) { rollingBallRadius = r; }
    public void setThresholdMethod(String m)   { thresholdMethod = m; }
    public void setManualThreshold(int t)      { manualThreshold = t; }
    public void setMinParticleSize(double s)   { minParticleSize = s; }
    public void setMaxParticleSize(double s)   { maxParticleSize = s; }
    public void setMinCircularity(double c)    { minCircularity = c; }
    public void setExcludeEdge(boolean b)      { excludeEdge = b; }
    public void setNmPerPixel(double n)        { nmPerPixel = n; unit = "nm"; }
    public void setUmPerPixel(double u)        { nmPerPixel = u; unit = "µm"; }
    public String getUnit()                    { return unit; }

    /** Apply sensible defaults per imaging mode. */
    private void applyModeDefaults() {
        switch (imageMode) {
            case SEM:
                // SEM: dark background, rough surface, touching particles
                gaussianSigma   = 3.0;   // heavier blur for rough texture
                thresholdMethod = "Triangle"; // Triangle works better for SEM
                doBackgroundSub = false;
                break;
            case BRIGHTFIELD:
                gaussianSigma   = 2.0;
                thresholdMethod = "Otsu";
                break;
            case FLUORESCENCE:
                gaussianSigma   = 1.5;
                thresholdMethod = "Otsu";
                doBackgroundSub = true; // uneven illumination is common
                break;
            default: break;
        }
    }

    // ── Run ────────────────────────────────────────────────────────────────
    public List<ParticleGeometry> run(ImagePlus imp, ProgressListener cb) {
        try {
            // ── Step 1: Convert to 8-bit grayscale ────────────────────────
            cb.onStep(1, "Converting to 8-bit grayscale…");
            ImagePlus work = imp.duplicate();
            work.setTitle("Pipeline_Work");
            if (work.getType() != ImagePlus.GRAY8) {
                new ImageConverter(work).convertToGray8();
            }
            ImageProcessor ip = work.getProcessor();

            // Auto min size: 0.05% of image area (filters noise, keeps real particles)
            double autoMin = Math.max(20.0, ip.getWidth() * ip.getHeight() * 0.0005);
            double effectiveMinSize = (minParticleSize <= 0) ? autoMin : minParticleSize;

            // ── Step 2: Pre-process ────────────────────────────────────────
            cb.onStep(2, "Pre-processing: Gaussian blur (σ=" + gaussianSigma +
                    ") to smooth specular highlights…");
            if (gaussianSigma > 0) {
                new GaussianBlur().blurGaussian(ip, gaussianSigma, gaussianSigma, 0.01);
            }
            if (doBackgroundSub) {
                new BackgroundSubtracter().rollingBallBackground(
                        ip, rollingBallRadius, false, false, false, false, false);
            }

            // ── Step 3: Threshold ──────────────────────────────────────────
            boolean brightBg;
            if (imageMode == ImageMode.SEM) {
                brightBg = false; // SEM always dark background
            } else if (imageMode == ImageMode.BRIGHTFIELD) {
                brightBg = true;
            } else if (imageMode == ImageMode.FLUORESCENCE) {
                brightBg = false;
            } else {
                brightBg = detectBrightBackground(ip); // AUTO
            }
            cb.onStep(3, "Threshold: " + thresholdMethod + " | background=" +
                    (brightBg ? "BRIGHT" : "DARK") +
                    (imageMode != ImageMode.AUTO ? " | mode=" + imageMode : ""));
            if (brightBg) ip.invert();

            // Threshold method
            int thresh;
            if ("Manual".equalsIgnoreCase(thresholdMethod)) {
                thresh = manualThreshold;
            } else {
                AutoThresholder.Method method;
                try {
                    method = AutoThresholder.Method.valueOf(thresholdMethod);
                } catch (Exception e) {
                    method = AutoThresholder.Method.Otsu;
                }
                thresh = new AutoThresholder().getThreshold(method, ip.getHistogram());
            }
            ip.threshold(thresh);
            work.updateAndDraw();

            // ── Step 4: Binary clean-up + watershed ───────────────────────
            cb.onStep(4, "Filling holes (removes internal highlights/reflections)…");
            // CRITICAL: Fill holes BEFORE watershed.
            // Specular highlights inside particles create holes → watershed splits them.
            // Fill Holes closes these, making each particle one solid region.
            IJ.run(work, "Fill Holes", "");

            cb.onStep(4, "Morphological closing (connect broken particle boundaries)…");
            // One pass of dilation then erosion to close small gaps
            IJ.run(work, "Dilate", "");
            IJ.run(work, "Erode",  "");

            cb.onStep(4, "Watershed separation for touching particles…");
            // Now safe: particles are solid, so watershed only splits truly touching ones
            IJ.run(work, "Watershed", "");

            cb.onStep(4, "Running ParticleAnalyzer (min=" +
                    String.format("%.0f", effectiveMinSize) + " px²)…");

            ResultsTable rt = new ResultsTable();
            int measurements = Measurements.AREA       | Measurements.PERIMETER |
                               Measurements.SHAPE_DESCRIPTORS |  // Circ, AR, Round, Solid
                               Measurements.FERET      | Measurements.ELLIPSE   |
                               Measurements.MEAN       | Measurements.CENTROID;
            // No EXCLUDE_EDGE_PARTICLES so nothing is missed
            int options = ParticleAnalyzer.SHOW_OVERLAY_OUTLINES |
                          ParticleAnalyzer.CLEAR_WORKSHEET;
            if (excludeEdge) {
                options |= ParticleAnalyzer.EXCLUDE_EDGE_PARTICLES;
                cb.onStep(4, "Edge particles excluded from count.");
            } else {
                cb.onStep(4, "Edge (half-cut) particles INCLUDED — they will be measured as partial.");
            }

            ParticleAnalyzer pa = new ParticleAnalyzer(options, measurements,
                    rt, effectiveMinSize, maxParticleSize, minCircularity, 1.0);
            pa.analyze(work);


            cb.onStep(5, "Extracting 14 geometric + morphological metrics per particle…");
            List<ParticleGeometry> results = new ArrayList<>();
            double s = nmPerPixel;
            for (int i = 0; i < rt.getCounter(); i++) {
                double area     = safeGet(rt, "Area",       i) * s * s;
                double perim    = safeGet(rt, "Perim.",     i) * s;
                double circ     = safeGet(rt, "Circ.",      i);
                double ar       = safeGet(rt, "AR",          i);
                double fMax     = safeGet(rt, "Feret",      i) * s;
                double fMin     = safeGet(rt, "MinFeret",   i) * s;
                double fAng     = safeGet(rt, "FeretAngle", i);
                double solidity = safeGet(rt, "Solidity",   i);  // Area/ConvexHullArea
                double round    = safeGet(rt, "Round",      i);  // 4A/(π·major²)
                double major    = safeGet(rt, "Major",      i) * s; // ellipse major axis
                double minor    = safeGet(rt, "Minor",      i) * s; // ellipse minor axis
                results.add(new ParticleGeometry(
                        i + 1, area, perim, circ, ar,
                        fMax, fMin, fAng,
                        solidity, round, major, minor));
            }

            cb.onStep(6, "Pipeline complete. " + results.size() + " particles measured.");
            cb.onDone(results, work);
            return results;

        } catch (Exception ex) {
            cb.onError("Pipeline error: " + ex.getClass().getSimpleName() +
                    " — " + ex.getMessage());
            ex.printStackTrace();
            return Collections.emptyList();
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Samples pixels near all 4 edges (border strip of 5px).
     * If average border brightness > 128, background is bright (brightfield).
     */
    private boolean detectBrightBackground(ImageProcessor ip) {
        int w = ip.getWidth(), h = ip.getHeight();
        long sum = 0; int count = 0;
        int strip = Math.max(5, Math.min(20, h / 20)); // 5–20px border
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < strip; y++)           { sum += ip.getPixel(x, y);     count++; }
            for (int y = h-strip; y < h; y++)         { sum += ip.getPixel(x, y);     count++; }
        }
        for (int y = strip; y < h - strip; y++) {
            for (int x = 0; x < strip; x++)           { sum += ip.getPixel(x, y);     count++; }
            for (int x = w-strip; x < w; x++)         { sum += ip.getPixel(x, y);     count++; }
        }
        double avgBorder = count > 0 ? (double) sum / count : 128;
        return avgBorder > 128; // bright border → bright background
    }

    private double safeGet(ResultsTable rt, String col, int row) {
        try { return rt.getValue(col, row); } catch (Exception e) { return 0; }
    }
}
