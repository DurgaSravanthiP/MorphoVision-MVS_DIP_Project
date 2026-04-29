package ij.gui;

import ij.*;
import ij.text.TextWindow;
import javax.swing.*;
import javax.swing.plaf.basic.BasicInternalFrameUI;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

/**
 * MorphoDesktop – single MDI workspace.
 * • All ImageWindows / TextWindows appear as JInternalFrames here.
 * • Minimised frames dock to a task-bar at the bottom of the desktop.
 * • Buttons (─ □ ✕) are always visible via Metal L&F + UIManager colours.
 */
public class MorphoDesktop extends JDesktopPane {

    private static MorphoDesktop instance;
    private final Map<Frame, JInternalFrame> frameMap = new LinkedHashMap<>();

    // Splash images shown when no images are open
    private java.awt.image.BufferedImage bgImg, circlesImg, logoImg;

    // ── Singleton ─────────────────────────────────────────────────────────────
    public static MorphoDesktop getInstance()              { return instance; }
    public static void setInstance(MorphoDesktop d)        { instance = d; }

    // ── Constructor ───────────────────────────────────────────────────────────
    public MorphoDesktop() {
        instance = this;
        setBackground(new Color(28, 28, 32));
        setDragMode(JDesktopPane.OUTLINE_DRAG_MODE);

        // Style InternalFrame title bars so buttons are always visible
        styleInternalFrameUI();

        // Custom desktop manager that docks minimised icons at the bottom
        setDesktopManager(new BottomDockManager(this));

        // Scroll-wheel on empty desktop → zoom active image
        addMouseWheelListener(e -> {
            JInternalFrame f = getSelectedFrame();
            if (f instanceof MDIImageFrame) {
                ImageCanvas ic = ((MDIImageFrame) f).getImageCanvas();
                if (ic != null) {
                    int cx = ic.getWidth() / 2, cy = ic.getHeight() / 2;
                    if (e.getWheelRotation() < 0) ic.zoomIn(cx, cy);
                    else                           ic.zoomOut(cx, cy);
                }
            }
        });

        // Load splash images for the empty-desktop background
        loadSplash();
    }

    // ── Splash background ──────────────────────────────────────────────────────
    private void loadSplash() {
        String root = getSplashRoot();
        bgImg      = loadImg(root + "background.png");
        circlesImg = loadImg(root + "circles.png");
        logoImg    = loadImg(root + "logo.png");
    }

    private String getSplashRoot() {
        try {
            String path = getClass().getProtectionDomain().getCodeSource()
                    .getLocation().toURI().getPath();
            java.io.File f = new java.io.File(path);
            java.io.File dir = f.isDirectory() ? f : f.getParentFile();
            for (int i = 0; i < 6; i++) {
                if (new java.io.File(dir, "background.png").exists())
                    return dir.getAbsolutePath() + java.io.File.separator;
                if (dir.getParentFile() == null) break;
                dir = dir.getParentFile();
            }
        } catch (Exception ignored) {}
        return System.getProperty("user.dir") + java.io.File.separator;
    }

