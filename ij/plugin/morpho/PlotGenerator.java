package ij.plugin.morpho;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Generates publication-quality chart images using Java2D only (no libraries).
 */
public class PlotGenerator {

    private static final Color BG        = Color.WHITE;
    private static final Color GRID      = new Color(220, 220, 220);
    private static final Color BAR_FILL  = new Color(45, 90, 160, 200);
    private static final Color BAR_LINE  = new Color(30, 60, 120);
    private static final Color AXIS      = new Color(60, 60, 60);
    private static final Color TITLE_COL = new Color(30, 30, 30);
    private static final Font  TITLE_F   = new Font("SansSerif", Font.BOLD, 13);
    private static final Font  AXIS_F    = new Font("SansSerif", Font.PLAIN, 10);
    private static final Font  TICK_F    = new Font("SansSerif", Font.PLAIN, 9);

    private static final int W = 480, H = 320;
    private static final int ML = 60, MR = 20, MT = 40, MB = 50; // margins

    // ── Public API ────────────────────────────────────────────────────────────

    public static BufferedImage areaHistogram(List<ParticleGeometry> p, String unit) {
        double[] data = p.stream().mapToDouble(g -> g.area).toArray();
        return histogram(data, 20, "Area Distribution",
                "Area (" + unit + "²)", "Count");
    }

    public static BufferedImage circularityHistogram(List<ParticleGeometry> p) {
        double[] data = p.stream().mapToDouble(g -> g.circularity).toArray();
        return histogram(data, 20, "Circularity Distribution",
                "Circularity (0=irregular, 1=circle)", "Count");
    }

    public static BufferedImage aspectRatioHistogram(List<ParticleGeometry> p) {
        double[] data = p.stream().mapToDouble(g -> g.aspectRatio).toArray();
        return histogram(data, 20, "Aspect Ratio Distribution",
                "Aspect Ratio (major/minor)", "Count");
    }

    public static BufferedImage scatterPlot(List<ParticleGeometry> p, String unit) {
        double[] xs = p.stream().mapToDouble(g -> g.area).toArray();
        double[] ys = p.stream().mapToDouble(g -> g.circularity).toArray();
        double[] cs = ys; // colour by circularity
        return scatter(xs, ys, cs,
                "Area vs Circularity",
                "Area (" + unit + "²)", "Circularity");
    }

    // ── Histogram ────────────────────────────────────────────────────────────

    private static BufferedImage histogram(double[] data, int bins,
                                           String title, String xLabel, String yLabel) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = setup(img, title);

        if (data.length == 0) { g.dispose(); return img; }

        double lo = data[0], hi = data[0];
        for (double v : data) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
        if (hi == lo) hi = lo + 1;

        int[] counts = new int[bins];
        double step = (hi - lo) / bins;
        for (double v : data) {
            int b = (int) ((v - lo) / step);
            if (b == bins) b = bins - 1;
            counts[b]++;
        }
        int maxCount = 0;
        for (int c : counts) maxCount = Math.max(maxCount, c);

        int pw = W - ML - MR, ph = H - MT - MB;
        drawGrid(g, 5, maxCount, pw, ph);

        // Bars
        int bw = pw / bins;
        for (int i = 0; i < bins; i++) {
            int bh = (int) ((double) counts[i] / maxCount * ph);
            int x = ML + i * bw, y = MT + ph - bh;
            g.setColor(BAR_FILL);
            g.fillRect(x + 1, y, bw - 2, bh);
            g.setColor(BAR_LINE);
            g.drawRect(x + 1, y, bw - 2, bh);
        }

        // X axis ticks
        g.setFont(TICK_F);
        g.setColor(AXIS);
        for (int i = 0; i <= 5; i++) {
            double val = lo + (hi - lo) * i / 5.0;
            int x = ML + (int) ((val - lo) / (hi - lo) * pw);
            g.drawLine(x, MT + ph, x, MT + ph + 4);
            String lbl = String.format("%.2g", val);
            g.drawString(lbl, x - g.getFontMetrics().stringWidth(lbl) / 2, MT + ph + 15);
        }
        // Y axis ticks
        for (int i = 0; i <= 5; i++) {
            int val = maxCount * i / 5;
            int y = MT + ph - (int) ((double) val / maxCount * ph);
            g.drawLine(ML - 4, y, ML, y);
            String lbl = String.valueOf(val);
            g.drawString(lbl, ML - 5 - g.getFontMetrics().stringWidth(lbl), y + 4);
        }

