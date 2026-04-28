package ij.gui;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import javax.imageio.ImageIO;

/**
 * SplashPanel – renders the MorphoVision splash view:
 *   background.png  → tiled/stretched across the full panel
 *   circles.png     → drawn at the left and right edges (mirrored)
 *   logo.png        → drawn centered on top
 *
 *  All three images are loaded from the project root directory
 *  (one level above the ij/ package directory).
 */
public class SplashPanel extends Canvas {

    private static final int PANEL_HEIGHT = 200;

    private BufferedImage bgImg;
    private BufferedImage circlesImg;
    private BufferedImage logoImg;

    public SplashPanel() {
        setPreferredSize(new Dimension(640, PANEL_HEIGHT));
        loadImages();
    }

    private void loadImages() {
        // Resolve paths relative to the running jar/class location.
        // We walk up from the ij/gui package to the project root.
        String root = getRootDir();
        bgImg      = loadImage(root + "background.png");
        circlesImg = loadImage(root + "circles.png");
        logoImg    = loadImage(root + "logo.png");
    }

    /** Returns the project root directory path (with trailing separator). */
    private String getRootDir() {
        // Try to locate via the class-file location
        try {
            String path = SplashPanel.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()
                    .getPath();
            File f = new File(path);
            // Walk up until we find background.png or hit the filesystem root
            File dir = f.isDirectory() ? f : f.getParentFile();
            for (int i = 0; i < 6; i++) {
                if (new File(dir, "background.png").exists())
                    return dir.getAbsolutePath() + File.separator;
                if (dir.getParentFile() == null) break;
                dir = dir.getParentFile();
            }
        } catch (Exception ignored) {}

        // Fallback: current working directory
        return System.getProperty("user.dir") + File.separator;
    }

    private BufferedImage loadImage(String path) {
        try {
            File f = new File(path);
            if (f.exists())
                return ImageIO.read(f);
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // ── 1. Background ────────────────────────────────────────────────
        if (bgImg != null) {
            g2.drawImage(bgImg, 0, 0, w, h, null);
        } else {
            // Fallback gradient
            g2.setPaint(new GradientPaint(0, 0, new Color(200, 120, 120),
                    w, h, new Color(240, 200, 200)));
            g2.fillRect(0, 0, w, h);
        }

        // ── 2. Circles – left & right at 30 % opacity ────────────────────
        if (circlesImg != null) {
            int circH = (int)(h * 0.55);
            int origW  = circlesImg.getWidth();
            int origH  = circlesImg.getHeight();
            int circW  = (int)((double)origW / origH * circH);
            int leftX  = -circW / 4;
            int leftY  = h - circH + (int)(circH * 0.1);

            // Apply 30 % alpha for both circle draws
            Composite savedComposite = g2.getComposite();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.30f));

            // Left side
            g2.drawImage(circlesImg, leftX, leftY, circW, circH, null);

            // Right side – mirrored
            Graphics2D g2r = (Graphics2D) g2.create();
            g2r.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2r.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.30f));
            g2r.translate(w + circW / 4, leftY);
            g2r.scale(-1, 1);
            g2r.drawImage(circlesImg, 0, 0, circW, circH, null);
            g2r.dispose();

            // Restore full opacity for subsequent layers
            g2.setComposite(savedComposite);
        }

        // ── 3. Logo – centred ─────────────────────────────────────────────
        if (logoImg != null) {
            // Target height is about 65 % of the panel height
            int logoH = (int)(h * 0.65);
            int origW  = logoImg.getWidth();
            int origH  = logoImg.getHeight();
            int logoW  = (int)((double)origW / origH * logoH);

            int lx = (w - logoW) / 2;
            int ly = (h - logoH) / 2;
            g2.drawImage(logoImg, lx, ly, logoW, logoH, null);
        } else {
            // Fallback text
            g2.setColor(new Color(0xBB, 0x37, 0x37));
            g2.setFont(new Font("SansSerif", Font.BOLD, 80));
            FontMetrics fm = g2.getFontMetrics();
            String txt = "MVS";
            int tx = (w - fm.stringWidth(txt)) / 2;
            int ty = (h + fm.getAscent()) / 2 - fm.getDescent();
            g2.drawString(txt, tx, ty);
        }
    }

    @Override
    public void update(Graphics g) {
        // Avoid flicker by not clearing before paint
        paint(g);
    }
}
