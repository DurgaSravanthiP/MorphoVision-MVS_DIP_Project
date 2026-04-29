package ij.gui;

import ij.*;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;

public class MDIImageFrame extends JInternalFrame {

    private final ImageWindow imageWin;
    private ImageCanvas ic;
    private Panel awtHost;

    public MDIImageFrame(ImageWindow win) {
        super(win.getTitle(), true, true, true, true);
        this.imageWin = win;
        this.ic = win.getCanvas();

        getContentPane().setLayout(new BorderLayout());
        getContentPane().setBackground(Color.black);

        // AWT bridge – null layout so WE fully control canvas bounds
        awtHost = new Panel(null);
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

        // When the canvas peer is created → fit image
        ic.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && ic.isShowing()) {
                SwingUtilities.invokeLater(this::fitImage);
            }
        });

        // Re-fit on every resize of the internal frame
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { fitImage(); }
        });

        addInternalFrameListener(new InternalFrameAdapter() {
            @Override public void internalFrameOpened(InternalFrameEvent e) {
                doFitLater(150);
                doFitLater(500);
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

        // ── Scroll wheel: zoom in/out without resizing the canvas ──────────────
        // We NEVER call ic.zoomIn/zoomOut because those call setSize() on the
        // canvas, shrinking it and leaving ghost-image paint artifacts.
        // Instead we directly set magnification + srcRect.
        MouseWheelListener zoom = e -> {
            if (ic == null || imageWin == null) return;
            ImagePlus imp = imageWin.getImagePlus();
            if (imp == null) return;

            int avW = awtHost.getWidth();
            int avH = awtHost.getHeight();
            if (avW < 10 || avH < 10) return;

            int imgW = imp.getWidth();
            int imgH = imp.getHeight();

            // Step magnification using ImageJ's own zoom levels
            double cur = ic.getMagnification();
            double newMag = (e.getWheelRotation() < 0)
                ? ImageCanvas.getHigherZoomLevel(cur)
                : ImageCanvas.getLowerZoomLevel(cur);

            // Don't zoom out beyond fit-to-window
            double minMag = Math.min((double) avW / imgW, (double) avH / imgH);
            if (newMag < minMag) newMag = minMag;

            // Keep the center of the current view stable
            Rectangle src = ic.getSrcRect();
            double cx = src.x + src.width  / 2.0;
            double cy = src.y + src.height / 2.0;

            int newSrcW = Math.min((int)(avW / newMag), imgW);
            int newSrcH = Math.min((int)(avH / newMag), imgH);
            int newX = (int)(cx - newSrcW / 2.0);
            int newY = (int)(cy - newSrcH / 2.0);
            newX = Math.max(0, Math.min(newX, imgW - newSrcW));
            newY = Math.max(0, Math.min(newY, imgH - newSrcH));

            // Apply – canvas stays pinned to full frame size
            ic.setMagnification(newMag);
            src.setBounds(newX, newY, newSrcW, newSrcH);
            ic.setBounds(0, 0, avW, avH);   // keep canvas at full size
            awtHost.repaint();              // clear old pixels before redraw
            ic.repaint();
        };
        addMouseWheelListener(zoom);
        awtHost.addMouseWheelListener(zoom);
    }

    private void doFitLater(int ms) {
        Timer t = new Timer(ms, ev -> { ((Timer)ev.getSource()).stop(); fitImage(); });
        t.setRepeats(false);
        t.start();
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

        // ── 1. Canvas fills the entire available area ──────────────────────
        awtHost.setBounds(0, 0, avW, avH);
        ic.setBounds(0, 0, avW, avH);   // null-layout parent → this sticks

        // ── 2. Compute magnification manually (do NOT call fitToWindow –
        //       it shrinks the canvas with setSize()) ──────────────────────
        double mag = Math.min((double) avW / imgW, (double) avH / imgH);
        if (mag <= 0) return;

        ic.setMagnification(mag);

        // ── 3. Reset view to show the full image ───────────────────────────
        Rectangle src = ic.getSrcRect();
        src.x = 0; src.y = 0;
        src.width = imgW; src.height = imgH;

        // ── 4. Repaint ──────────────────────────────────────────────────────
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
