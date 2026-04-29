package ij.plugin.morpho;

import ij.*;
import ij.measure.*;
import ij.plugin.filter.*;
import ij.process.*;
import java.awt.image.BufferedImage;
import java.util.*;

/**
 * Runs the 6-step automated particle analysis pipeline on an ImagePlus.
 *
 * Steps:
 *  1 Load (caller provides ImagePlus)
 *  2 Pre-process  (Gaussian blur + background subtraction)
 *  3 Threshold    (Otsu auto or user-supplied value)
 *  4 Detect       (ParticleAnalyzer)
 *  5 Measure      (extract geometry, apply scale)
 *  6 Export       (CSV + HTML report — caller calls GeometryExporter)
 */
public class PipelineEngine {

    public interface ProgressListener {
        void onStep(int step, String message);
        void onDone(List<ParticleGeometry> particles, ImagePlus outlinedImage);
        void onError(String message);
    }

    // ── Parameters ────────────────────────────────────────────────────────────

    private double  gaussianSigma       = 2.0;
    private boolean doBackgroundSub     = true;
    private double  rollingBallRadius   = 50.0;
    private String  thresholdMethod     = "Otsu";  // or "Manual"
    private int     manualThreshold     = 128;
    private double  minParticleSize     = 10.0;    // pixels²
    private double  maxParticleSize     = Double.MAX_VALUE;
    private double  minCircularity      = 0.0;
    private double  nmPerPixel          = 1.0;     // scale factor
    private String  unit                = "px";

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setGaussianSigma(double s)       { gaussianSigma = s; }
    public void setDoBackgroundSub(boolean b)    { doBackgroundSub = b; }
    public void setRollingBallRadius(double r)   { rollingBallRadius = r; }
    public void setThresholdMethod(String m)     { thresholdMethod = m; }
    public void setManualThreshold(int t)        { manualThreshold = t; }
    public void setMinParticleSize(double s)     { minParticleSize = s; }
    public void setMaxParticleSize(double s)     { maxParticleSize = s; }
    public void setMinCircularity(double c)      { minCircularity = c; }
    public void setNmPerPixel(double n)          { nmPerPixel = n; unit = "nm"; }
    public void setUmPerPixel(double u)          { nmPerPixel = u; unit = "µm"; }
    public String getUnit()                      { return unit; }

    // ── Run ───────────────────────────────────────────────────────────────────

    /** Run pipeline synchronously. Call from a background thread. */
    public List<ParticleGeometry> run(ImagePlus imp, ProgressListener cb) {

        try {
            cb.onStep(1, "Converting to 8-bit grayscale…");
            ImagePlus work = imp.duplicate();
            work.setTitle("Pipeline_Work");
            if (work.getType() != ImagePlus.GRAY8) {
                new ImageConverter(work).convertToGray8();
            }
            ImageProcessor ip = work.getProcessor();

            cb.onStep(2, "Pre-processing (blur + background subtraction)…");
            // Gaussian blur
            GaussianBlur gb = new GaussianBlur();
            gb.blurGaussian(ip, gaussianSigma, gaussianSigma, 0.01);

            // Background subtraction (rolling ball)
            if (doBackgroundSub) {
                BackgroundSubtracter bs = new BackgroundSubtracter();
                bs.rollingBallBackground(ip, rollingBallRadius,
                        false, false, false, false, false);
            }

            cb.onStep(3, "Applying threshold (" + thresholdMethod + ")…");
            int[] hist = ip.getHistogram();
            int thresh;
            if ("Manual".equals(thresholdMethod)) {
                thresh = manualThreshold;
            } else {
                thresh = new AutoThresholder()
                        .getThreshold(AutoThresholder.Method.Otsu, hist);
            }
            ip.threshold(thresh);
            work.updateAndDraw();

            // Ensure particles are white (255) on black background
            // If more white pixels than black, invert
            int whites = 0;
            byte[] pixels = (byte[]) ip.getPixels();
            for (byte b : pixels) if ((b & 0xFF) > 0) whites++;
            if (whites > pixels.length / 2) ip.invert();

            cb.onStep(4, "Detecting particles…");
            ResultsTable rt = new ResultsTable();
            int measurements = Measurements.AREA | Measurements.PERIMETER |
                    Measurements.SHAPE_DESCRIPTORS | Measurements.FERET |
                    Measurements.ELLIPSE;
            int options = ParticleAnalyzer.SHOW_OVERLAY_OUTLINES |
                    ParticleAnalyzer.CLEAR_WORKSHEET |
                    ParticleAnalyzer.EXCLUDE_EDGE_PARTICLES;

            ParticleAnalyzer pa = new ParticleAnalyzer(options, measurements,
                    rt, minParticleSize, maxParticleSize, minCircularity, 1.0);
            pa.analyze(work);

            cb.onStep(5, "Extracting measurements and applying scale…");
            List<ParticleGeometry> results = new ArrayList<>();
            double s = nmPerPixel;
            for (int i = 0; i < rt.getCounter(); i++) {
                double area  = getVal(rt, "Area",      i) * s * s;
                double perim = getVal(rt, "Perim.",    i) * s;
                double circ  = getVal(rt, "Circ.",     i);
                double ar    = getVal(rt, "AR",         i);
                double fMax  = getVal(rt, "Feret",     i) * s;
                double fMin  = getVal(rt, "MinFeret",  i) * s;
                double fAng  = getVal(rt, "FeretAngle",i);
                results.add(new ParticleGeometry(i + 1, area, perim, circ, ar, fMax, fMin, fAng));
            }

            // Get outlined image (work now has overlay)
            ImagePlus outlined = work;

            cb.onStep(6, "Pipeline complete. " + results.size() + " particles measured.");
            cb.onDone(results, outlined);
            return results;

        } catch (Exception ex) {
            cb.onError("Pipeline error: " + ex.getMessage());
            return Collections.emptyList();
        }
    }

    private double getVal(ResultsTable rt, String col, int row) {
        try { return rt.getValue(col, row); } catch (Exception e) { return 0; }
    }
}