    private java.awt.image.BufferedImage loadImg(String path) {
        try {
            java.io.File f = new java.io.File(path);
            if (f.exists()) return javax.imageio.ImageIO.read(f);
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int w = getWidth(), h = getHeight();
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        // 1. Background
        if (bgImg != null) {
            g2.drawImage(bgImg, 0, 0, w, h, null);
        }

        // 2. Circles at 30% opacity (left + mirrored right)
        if (circlesImg != null) {
            int circH = (int)(h * 0.55);
            int circW = (int)((double) circlesImg.getWidth() / circlesImg.getHeight() * circH);
            int leftX = -circW / 4;
            int leftY = h - circH + (int)(circH * 0.1);
            Composite saved = g2.getComposite();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.30f));
            g2.drawImage(circlesImg, leftX, leftY, circW, circH, null);
            Graphics2D g2r = (Graphics2D) g2.create();
            g2r.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.30f));
            g2r.translate(w + circW / 4, leftY);
            g2r.scale(-1, 1);
            g2r.drawImage(circlesImg, 0, 0, circW, circH, null);
            g2r.dispose();
            g2.setComposite(saved);
        }

        // 3. Logo centred
        if (logoImg != null) {
            int logoH = (int)(h * 0.40);
            int logoW = (int)((double) logoImg.getWidth() / logoImg.getHeight() * logoH);
            g2.drawImage(logoImg, (w - logoW) / 2, (h - logoH) / 2, logoW, logoH, null);
        } else {
            g2.setColor(new Color(0xBB, 0x37, 0x37));
            g2.setFont(new Font("SansSerif", Font.BOLD, 80));
            FontMetrics fm = g2.getFontMetrics();
            String txt = "MVS";
            g2.drawString(txt, (w - fm.stringWidth(txt)) / 2,
                    (h + fm.getAscent()) / 2 - fm.getDescent());
        }
    }

    // ── UIManager styling ──────────────────────────────────────────────────────
    private static void styleInternalFrameUI() {
        // Title bar colours (active)
        UIManager.put("InternalFrame.activeTitleBackground",  new Color(45, 90, 160));
        UIManager.put("InternalFrame.activeTitleForeground",  Color.WHITE);
        // Title bar colours (inactive)
        UIManager.put("InternalFrame.inactiveTitleBackground", new Color(70, 70, 80));
        UIManager.put("InternalFrame.inactiveTitleForeground", new Color(200, 200, 200));
        // Font
        UIManager.put("InternalFrame.titleFont",
                new Font("SansSerif", Font.BOLD, 12));
        // Button colours – ensures they are visible on the dark title bar
        UIManager.put("InternalFrame.closeIcon",    makeIcon("✕"));
        UIManager.put("InternalFrame.minimizeIcon", makeIcon("─"));
        UIManager.put("InternalFrame.maximizeIcon", makeIcon("□"));
        UIManager.put("InternalFrame.restoreUpIcon",makeIcon("❐"));
        // Border
        UIManager.put("InternalFrame.border",
                BorderFactory.createLineBorder(new Color(60, 60, 80), 1));
        // Icon (minimised) title bar
        UIManager.put("InternalFrameTitlePane.closeButtonToolTip",    "Close");
        UIManager.put("InternalFrameTitlePane.minimizeButtonToolTip", "Minimize");
        UIManager.put("InternalFrameTitlePane.maximizeButtonToolTip", "Maximize");
    }

    /** Creates a simple text-based icon for the title-bar buttons. */
    private static Icon makeIcon(String symbol) {
        return new Icon() {
            @Override public int getIconWidth()  { return 16; }
            @Override public int getIconHeight() { return 16; }
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 11));
                FontMetrics fm = g2.getFontMetrics();
                int tx = x + (16 - fm.stringWidth(symbol)) / 2;
                int ty = y + (16 + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(symbol, tx, ty);
                g2.dispose();
            }
        };
    }

    // ── Embed ImageWindow ──────────────────────────────────────────────────────
    public MDIImageFrame embedImageWindow(ImageWindow win) {
        if (frameMap.containsKey(win)) {
            JInternalFrame ex = frameMap.get(win);
            try { ex.setIcon(false); ex.setSelected(true); } catch (Exception ignored) {}
            return (MDIImageFrame) ex;
        }

        MDIImageFrame mif = new MDIImageFrame(win);
        frameMap.put(win, mif);

        int offset = (frameMap.size() - 1) % 8 * 24;
        // Open at ~60 % of desktop – not covering everything
        int w = (int)(getWidth()  * 0.60);
        int h = (int)(getHeight() * 0.60) - 40;
        if (w < 320) w = 320;
        if (h < 240) h = 240;
        mif.setBounds(offset, offset, w, h);

        add(mif);
        mif.setVisible(true);
        try { mif.setSelected(true); } catch (Exception ignored) {}

        win.setVisible(false);
        repaint();
        return mif;
    }

    // ── Embed TextWindow (Results / Log) ───────────────────────────────────────
    public void embedTextWindow(Frame win) {
        if (frameMap.containsKey(win)) {
            JInternalFrame ex = frameMap.get(win);
            ex.toFront();
            try { ex.setIcon(false); ex.setSelected(true); } catch (Exception ignored) {}
            return;
        }
        win.setVisible(false);

        JInternalFrame jif = new JInternalFrame(win.getTitle(), true, true, true, true);
        jif.setLayout(new BorderLayout());
        Component[] comps = win.getComponents();
        for (Component c : comps) { win.remove(c); jif.getContentPane().add(c); }
        frameMap.put(win, jif);

        int dw = Math.max(400, getWidth() / 3);
        int dh = Math.max(220, getHeight() / 3);
        jif.setBounds(getWidth() - dw - 10, getHeight() - dh - 60, dw, dh);

        add(jif);
        jif.setVisible(true);
        try { jif.setSelected(true); } catch (Exception ignored) {}

        jif.addInternalFrameListener(new javax.swing.event.InternalFrameAdapter() {
            @Override public void internalFrameClosing(javax.swing.event.InternalFrameEvent e) {
                if (win instanceof TextWindow) ((TextWindow) win).close();
                else win.dispose();
                frameMap.remove(win);
            }
        });
    }

    public void unregister(Frame win) {
        frameMap.remove(win);
        repaint();
    }

    /** Opens the Automate Your Work panel as an internal frame. */
    public void openAutomationPanel() {
        // Check if already open
        for (JInternalFrame f : getAllFrames()) {
            if (f instanceof ij.plugin.morpho.AutomationPanel) {
                try { f.setIcon(false); f.setSelected(true); } catch (Exception ignored) {}
                return;
            }
        }
        ij.plugin.morpho.AutomationPanel ap = new ij.plugin.morpho.AutomationPanel();
        // Fill ~90% of desktop
        int w = (int)(getWidth() * 0.90);
        int h = (int)(getHeight() * 0.90);
        ap.setBounds((getWidth() - w) / 2, (getHeight() - h) / 2, w, h);
        add(ap);
        ap.setVisible(true);
        try { ap.setSelected(true); } catch (Exception ignored) {}
    }

    // ── Tile / Cascade ─────────────────────────────────────────────────────────
    public void tileWindows() {
        JInternalFrame[] frames = getAllFrames();
        int n = frames.length;
        if (n == 0) return;
        int cols = (int) Math.ceil(Math.sqrt(n));
        int rows = (int) Math.ceil((double) n / cols);
        int w = getWidth() / cols, h = (getHeight() - 40) / rows;
        int i = 0;
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols && i < n; c++, i++) {
                try { frames[i].setIcon(false); frames[i].setMaximum(false); } catch (Exception ignored) {}
                frames[i].setBounds(c * w, r * h, w, h);
            }
    }

    // ── Custom DesktopManager: dock minimised icons at bottom ──────────────────
    static class BottomDockManager extends DefaultDesktopManager {

        private static final int ICON_W = 160;
        private static final int ICON_H = 32;
        private final JDesktopPane desk;

        BottomDockManager(JDesktopPane desk) { this.desk = desk; }

        @Override
        public void iconifyFrame(JInternalFrame f) {
            super.iconifyFrame(f);
            SwingUtilities.invokeLater(this::repositionIcons);
        }

        @Override
        public void deiconifyFrame(JInternalFrame f) {
            super.deiconifyFrame(f);
            SwingUtilities.invokeLater(this::repositionIcons);
        }

        /** Lays out all minimised icons along the bottom of the desktop. */
        void repositionIcons() {
            int deskH = desk.getHeight();
            int x = 0;
            for (JInternalFrame f : desk.getAllFrames()) {
                if (f.isIcon()) {
                    JInternalFrame.JDesktopIcon icon = f.getDesktopIcon();
                    icon.setBounds(x, deskH - ICON_H, ICON_W, ICON_H);
                    x += ICON_W + 2;
                }
            }
        }
    }
}
