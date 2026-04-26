package ij.gui;
import ij.*;
import ij.measure.*;
import ij.plugin.frame.RoiManager;
import java.awt.*;
import java.awt.event.*;

/**
 * Interactive Plotter for MorphoVision.
 */
public class MorphoPlotter extends Plot implements MouseListener {
    private ResultsTable rt;
    private String title;

    public MorphoPlotter(String title, ResultsTable rt) {
        super(title, "Area", "Curvature");
        this.rt = rt;
        this.title = title;
        
        if (rt.columnExists("Area") && rt.columnExists("Curvature")) {
            double[] x = rt.getColumnAsDoubles(rt.getColumnIndex("Area"));
            double[] y = rt.getColumnAsDoubles(rt.getColumnIndex("Curvature"));
            addPoints(x, y, Plot.CIRCLE);
        }
    }

    @Override
    public PlotWindow show() {
        PlotWindow pw = super.show();
        pw.getCanvas().addMouseListener(this);
        return pw;
    }

    public void mousePressed(MouseEvent e) {
        // Find nearest point
        Point p = e.getPoint();
        // This is a simplified version. In a real app, we'd map p back to data coordinates.
        // For demonstration, let's assume we can find the index.
        
        // Let's say we find index 'i'
        int i = findNearestIndex(p);
        if (i >= 0) {
            highlightParticle(i);
        }
    }

    private int findNearestIndex(Point p) {
        if (rt == null) return -1;
        int areaIdx = rt.getColumnIndex("Area");
        int curvIdx = rt.getColumnIndex("Curvature");
        if (areaIdx == -1 || curvIdx == -1) return -1;
        
        double[] xData = rt.getColumnAsDoubles(areaIdx);
        double[] yData = rt.getColumnAsDoubles(curvIdx);
        
        int nearest = -1;
        double minDist = Double.MAX_VALUE;
        
        for (int i = 0; i < xData.length; i++) {
            // Map data to screen coordinates using Plot's scale methods
            int sx = (int)scaleXtoPxl(xData[i]);
            int sy = (int)scaleYtoPxl(yData[i]);
            
            double dist = p.distance(sx, sy);
            if (dist < minDist) {
                minDist = dist;
                nearest = i;
            }
        }
        return (minDist < 15) ? nearest : -1; // 15 pixel radius
    }

    private void highlightParticle(int index) {
        RoiManager rm = RoiManager.getInstance();
        if (rm != null && index < rm.getCount()) {
            rm.select(index);
            IJ.log("Selected Particle #" + (index + 1));
        }
    }

    public void mouseClicked(MouseEvent e) {}
    public void mouseReleased(MouseEvent e) {}
    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}
}
