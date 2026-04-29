package ij.gui;

import ij.*;
import ij.process.*;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;

/**
 * MDIImageFrame – a JInternalFrame that hosts an ImageCanvas inside
 * the MorphoDesktop.  It keeps the AWT ImageWindow alive for all
 * ImageJ processing logic but routes display into this Swing frame.
 */
public class MDIImageFrame extends JInternalFrame {

    private final ImageWindow imageWin;
    private ImageCanvas ic;

    // AWT Panel bridge (heavyweight inside Swing is safe from Java 6+)
    private Panel awtHost;

    public MDIImageFrame(ImageWindow win) {
        super(win.getTitle(), true, true, true, true);
        this.imageWin = win;
        this.ic = win.getCanvas();

        setBackground(new Color(30, 30, 35));
        getContentPane().setLayout(new BorderLayout());

        // ── AWT host panel ──────────────────────────────────────────────────
        awtHost = new Panel(new BorderLayout());
        awtHost.setBackground(Color.black);

        // Pull canvas out of the hidden ImageWindow and into our host
        win.remove(ic);
        awtHost.add(ic, BorderLayout.CENTER);

        // Also move any scrollbars (StackWindow sliders) to SOUTH
        int count = win.getComponentCount();
        for (int i = 0; i < count; i++) {
            Component c = win.getComponent(i);
            win.remove(c);
            awtHost.add(c, BorderLayout.SOUTH);
            i--;
            count--;
        }

        getContentPane().add(awtHost, BorderLayout.CENTER);

        // ── Resize: fit image to the frame whenever the frame is resized ────
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                fitImage();
            }
            @Override
            public void componentShown(ComponentEvent e) {
                fitImage();
            }
        });

        // ── Internal frame events ────────────────────────────────────────────
        addInternalFrameListener(new InternalFrameAdapter() {

            @Override
            public void internalFrameOpened(InternalFrameEvent e) {
                // Defer so the layout has been realized first
                SwingUtilities.invokeLater(() -> fitImage());
            }

            @Override
            public void internalFrameClosing(InternalFrameEvent e) {
                MorphoDesktop desk = MorphoDesktop.getInstance();
                if (desk != null) desk.unregister(imageWin);
                // Re-attach canvas so ImageWindow.close() can dispose properly
                imageWin.add(ic);
                imageWin.close();
            }

            @Override
            public void internalFrameActivated(InternalFrameEvent e) {
                if (!imageWin.isClosed())
                    WindowManager.setCurrentWindow(imageWin);
            }
        });

        // ── Ctrl+Scroll → zoom the image ─────────────────────────────────────
        MouseWheelListener zoomListener = e -> {
            if (ic == null) return;
            boolean ctrl = (e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0;
            // Always zoom with scroll inside the MDI frame (no Ctrl required)
            int rot = e.getWheelRotation();
            int cx = ic.getWidth() / 2;
            int cy = ic.getHeight() / 2;
            if (rot < 0) ic.zoomIn(cx, cy);
            else          ic.zoomOut(cx, cy);
        };
        addMouseWheelListener(zoomListener);
        awtHost.addMouseWheelListener(zoomListener);

        // ── Initial size: use most of the desktop ────────────────────────────
        setPreferredSize(new Dimension(700, 520));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Sizes the canvas to fill the host panel, then asks ImageJ to
     * compute the correct magnification for that size.
     */
    private void fitImage() {
        if (ic == null || !isVisible()) return;
        Dimension hostSize = awtHost.getSize();
        if (hostSize.width < 10 || hostSize.height < 10) return;

        // Resize the canvas to fill the host panel
        ic.setSize(hostSize.width, hostSize.height);
        // Ask ImageJ to recalculate zoom so the whole image is visible
        ic.fitToWindow();
        // Force a repaint of both AWT and Swing layers
        ic.repaint();
        awtHost.repaint();
        repaint();
    }

    public ImageCanvas getImageCanvas() { return ic; }
    public ImageWindow getImageWindow() { return imageWin; }

    public void updateTitle() {
        if (imageWin != null && imageWin.getImagePlus() != null)
            setTitle(imageWin.getImagePlus().getTitle());
    }
}