        drawAxes(g, xLabel, yLabel, pw, ph);
        g.dispose();
        return img;
    }

    // ── Scatter plot ──────────────────────────────────────────────────────────

    private static BufferedImage scatter(double[] xs, double[] ys, double[] cs,
                                         String title, String xLabel, String yLabel) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = setup(img, title);

        if (xs.length == 0) { g.dispose(); return img; }

        double xlo = xs[0], xhi = xs[0], ylo = ys[0], yhi = ys[0];
        for (int i = 0; i < xs.length; i++) {
            xlo = Math.min(xlo, xs[i]); xhi = Math.max(xhi, xs[i]);
            ylo = Math.min(ylo, ys[i]); yhi = Math.max(yhi, ys[i]);
        }
        if (xhi == xlo) xhi = xlo + 1;
        if (yhi == ylo) yhi = ylo + 1;

        int pw = W - ML - MR, ph = H - MT - MB;
        drawGrid(g, 5, 1, pw, ph);

        // Points coloured by circularity (blue=low, red=high)
        for (int i = 0; i < xs.length; i++) {
            int px = ML + (int) ((xs[i] - xlo) / (xhi - xlo) * pw);
            int py = MT + ph - (int) ((ys[i] - ylo) / (yhi - ylo) * ph);
            float norm = (float) ((cs[i] - ylo) / (yhi - ylo));
            g.setColor(new Color(1f - norm, 0.3f, norm, 0.7f));
            g.fillOval(px - 3, py - 3, 6, 6);
        }

        // Tick labels
        g.setFont(TICK_F);
        g.setColor(AXIS);
        for (int i = 0; i <= 5; i++) {
            double xv = xlo + (xhi - xlo) * i / 5.0;
            double yv = ylo + (yhi - ylo) * i / 5.0;
            int x = ML + (int) ((xv - xlo) / (xhi - xlo) * pw);
            int y = MT + ph - (int) ((yv - ylo) / (yhi - ylo) * ph);
            g.drawLine(x, MT + ph, x, MT + ph + 4);
            g.drawLine(ML - 4, y, ML, y);
            String xl = String.format("%.2g", xv);
            String yl = String.format("%.2f", yv);
            g.drawString(xl, x - g.getFontMetrics().stringWidth(xl) / 2, MT + ph + 15);
            g.drawString(yl, ML - 5 - g.getFontMetrics().stringWidth(yl), y + 4);
        }

        drawAxes(g, xLabel, yLabel, pw, ph);
        g.dispose();
        return img;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Graphics2D setup(BufferedImage img, String title) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(BG); g.fillRect(0, 0, W, H);
        // Border
        g.setColor(new Color(200, 200, 200));
        g.drawRect(0, 0, W - 1, H - 1);
        // Title
        g.setFont(TITLE_F); g.setColor(TITLE_COL);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, (W - fm.stringWidth(title)) / 2, MT - 12);
        return g;
    }

    private static void drawGrid(Graphics2D g, int gridLines, double maxY, int pw, int ph) {
        g.setColor(GRID);
        g.setStroke(new BasicStroke(0.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[]{3f}, 0f));
        for (int i = 1; i <= gridLines; i++) {
            int x = ML + pw * i / gridLines;
            int y = MT + ph - ph * i / gridLines;
            g.drawLine(x, MT, x, MT + ph);
            g.drawLine(ML, y, ML + pw, y);
        }
        g.setStroke(new BasicStroke(1f));
    }

    private static void drawAxes(Graphics2D g, String xLabel, String yLabel, int pw, int ph) {
        g.setColor(AXIS);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(ML, MT, ML, MT + ph);
        g.drawLine(ML, MT + ph, ML + pw, MT + ph);
        g.setStroke(new BasicStroke(1f));

        // X label
        g.setFont(AXIS_F);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(xLabel, ML + (pw - fm.stringWidth(xLabel)) / 2, H - 8);

        // Y label (rotated)
        Graphics2D g2 = (Graphics2D) g.create();
        g2.rotate(-Math.PI / 2, ML - 40, MT + ph / 2);
        g2.drawString(yLabel, ML - 40 - fm.stringWidth(yLabel) / 2, MT + ph / 2);
        g2.dispose();
    }
}
