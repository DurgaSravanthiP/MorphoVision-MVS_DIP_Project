package ij.gui;

import ij.*;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;

/**
 * MDIImageFrame – JInternalFrame hosting an ImageCanvas inside MorphoDesktop.
 *
 * Key fix: sizing the canvas is done via a Swing Timer so the JInternalFrame
 * has been fully laid out before we call fitToWindow(). We also derive the
 * target size from the JInternalFrame bounds (always valid) rather than from
 * the AWT sub-panel size (which is 0 until the AWT peer is realised).
 */
public class MDIImageFrame extends JInternalFrame {

    private final ImageWindow imageWin;
    private ImageCanvas ic;
    private Panel awtHost;   // AWT bridge panel

    public MDIImageFrame(ImageWindow win) {
        super(win.getTitle(), true, true, true, true);
        this.imageWin = win;
        this.ic       = win.getCanvas();

        getContentPane().setLayout(new BorderLayout());
        getContentPane().setBackground(Color.black);

        // ── AWT bridge: Canvas lives inside an AWT Panel ──────────────────
        awtHost = new Panel(new BorderLayout());
        awtHost.setBackground(Color.black);

        win.remove(ic);
        awtHost.add(ic, BorderLayout.CENTER);

        // Transfer any scrollbars (StackWindow sliders)
        int n = win.getComponentCount();
        for (int i = 0; i < n; i++) {
            Component c = win.getComponent(i);
            win.remove(c);
            awtHost.add(c, BorderLayout.SOUTH);
            i--; n--;
        }

        getContentPane().add(awtHost, BorderLayout.CENTER);

        // ── Resize: re-fit image whenever the internal frame is resized ────
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { scheduleFit(); }
        });

        // ── Internal frame lifecycle ────────────────────────────────────────
        addInternalFrameListener(new InternalFrameAdapter() {

            @Override
            public void internalFrameOpened(InternalFrameEvent e) {
                // Delay so AWT + Swing layout is fully realized first
                scheduleFit();
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
                imageWin.add(ic);   // re-attach so ImageWindow.close() can dispose
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

    /** Schedules a fit 200 ms later so Swing/AWT layout finishes first. */
    private void scheduleFit() {
        Timer t = new Timer(200, ev -> {
            ((Timer) ev.getSource()).stop();
            fitImage();
        });
        t.setRepeats(false);
        t.start();
    }

    /**
     * Resizes the canvas to fill the content pane, then calls fitToWindow()
     * so ImageJ recalculates the correct magnification.
     */
    private void fitImage() {
        if (ic == null || !isShowing()) return;

        // Measure the usable area: JInternalFrame minus its own borders/title
        Insets fi  = getInsets();                          // Swing frame insets
        int avW = getWidth()  - fi.left - fi.right;
        int avH = getHeight() - fi.top  - fi.bottom;
        if (avW < 10 || avH < 10) return;

        // Push the size down to the AWT panel and canvas
        awtHost.setBounds(0, 0, avW, avH);
        awtHost.setPreferredSize(new Dimension(avW, avH));
        ic.setBounds(0, 0, avW, avH);
        ic.setSize(avW, avH);

        // Recalculate magnification so the whole image is visible
        ic.fitToWindow();

        // Force repaints on both layers
        ic.repaint();
        awtHost.validate();
        awtHost.repaint();
        validate();
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
