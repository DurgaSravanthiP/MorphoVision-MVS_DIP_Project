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

    // Panel that wraps the AWT canvas for Swing embedding
    private Panel awtHost;

    public MDIImageFrame(ImageWindow win) {
        super(win.getTitle(), true, true, true, true);
        this.imageWin = win;
        this.ic = win.getCanvas();

        setBackground(new Color(45, 45, 50));
        getContentPane().setLayout(new BorderLayout());

        // --- title bar / subtitle label ---
        JLabel titleLabel = new JLabel(win.getTitle());
        titleLabel.setForeground(new Color(220, 220, 220));
        titleLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        titleLabel.setBackground(new Color(60, 60, 65));
        titleLabel.setOpaque(true);

        // Embed the AWT ImageCanvas into the Swing frame using a Panel bridge
        // (AWT heavyweight inside Swing lightweight is safe from Java 6+)
        awtHost = new Panel(new BorderLayout());
        awtHost.setBackground(Color.black);

        // Transfer the canvas from the original ImageWindow into our host panel
        win.remove(ic);
        awtHost.add(ic, BorderLayout.CENTER);

        // If this is a StackWindow, also transfer the scrollbars
        int count = win.getComponentCount();
        for (int i = 0; i < count; i++) {
            Component c = win.getComponent(i);
            win.remove(c);
            awtHost.add(c, BorderLayout.SOUTH);
            i--;
            count--;
        }

        getContentPane().add(awtHost, BorderLayout.CENTER);
        getContentPane().add(titleLabel, BorderLayout.NORTH);

        // ---- Internal frame listeners ----
        addInternalFrameListener(new InternalFrameAdapter() {

            @Override
            public void internalFrameClosing(InternalFrameEvent e) {
                // Delegate to ImageWindow.close() which handles unsaved-changes dialog
                MorphoDesktop desk = MorphoDesktop.getInstance();
                if (desk != null) desk.unregister(imageWin);
                // Re-attach canvas so ImageWindow.close() can dispose properly
                imageWin.add(ic);
                imageWin.close();
            }

            @Override
            public void internalFrameActivated(InternalFrameEvent e) {
                // Notify WindowManager which image is active
                if (!imageWin.isClosed())
                    WindowManager.setCurrentWindow(imageWin);
            }

            @Override
            public void internalFrameIconified(InternalFrameEvent e) {
                // nothing special
            }
        });

        // Zoom with Ctrl+Scroll anywhere on the frame
        MouseWheelListener zoomListener = e -> {
            if (ic == null) return;
            boolean ctrl = (e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0;
            if (ctrl || IJ.shiftKeyDown()) {
                int rot = e.getWheelRotation();
                int cx = ic.getWidth() / 2;
                int cy = ic.getHeight() / 2;
                if (rot < 0) ic.zoomIn(cx, cy);
                else          ic.zoomOut(cx, cy);
            }
        };
        addMouseWheelListener(zoomListener);
        awtHost.addMouseWheelListener(zoomListener);

        // Set icon from the main ImageJ frame if available
        ImageJ ij = IJ.getInstance();
        if (ij != null && !ij.getIconImages().isEmpty()) {
            try { setFrameIcon(new ImageIcon(ij.getIconImages().get(0))); } catch (Exception ignored) {}
        }

        // Pack to a reasonable initial size based on the canvas
        if (ic != null) {
            Dimension canvasSize = ic.getPreferredSize();
            int fw = Math.max(200, canvasSize.width + 20);
            int fh = Math.max(150, canvasSize.height + 50);
            setPreferredSize(new Dimension(fw, fh));
        }
    }

    public ImageCanvas getImageCanvas() {
        return ic;
    }

    public ImageWindow getImageWindow() {
        return imageWin;
    }

    /** Refreshes the subtitle label and frame title when image changes. */
    public void updateTitle() {
        if (imageWin != null && imageWin.getImagePlus() != null)
            setTitle(imageWin.getImagePlus().getTitle());
    }
}
