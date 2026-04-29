package ij.gui;

import ij.*;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;

public class MDIImageFrame extends JInternalFrame {

    private final ImageWindow imageWin;
    private ImageCanvas ic;

    // ClearPanel fills black before painting children, preventing ghost artifacts
    // because ImageCanvas overrides update() to skip clearRect()
    private Panel awtHost;

    public MDIImageFrame(ImageWindow win) {
        super(win.getTitle(), true, true, true, true);
        this.imageWin = win;
        this.ic = win.getCanvas();

        getContentPane().setLayout(new BorderLayout());
        getContentPane().setBackground(Color.black);

        // AWT bridge: anonymous Panel that clears itself before children paint
        awtHost = new Panel(null) {
            @Override
            public void paint(Graphics g) {
                // Clear background so old canvas content doesn't bleed through
                g.setColor(Color.black);
                g.fillRect(0, 0, getWidth(), getHeight());
                super.paint(g);   // paints child components (ic)
            }
            @Override
            public void update(Graphics g) {
                paint(g);         // same clearing behaviour on update
            }
        };
        awtHost.setBackground(Color.black);

        win.remove(ic);
        awtHost.add(ic);

        // Transfer any StackWindow scrollbars
        int n = win.getComponentCount();
        for (int i = 0; i < n; i++) {
            Component c = win.getComponent(i);
            win.remove(c);
            awtHost.add(c);
            i--; n--;
        }

        getContentPane().add(awtHost, BorderLayout.CENTER);

        // Fit image as soon as AWT peer is ready
        ic.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && ic.isShowing())
                SwingUtilities.invokeLater(this::fitImage);
        });

        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { fitImage(); }
        });

        addInternalFrameListener(new InternalFrameAdapter() {
            @Override public void internalFrameOpened(InternalFrameEvent e) {
                doFitLater(150); doFitLater(500);
            }
            @Override public void internalFrameActivated(InternalFrameEvent e) {
                if (!imageWin.isClosed()) WindowManager.setCurrentWindow(imageWin);
            }
            @Override public void internalFrameClosing(InternalFrameEvent e) {
                MorphoDesktop desk = MorphoDesktop.getInstance();
                if (desk != null) desk.unregister(imageWin);
                imageWin.add(ic);
                imageWin.close();
            }
        });

        // ── Scroll wheel zoom ────────────────────────────────────────────────
        // We do NOT call ic.zoomIn/zoomOut — those resize the canvas causing
        // ghost images. We manually update magnification + srcRect instead.
        MouseWheelListener zoom = e -> {
            if (ic == null || imageWin == null) return;
            ImagePlus imp = imageWin.getImagePlus();
            if (imp == null) return;

            int avW = awtHost.getWidth();
            int avH = awtHost.getHeight();
            if (avW < 10 || avH < 10) return;

            int imgW = imp.getWidth();
            int imgH = imp.getHeight();

            // Pick next ImageJ zoom level
            double cur = ic.getMagnification();
            double newMag = (e.getWheelRotation() < 0)
                    ? ImageCanvas.getHigherZoomLevel(cur)
                    : ImageCanvas.getLowerZoomLevel(cur);

            // Don't zoom out past fit-to-frame
            double minMag = Math.min((double) avW / imgW, (double) avH / imgH);
            if (newMag < minMag) newMag = minMag;
            if (newMag > 32.0)   newMag = 32.0;

            // Compute new srcRect centred on current view centre
            Rectangle src = ic.getSrcRect();
            double cx = src.x + src.width  / 2.0;
            double cy = src.y + src.height / 2.0;
            int newSrcW = Math.min((int) Math.ceil(avW / newMag), imgW);
            int newSrcH = Math.min((int) Math.ceil(avH / newMag), imgH);
            int newX = (int)(cx - newSrcW / 2.0);
            int newY = (int)(cy - newSrcH / 2.0);
            newX = Math.max(0, Math.min(newX, imgW - newSrcW));
            newY = Math.max(0, Math.min(newY, imgH - newSrcH));

            // Apply – canvas always fills the frame
            ic.setMagnification(newMag);
            src.setBounds(newX, newY, newSrcW, newSrcH);
            ic.setBounds(0, 0, avW, avH);

            // Clear stale content, then repaint
            Graphics g = ic.getGraphics();
            if (g != null) { g.setColor(Color.black); g.fillRect(0, 0, avW, avH); g.dispose(); }
            ic.repaint();
        };
        addMouseWheelListener(zoom);
        awtHost.addMouseWheelListener(zoom);
    }

    private void doFitLater(int ms) {
        Timer t = new Timer(ms, ev -> { ((Timer) ev.getSource()).stop(); fitImage(); });
        t.setRepeats(false); t.start();
    }

    private void fitImage() {
        if (ic == null || imageWin == null) return;
        ImagePlus imp = imageWin.getImagePlus();
        if (imp == null) return;

        Insets fi = getInsets();
        int avW = getWidth()  - fi.left - fi.right;
        int avH = getHeight() - fi.top  - fi.bottom;
        if (avW < 10 || avH < 10) return;

        int imgW = imp.getWidth();
        int imgH = imp.getHeight();
        if (imgW <= 0 || imgH <= 0) return;

        // Expand canvas to fill frame
        awtHost.setBounds(0, 0, avW, avH);
        ic.setBounds(0, 0, avW, avH);

        // Magnification to show full image
        double mag = Math.min((double) avW / imgW, (double) avH / imgH);
        if (mag <= 0) return;

        ic.setMagnification(mag);
        Rectangle src = ic.getSrcRect();
        src.x = 0; src.y = 0; src.width = imgW; src.height = imgH;

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
