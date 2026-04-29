package ij.gui;

import ij.*;
import ij.text.TextWindow;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

/**
 * MorphoDesktop – a JDesktopPane that acts as the single MDI workspace.
 * All ImageWindows and TextWindows are embedded here as JInternalFrames
 * instead of opening as separate OS windows.
 *
 * Layout inside ImageJ's main Frame:
 *   CENTER  → this JDesktopPane (image workspace)
 *   EAST    → docked results/log panel (split off when TextWindows appear)
 */
public class MorphoDesktop extends JDesktopPane {

    private static MorphoDesktop instance;

    /** Dock panel on the right for Results / Log */
    private JPanel dockPanel;
    private JSplitPane splitPane;

    // Background splash drawn when no images are open
    private SplashPanel splashBg;

    // Map from AWT Frame → JInternalFrame so we can sync close events
    private final Map<Frame, JInternalFrame> frameMap = new LinkedHashMap<>();

    // ── Singleton ────────────────────────────────────────────────────────────

    public static MorphoDesktop getInstance() {
        return instance;
    }

    public static void setInstance(MorphoDesktop d) {
        instance = d;
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    public MorphoDesktop() {
        setBackground(new Color(30, 30, 35));  // dark desktop background
        setDragMode(JDesktopPane.OUTLINE_DRAG_MODE);

        // Draw splash background when the desktop is empty
        splashBg = new SplashPanel() {
            @Override public boolean isOpaque() { return false; }
        };
        splashBg.setBackground(new Color(0, 0, 0, 0));
        setLayout(null);  // manual layout for internal frames
        instance = this;

        // Mouse-wheel on the desktop → zoom active image
        addMouseWheelListener(e -> {
            JInternalFrame active = getSelectedFrame();
            if (active instanceof MDIImageFrame) {
                MDIImageFrame mif = (MDIImageFrame) active;
                ImageCanvas ic = mif.getImageCanvas();
                if (ic != null) {
                    boolean ctrl = (e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0;
                    int rot = e.getWheelRotation();
                    if (rot < 0) ic.zoomIn(ic.getWidth()/2, ic.getHeight()/2);
                    else          ic.zoomOut(ic.getWidth()/2, ic.getHeight()/2);
                }
            }
        });
    }

    // ── Embed an ImageWindow ─────────────────────────────────────────────────

    /**
     * Hides the given AWT ImageWindow and wraps its canvas in a
     * JInternalFrame on this desktop.
     */
    public MDIImageFrame embedImageWindow(ImageWindow win) {
        if (frameMap.containsKey(win)) {
            JInternalFrame existing = frameMap.get(win);
            try { existing.setSelected(true); } catch (Exception ignored) {}
            return (MDIImageFrame) existing;
        }

        MDIImageFrame mif = new MDIImageFrame(win);
        frameMap.put(win, mif);

        // Place the new internal frame cascaded
        int offset = frameMap.size() * 24;
        int w = Math.min(600, getWidth() - offset - 40);
        int h = Math.min(460, getHeight() - offset - 40);
        if (w < 200) w = 400;
        if (h < 150) h = 300;
        mif.setBounds(offset, offset, w, h);

        add(mif);
        mif.setVisible(true);
        try { mif.setSelected(true); } catch (Exception ignored) {}

        // Hide the original OS window
        win.setVisible(false);

        repaint();
        return mif;
    }

    /** Called when an MDIImageFrame is closed — removes from map. */
    public void unregister(Frame win) {
        frameMap.remove(win);
        repaint();
    }

    // ── Embed a TextWindow (Results / Log) ───────────────────────────────────

    /**
     * Wraps a TextWindow as a JInternalFrame docked inside the desktop.
     */
    public void embedTextWindow(Frame win) {
        if (frameMap.containsKey(win)) {
            JInternalFrame existing = frameMap.get(win);
            existing.toFront();
            try { existing.setSelected(true); } catch (Exception ignored) {}
            return;
        }

        // Remove the AWT content and re-embed in a JInternalFrame
        win.setVisible(false);

        JInternalFrame jif = new JInternalFrame(win.getTitle(), true, true, true, true);
        jif.setLayout(new BorderLayout());

        // Move all components from the Frame into the JInternalFrame
        Component[] comps = win.getComponents();
        for (Component c : comps) {
            win.remove(c);
            jif.getContentPane().add(c, BorderLayout.CENTER);
        }

        frameMap.put(win, jif);

        // Position in the bottom-right corner of the desktop
        int dw = Math.max(400, getWidth() / 3);
        int dh = Math.max(200, getHeight() / 3);
        int dx = Math.max(0, getWidth() - dw - 10);
        int dy = Math.max(0, getHeight() - dh - 10);
        jif.setBounds(dx, dy, dw, dh);

        add(jif);
        jif.setVisible(true);
        try { jif.setSelected(true); } catch (Exception ignored) {}

        // Keep the TextWindow's close logic wired up
        jif.addInternalFrameListener(new javax.swing.event.InternalFrameAdapter() {
            @Override
            public void internalFrameClosing(javax.swing.event.InternalFrameEvent e) {
                if (win instanceof TextWindow)
                    ((TextWindow) win).close();
                else
                    win.dispose();
                frameMap.remove(win);
            }
        });
    }

    // ── Paint background splash when desktop is empty ────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (frameMap.isEmpty()) {
            // Draw the splash when no images/panels are open
            splashBg.setSize(getWidth(), getHeight());
            splashBg.paint(g);
        }
    }

    // ── Tile / Cascade helpers (called from Window menu) ─────────────────────

    public void tileWindows() {
        JInternalFrame[] frames = getAllFrames();
        int n = frames.length;
        if (n == 0) return;
        int cols = (int) Math.ceil(Math.sqrt(n));
        int rows = (int) Math.ceil((double) n / cols);
        int w = getWidth() / cols;
        int h = getHeight() / rows;
        int i = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols && i < n; c++, i++) {
                frames[i].setBounds(c * w, r * h, w, h);
                try { frames[i].setIcon(false); frames[i].setMaximum(false); } catch (Exception ignored) {}
            }
        }
    }

    public void cascadeWindows() {
        JInternalFrame[] frames = getAllFrames();
        int offset = 24;
        for (int i = 0; i < frames.length; i++) {
            int x = i * offset;
            int y = i * offset;
            int w = Math.max(300, getWidth() - x - 20);
            int h = Math.max(200, getHeight() - y - 20);
            frames[i].setBounds(x, y, w, h);
            try { frames[i].setIcon(false); frames[i].setMaximum(false); } catch (Exception ignored) {}
        }
    }
}
