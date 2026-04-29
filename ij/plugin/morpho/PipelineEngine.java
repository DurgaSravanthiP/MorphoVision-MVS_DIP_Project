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
    private double  gaussianSigma     = 1.0;     // lower = less blurring
    private boolean doBackgroundSub   = false;   // OFF by default for bright-bg images
    private double  rollingBallRadius = 50.0;
    private String  thresholdMethod   = "Otsu";
    private int     manualThreshold   = 128;
    private double  minParticleSize   = 1.0;     // px² – catch everything
    private double  maxParticleSize   = Double.MAX_VALUE;
    private double  minCircularity    = 0.0;
    private double  nmPerPixel        = 1.0;
    private String  unit              = "px";

    // ── Setters ────────────────────────────────────────────────────────────
    public void setGaussianSigma(double s)     { gaussianSigma = s; }
    public void setDoBackgroundSub(boolean b)  { doBackgroundSub = b; }
    public void setRollingBallRadius(double r) { rollingBallRadius = r; }
    public void setThresholdMethod(String m)   { thresholdMethod = m; }
    public void setManualThreshold(int t)      { manualThreshold = t; }
    public void setMinParticleSize(double s)   { minParticleSize = s; }
    public void setMaxParticleSize(double s)   { maxParticleSize = s; }
    public void setMinCircularity(double c)    { minCircularity = c; }
    public void setNmPerPixel(double n)        { nmPerPixel = n; unit = "nm"; }
    public void setUmPerPixel(double u)        { nmPerPixel = u; unit = "µm"; }
    public String getUnit()                    { return unit; }

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

            // ── Step 2: Pre-process ────────────────────────────────────────
            cb.onStep(2, "Pre-processing (Gaussian blur" +
                    (doBackgroundSub ? " + background subtraction" : "") + ")…");

            // Light Gaussian to reduce noise
            if (gaussianSigma > 0) {
                new GaussianBlur().blurGaussian(ip, gaussianSigma, gaussianSigma, 0.01);
            }
            // Background subtraction only if requested
            if (doBackgroundSub) {
                new BackgroundSubtracter().rollingBallBackground(
                        ip, rollingBallRadius, false, false, false, false, false);
            }

            // ── Step 3: Threshold ──────────────────────────────────────────
            cb.onStep(3, "Applying threshold (" + thresholdMethod + ")…");

            // Detect whether background is bright or dark by sampling image edges
            boolean brightBackground = detectBrightBackground(ip);
            cb.onStep(3, "Threshold: background detected as " +
                    (brightBackground ? "BRIGHT (brightfield)" : "DARK (fluorescence)") + "…");

            // For bright backgrounds, invert so particles become white
            if (brightBackground) {
                ip.invert();
            }

            // Compute threshold
            int thresh;
            if ("Manual".equalsIgnoreCase(thresholdMethod)) {
                thresh = manualThreshold;
            } else {
                int[] hist = ip.getHistogram();
                thresh = new AutoThresholder()
                        .getThreshold(AutoThresholder.Method.Otsu, hist);
            }
            ip.threshold(thresh);
            work.updateAndDraw();

            // ── Step 4: Detect particles (with watershed) ──────────────────
            cb.onStep(4, "Applying watershed separation for touching particles…");
            // Watershed splits touching particles – critical for accuracy
            IJ.run(work, "Watershed", "");

            cb.onStep(4, "Detecting particles with ParticleAnalyzer…");
            ResultsTable rt = new ResultsTable();
            int measurements = Measurements.AREA | Measurements.PERIMETER |
                    Measurements.SHAPE_DESCRIPTORS | Measurements.FERET |
                    Measurements.ELLIPSE | Measurements.MEAN;

            // SHOW_OVERLAY_OUTLINES adds coloured outlines to the image
            // No EXCLUDE_EDGE_PARTICLES so nothing is missed
            int options = ParticleAnalyzer.SHOW_OVERLAY_OUTLINES |
                          ParticleAnalyzer.CLEAR_WORKSHEET;

            ParticleAnalyzer pa = new ParticleAnalyzer(options, measurements,
                    rt, minParticleSize, maxParticleSize, minCircularity, 1.0);
            pa.analyze(work);

            // ── Step 5: Extract measurements ──────────────────────────────
            cb.onStep(5, "Extracting geometry and applying scale (1 px = " +
                    nmPerPixel + " " + unit + ")…");
            List<ParticleGeometry> results = new ArrayList<>();
            double s = nmPerPixel;
            for (int i = 0; i < rt.getCounter(); i++) {
                double area  = safeGet(rt, "Area",       i) * s * s;
                double perim = safeGet(rt, "Perim.",     i) * s;
                double circ  = safeGet(rt, "Circ.",      i);
                double ar    = safeGet(rt, "AR",          i);
                double fMax  = safeGet(rt, "Feret",      i) * s;
                double fMin  = safeGet(rt, "MinFeret",   i) * s;
                double fAng  = safeGet(rt, "FeretAngle", i);
                results.add(new ParticleGeometry(
                        i + 1, area, perim, circ, ar, fMax, fMin, fAng));
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
