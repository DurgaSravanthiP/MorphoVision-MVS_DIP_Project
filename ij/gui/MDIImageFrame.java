package ij.gui;

import ij.*;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;

/**
 * MDIImageFrame – JInternalFrame hosting an ImageCanvas inside MorphoDesktop.
 *
 * Fix: awtHost uses null layout so we set canvas bounds directly.
 * fitToWindow() then reads the correct size from getWidth()/getHeight().
 */
public class MDIImageFrame extends JInternalFrame {

    private final ImageWindow imageWin;
    private ImageCanvas ic;
    private Panel awtHost;

    public MDIImageFrame(ImageWindow win) {
        super(win.getTitle(), true, true, true, true);
        this.imageWin = win;
        this.ic       = win.getCanvas();

        getContentPane().setLayout(new BorderLayout());
        getContentPane().setBackground(Color.black);

        // ── AWT bridge: null layout so WE control canvas bounds ───────────
        awtHost = new Panel(null);
        awtHost.setBackground(Color.black);

        win.remove(ic);
        awtHost.add(ic);

        // Transfer any scrollbars (StackWindow sliders)
        int n = win.getComponentCount();
        for (int i = 0; i < n; i++) {
            Component c = win.getComponent(i);
            win.remove(c);
            awtHost.add(c);
            i--; n--;
        }

        getContentPane().add(awtHost, BorderLayout.CENTER);

        // ── Re-fit on every resize ─────────────────────────────────────────
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                doFitLater(50);
            }
        });

        // ── Internal frame lifecycle ────────────────────────────────────────
        addInternalFrameListener(new InternalFrameAdapter() {

            @Override
            public void internalFrameOpened(InternalFrameEvent e) {
                // Try multiple times to handle slow AWT peer creation
                doFitLater(100);
                doFitLater(300);
                doFitLater(600);
            }

            @Override
            public void internalFrameActivated(InternalFrameEvent e) {
                if (!imageWin.isClosed())
                    WindowManager.setCurrentWindow(imageWin);
            }

            @Override
            public void internalFrameClosing(InternalFrameEvent e) {
                MorphoDesktop desk = MorphoDesktop.getInstance();
                if (desk != null) desk.unregister(imageWin);
                imageWin.add(ic);
                imageWin.close();
            }
        });

        // ── Scroll wheel → zoom ─────────────────────────────────────────────
        MouseWheelListener zoom = e -> {
            if (ic == null) return;
            int rot = e.getWheelRotation();
            int cx  = ic.getWidth()  / 2;
            int cy  = ic.getHeight() / 2;
            if (rot < 0) ic.zoomIn(cx, cy);
            else          ic.zoomOut(cx, cy);
        };
        addMouseWheelListener(zoom);
        awtHost.addMouseWheelListener(zoom);
    }

    // ── Fit helpers ──────────────────────────────────────────────────────────

    private void doFitLater(int delayMs) {
        Timer t = new Timer(delayMs, ev -> {
            ((Timer) ev.getSource()).stop();
            fitImage();
        });
        t.setRepeats(false);
        t.start();
    }

    /**
     * Manually sets canvas bounds to fill the content pane, then calls
     * fitToWindow() which uses getWidth()/getHeight() to recalculate zoom.
     */
    private void fitImage() {
        if (ic == null || imageWin == null || imageWin.getImagePlus() == null) return;
        if (!isShowing()) return;

        // Available area = JInternalFrame minus its own border/title insets
        Insets fi = getInsets();
        int avW = getWidth()  - fi.left - fi.right;
        int avH = getHeight() - fi.top  - fi.bottom;
        if (avW < 10 || avH < 10) return;

        // Stretch the AWT host to fill the content area
        awtHost.setBounds(0, 0, avW, avH);

        // Directly set the canvas bounds (null layout, so this sticks)
        ic.setBounds(0, 0, avW, avH);

        // fitToWindow() now reads ic.getWidth()==avW, ic.getHeight()==avH
        // and computes the correct magnification
        ic.fitToWindow();

        // Force repaints
        ic.repaint();
        awtHost.repaint();
        repaint();
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    public ImageCanvas getImageCanvas() { return ic; }
    public ImageWindow getImageWindow() { return imageWin; }

    public void updateTitle() {
        if (imageWin != null && imageWin.getImagePlus() != null)
            setTitle(imageWin.getImagePlus().getTitle());
    }
}
