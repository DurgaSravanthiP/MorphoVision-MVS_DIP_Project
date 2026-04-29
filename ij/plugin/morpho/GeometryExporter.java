package ij.plugin.morpho;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Base64;

/**
 * Exports particle analysis results as:
 *  – CSV  (one row per particle)
 *  – Self-contained HTML report (all charts embedded as base64 PNG)
 */
public class GeometryExporter {

    // ── CSV ───────────────────────────────────────────────────────────────────

    public static void exportCSV(List<ParticleGeometry> particles,
                                 String unit, File out) throws IOException {
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(out), StandardCharsets.UTF_8))) {
            pw.println("# MorphoVision Particle Analysis — " + timestamp());
            pw.println("# Scale unit: " + unit);
            pw.println(ParticleGeometry.csvHeader() + ",Unit");
            for (ParticleGeometry p : particles)
                pw.println(p.toCSVRow() + "," + unit);
        }
    }

    // ── HTML Report ───────────────────────────────────────────────────────────

    public static void exportHTMLReport(List<ParticleGeometry> particles,
                                        List<BufferedImage> charts,
                                        String imageName, String unit,
                                        double scale, int particleCount,
                                        File out) throws IOException {
        StringBuilder sb = new StringBuilder();

        // ── statistics ─────────────────────────────────────────────────────
        double meanArea   = ParticleGeometry.mean(particles, p -> p.area);
        double sdArea     = ParticleGeometry.stdDev(particles, p -> p.area);
        double meanCirc   = ParticleGeometry.mean(particles, p -> p.circularity);
        double sdCirc     = ParticleGeometry.stdDev(particles, p -> p.circularity);
        double meanAR     = ParticleGeometry.mean(particles, p -> p.aspectRatio);
        double sdAR       = ParticleGeometry.stdDev(particles, p -> p.aspectRatio);
        double meanED     = ParticleGeometry.mean(particles, p -> p.equivDiameter);
        double sdED       = ParticleGeometry.stdDev(particles, p -> p.equivDiameter);
        double meanFeret  = ParticleGeometry.mean(particles, p -> p.feretMax);
        double sdFeret    = ParticleGeometry.stdDev(particles, p -> p.feretMax);
        double meanPerim  = ParticleGeometry.mean(particles, p -> p.perimeter);
        double sdPerim    = ParticleGeometry.stdDev(particles, p -> p.perimeter);

        // ── HTML head ──────────────────────────────────────────────────────
        sb.append("<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'/>");
        sb.append("<title>MorphoVision — Particle Analysis Report</title>");
        sb.append("<style>");
        sb.append("body{font-family:'Segoe UI',Arial,sans-serif;background:#f4f4f7;color:#222;margin:0;padding:0}");
        sb.append(".header{background:linear-gradient(135deg,#1a3a6e,#2d5aa0);color:#fff;padding:32px 40px}");
        sb.append(".header h1{margin:0;font-size:24px;letter-spacing:1px}");
        sb.append(".header p{margin:6px 0 0;opacity:.8;font-size:13px}");
        sb.append(".section{background:#fff;border-radius:8px;box-shadow:0 2px 8px rgba(0,0,0,.08);margin:24px 40px;padding:24px}");
        sb.append(".section h2{color:#2d5aa0;border-bottom:2px solid #e8eef7;padding-bottom:8px;margin-top:0}");
        sb.append(".stats-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:16px;margin-top:16px}");
        sb.append(".stat-card{background:#f8faff;border:1px solid #dde8f8;border-radius:6px;padding:14px;text-align:center}");
        sb.append(".stat-card .val{font-size:20px;font-weight:700;color:#1a3a6e}");
        sb.append(".stat-card .lbl{font-size:11px;color:#666;margin-top:4px}");
        sb.append(".charts{display:grid;grid-template-columns:repeat(2,1fr);gap:20px;margin-top:16px}");
        sb.append(".charts img{width:100%;border-radius:6px;border:1px solid #ddd}");
        sb.append("table{width:100%;border-collapse:collapse;font-size:12px;margin-top:12px}");
        sb.append("th{background:#2d5aa0;color:#fff;padding:8px 10px;text-align:left}");
        sb.append("tr:nth-child(even){background:#f4f8ff} td{padding:6px 10px;border-bottom:1px solid #eee}");
        sb.append(".footer{text-align:center;color:#999;font-size:11px;padding:24px;margin:0 40px}");
        sb.append("@media print{.section{box-shadow:none} body{background:#fff}}");
        sb.append("</style></head><body>");

        // ── Header ─────────────────────────────────────────────────────────
        sb.append("<div class='header'>");
        sb.append("<h1>⚡ MorphoVision — Automated Particle Analysis Report</h1>");
        sb.append("<p>Image: <strong>").append(imageName).append("</strong> &nbsp;|&nbsp; ");
        sb.append("Generated: ").append(timestamp()).append(" &nbsp;|&nbsp; ");
        sb.append("Scale: ").append(scale > 0 ? scale + " nm/pixel" : "Not calibrated").append("</p>");
        sb.append("</div>");

        // ── Summary stats ──────────────────────────────────────────────────
        sb.append("<div class='section'><h2>📊 Analysis Summary</h2>");
        sb.append("<div class='stats-grid'>");
        stat(sb, String.valueOf(particleCount), "Total Particles Detected");
        stat(sb, fmt(meanArea) + " ± " + fmt(sdArea), "Mean Area (" + unit + "²)");
        stat(sb, fmt(meanCirc) + " ± " + fmt(sdCirc), "Mean Circularity");
        stat(sb, fmt(meanED) + " ± " + fmt(sdED), "Mean Equiv. Diameter (" + unit + ")");
        stat(sb, fmt(meanAR) + " ± " + fmt(sdAR), "Mean Aspect Ratio");
        stat(sb, fmt(meanFeret) + " ± " + fmt(sdFeret), "Mean Feret Max (" + unit + ")");
        sb.append("</div></div>");

        // ── Charts ─────────────────────────────────────────────────────────
        sb.append("<div class='section'><h2>📈 Distribution Charts</h2><div class='charts'>");
        String[] chartTitles = {"Area Distribution", "Circularity Distribution",
                                "Aspect Ratio Distribution", "Area vs Circularity Scatter"};
        for (int i = 0; i < charts.size(); i++) {
            sb.append("<div><img src='data:image/png;base64,")
              .append(toBase64(charts.get(i)))
              .append("' alt='").append(chartTitles[Math.min(i, chartTitles.length - 1)]).append("'/></div>");
        }
        sb.append("</div></div>");

        // ── Particle table ─────────────────────────────────────────────────
        sb.append("<div class='section'><h2>🔬 Per-Particle Measurements</h2>");
        sb.append("<table><thead><tr>");
        sb.append("<th>ID</th><th>Area (").append(unit).append("²)</th>");
        sb.append("<th>Perimeter (").append(unit).append(")</th>");
        sb.append("<th>Circularity</th><th>Equiv.Diam (").append(unit).append(")</th>");
        sb.append("<th>Aspect Ratio</th><th>Feret Max (").append(unit).append(")</th>");
        sb.append("</tr></thead><tbody>");
        for (ParticleGeometry p : particles) {
            sb.append("<tr><td>").append(p.id).append("</td>")
              .append("<td>").append(fmt(p.area)).append("</td>")
              .append("<td>").append(fmt(p.perimeter)).append("</td>")
              .append("<td>").append(fmt(p.circularity)).append("</td>")
              .append("<td>").append(fmt(p.equivDiameter)).append("</td>")
              .append("<td>").append(fmt(p.aspectRatio)).append("</td>")
              .append("<td>").append(fmt(p.feretMax)).append("</td></tr>");
        }
        sb.append("</tbody></table></div>");

        // ── Footer ─────────────────────────────────────────────────────────
        sb.append("<div class='footer'>Generated by MorphoVision · Automated Particle Analysis Pipeline · ")
          .append(timestamp()).append("</div></body></html>");

        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(out), StandardCharsets.UTF_8))) {
            pw.print(sb);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void stat(StringBuilder sb, String val, String label) {
        sb.append("<div class='stat-card'><div class='val'>").append(val)
          .append("</div><div class='lbl'>").append(label).append("</div></div>");
    }

    private static String fmt(double v) {
        return String.format("%.3f", v);
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    private static String toBase64(BufferedImage img) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }
}
