package ij.util;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import java.util.*;

/**
 * Advanced geometric analysis tools for MorphoVision.
 */
public class MorphoAnalyzer {

    /**
     * Calculates the local curvature at each point along the perimeter.
     * Returns an array of curvature values.
     */
    public static double[] calculateCurvature(Roi roi) {
        if (roi == null) return new double[0];
        Polygon poly = roi.getPolygon();
        int n = poly.npoints;
        if (n < 3) return new double[n];
        
        double[] curvatures = new double[n];
        for (int i = 0; i < n; i++) {
            int prev = (i - 1 + n) % n;
            int next = (i + 1) % n;
            
            double x1 = poly.xpoints[prev], y1 = poly.ypoints[prev];
            double x2 = poly.xpoints[i], y2 = poly.ypoints[i];
            double x3 = poly.xpoints[next], y3 = poly.ypoints[next];
            
            // Vector calculation
            double v1x = x2 - x1, v1y = y2 - y1;
            double v2x = x3 - x2, v2y = y3 - y2;
            
            double dot = v1x * v2x + v1y * v2y;
            double mag1 = Math.sqrt(v1x * v1x + v1y * v1y);
            double mag2 = Math.sqrt(v2x * v2x + v2y * v2y);
            
            if (mag1 * mag2 == 0) {
                curvatures[i] = 0;
            } else {
                double cosTheta = dot / (mag1 * mag2);
                if (cosTheta > 1.0) cosTheta = 1.0;
                if (cosTheta < -1.0) cosTheta = -1.0;
                curvatures[i] = Math.acos(cosTheta); // Angle in radians
            }
        }
        return curvatures;
    }

    /**
     * Estimates Fractal Dimension using Box Counting.
     */
    public static double calculateFractalDimension(ImageProcessor ip, Roi roi) {
        // Simplified box counting for a specific ROI
        // For production, we'd use a more robust implementation
        int[] sizes = {2, 4, 8, 16, 32};
        double[] logSizes = new double[sizes.length];
        double[] logCounts = new double[sizes.length];
        
        Rectangle bounds = roi.getBounds();
        for (int i = 0; i < sizes.length; i++) {
            int size = sizes[i];
            int count = 0;
            for (int y = bounds.y; y < bounds.y + bounds.height; y += size) {
                for (int x = bounds.x; x < bounds.x + bounds.width; x += size) {
                    if (containsForeground(ip, x, y, size, size)) {
                        count++;
                    }
                }
            }
            logSizes[i] = Math.log(1.0/size);
            logCounts[i] = Math.log(count);
        }
        
        // Linear regression slope
        return linearRegressionSlope(logSizes, logCounts);
    }
    
    private static boolean containsForeground(ImageProcessor ip, int x, int y, int w, int h) {
        for (int j = y; j < y + h; j++) {
            for (int i = x; i < x + w; i++) {
                if (ip.getPixel(i, j) > 0) return true;
            }
        }
        return false;
    }
    
    private static double linearRegressionSlope(double[] x, double[] y) {
        int n = x.length;
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        for (int i = 0; i < n; i++) {
            sumX += x[i];
            sumY += y[i];
            sumXY += x[i] * y[i];
            sumXX += x[i] * x[i];
        }
        return (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);
    }
}
