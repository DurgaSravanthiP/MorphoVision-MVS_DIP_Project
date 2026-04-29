package ij.plugin.morpho;

import ij.*;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * AutomationPanel – full-screen JInternalFrame for the automated pipeline.
 * Opens inside MorphoDesktop without disturbing any existing functionality.
 */
public class AutomationPanel extends JInternalFrame {

    // ── Colours ───────────────────────────────────────────────────────────────
    private static final Color BG       = new Color(28, 28, 34);
    private static final Color PANEL_BG = new Color(38, 38, 46);
    private static final Color CARD_BG  = new Color(48, 48, 58);
    private static final Color ACCENT   = new Color(45, 110, 200);
    private static final Color SUCCESS  = new Color(40, 160, 80);
    private static final Color TEXT     = new Color(220, 220, 230);
    private static final Color SUBTEXT  = new Color(140, 140, 155);
    private static final Color BTN_BG   = new Color(55, 55, 65);

    // ── State ─────────────────────────────────────────────────────────────────
    private ImagePlus               currentImage;
    private List<ParticleGeometry>  results      = new ArrayList<>();
    private final PipelineEngine    engine       = new PipelineEngine();

    // ── UI Components ─────────────────────────────────────────────────────────
    private JLabel        previewLabel;
    private JLabel        particleCountLabel;
    private JProgressBar  progressBar;
    private JLabel        statusLabel;
    private JTextArea     logArea;

    // Step indicators
    private JLabel[] stepIcons;
    private static final String[] STEP_NAMES = {
        "Load Image", "Pre-process", "Threshold",
        "Detect Particles", "Measure Geometry", "Export Results"
    };

    // Settings fields
    private JTextField  sigmaField, minSizeField, thresholdField, scaleField;
    private JCheckBox   bgSubCheck;
    private JComboBox<String> threshMethodCombo;
    private JComboBox<String> scaleUnitCombo;
    private JButton     runBtn, exportCsvBtn, exportReportBtn;

    // ── Constructor ───────────────────────────────────────────────────────────
    public AutomationPanel() {
        super("⚡ Automate Your Work — Particle Analysis Pipeline",
              true, true, true, true);
        setBackground(BG);
        buildUI();
        pack();
    }

    // ── Build UI ──────────────────────────────────────────────────────────────
    private void buildUI() {
        JPanel root = dark(new JPanel(new BorderLayout(0, 0)));
        setContentPane(root);

        root.add(buildHeader(), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildLeftPanel(), buildRightPanel());
        split.setDividerLocation(380);
        split.setDividerSize(4);
        split.setBackground(BG);
        root.add(split, BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);

        setPreferredSize(new Dimension(1000, 680));
    }

    // ── Header ────────────────────────────────────────────────────────────────
    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(20, 50, 110));
        p.setBorder(new EmptyBorder(14, 20, 14, 20));

        JLabel title = new JLabel("⚡  Automated Particle Analysis Pipeline");
        title.setFont(new Font("SansSerif", Font.BOLD, 16));
        title.setForeground(Color.WHITE);

        JLabel sub = new JLabel("One click → detect all particles → measure geometry → export report");
        sub.setFont(new Font("SansSerif", Font.PLAIN, 11));
        sub.setForeground(new Color(180, 200, 240));

        JPanel txt = new JPanel(new GridLayout(2, 1, 0, 2));
        txt.setOpaque(false);
        txt.add(title); txt.add(sub);
        p.add(txt, BorderLayout.CENTER);
        return p;
    }

    // ── Left: preview + particle count ────────────────────────────────────────
    private JPanel buildLeftPanel() {
        JPanel p = dark(new JPanel(new BorderLayout(0, 8)));
        p.setBorder(new EmptyBorder(12, 12, 8, 6));

        // Image preview
        previewLabel = new JLabel("No image loaded", SwingConstants.CENTER);
        previewLabel.setForeground(SUBTEXT);
        previewLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
        previewLabel.setBorder(BorderFactory.createDashedBorder(
                new Color(80, 80, 100), 2, 4, 2, false));
        previewLabel.setPreferredSize(new Dimension(340, 300));

        JScrollPane previewScroll = new JScrollPane(previewLabel);
        previewScroll.setBackground(CARD_BG);
        previewScroll.setBorder(null);
        p.add(previewScroll, BorderLayout.CENTER);

        // Particle count card
        JPanel countCard = card(new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 6)));
        particleCountLabel = new JLabel("–");
        particleCountLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        particleCountLabel.setForeground(ACCENT);
        JLabel countLbl = label("particles detected", SUBTEXT);
        countLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        countCard.add(particleCountLabel);
        countCard.add(countLbl);
        p.add(countCard, BorderLayout.SOUTH);

        return p;
    }

    // ── Right: pipeline steps + settings ─────────────────────────────────────
    private JScrollPane buildRightPanel() {
        JPanel p = dark(new JPanel());
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(12, 6, 8, 12));

        stepIcons = new JLabel[STEP_NAMES.length];

        // Step 1: Load Image
        p.add(buildStepPanel(0, buildStep1Controls()));
        // Step 2: Pre-process
        p.add(buildStepPanel(1, buildStep2Controls()));
        // Step 3: Threshold
        p.add(buildStepPanel(2, buildStep3Controls()));
        // Step 4: Detect
        p.add(buildStepPanel(3, buildStep4Controls()));
        // Step 5: Scale
        p.add(buildStepPanel(4, buildStep5Controls()));
        // Step 6: Export
        p.add(buildStepPanel(5, buildStep6Controls()));

        JScrollPane scroll = new JScrollPane(p);
        scroll.setBorder(null);
        scroll.setBackground(BG);
        return scroll;
    }

    private JPanel buildStepPanel(int idx, JPanel controls) {
        JPanel outer = card(new JPanel(new BorderLayout(0, 6)));
        outer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 999));
        outer.setBorder(new CompoundBorder(
                new EmptyBorder(0, 0, 8, 0),
                new CompoundBorder(
                        BorderFactory.createLineBorder(new Color(60, 60, 80), 1, true),
                        new EmptyBorder(10, 14, 10, 14))));

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleRow.setOpaque(false);
        stepIcons[idx] = new JLabel("⬜");
        stepIcons[idx].setFont(new Font("SansSerif", Font.PLAIN, 13));
        JLabel name = label("  " + (idx + 1) + ".  " + STEP_NAMES[idx], TEXT);
        name.setFont(new Font("SansSerif", Font.BOLD, 12));
        titleRow.add(stepIcons[idx]);
        titleRow.add(name);

        outer.add(titleRow, BorderLayout.NORTH);
        if (controls != null) outer.add(controls, BorderLayout.CENTER);
        return outer;
    }

    // ── Step controls ─────────────────────────────────────────────────────────

    private JPanel buildStep1Controls() {
        JPanel p = flow();
        JButton browse = actionBtn("📂  Browse Image");
        browse.addActionListener(e -> browseImage());
        p.add(browse);
        p.add(label("  or use currently open image →", SUBTEXT));
        JButton useCurrent = actionBtn("Use Current");
        useCurrent.addActionListener(e -> useCurrentImage());
        p.add(useCurrent);
        return p;
    }

    private JPanel buildStep2Controls() {
        JPanel p = new JPanel(new GridLayout(2, 2, 8, 4));
        p.setOpaque(false);
        p.add(label("Gaussian σ:", SUBTEXT));
        sigmaField = field("2.0");
        p.add(sigmaField);
        bgSubCheck = new JCheckBox("Background Subtraction", true);
        bgSubCheck.setForeground(TEXT);
        bgSubCheck.setOpaque(false);
        p.add(bgSubCheck);
        p.add(new JLabel());
        return p;
    }

    private JPanel buildStep3Controls() {
        JPanel p = new JPanel(new GridLayout(2, 2, 8, 4));
        p.setOpaque(false);
        p.add(label("Method:", SUBTEXT));
        threshMethodCombo = new JComboBox<>(new String[]{"Otsu (Auto)", "Manual"});
        threshMethodCombo.setBackground(BTN_BG);
        threshMethodCombo.setForeground(TEXT);
        p.add(threshMethodCombo);
        p.add(label("Manual value:", SUBTEXT));
        thresholdField = field("128");
        p.add(thresholdField);
        return p;
    }

    private JPanel buildStep4Controls() {
        JPanel p = new JPanel(new GridLayout(1, 4, 8, 4));
        p.setOpaque(false);
        p.add(label("Min size (px²):", SUBTEXT));
        minSizeField = field("10");
        p.add(minSizeField);
        p.add(label("Max size:", SUBTEXT));
        p.add(field("Infinity"));
        return p;
    }

    private JPanel buildStep5Controls() {
        JPanel p = new JPanel(new GridLayout(1, 4, 8, 4));
        p.setOpaque(false);
        p.add(label("Scale:", SUBTEXT));
        scaleField = field("1.0");
        p.add(scaleField);
        p.add(label("Unit:", SUBTEXT));
        scaleUnitCombo = new JComboBox<>(new String[]{"px (none)", "nm/pixel", "µm/pixel"});
        scaleUnitCombo.setBackground(BTN_BG);
        scaleUnitCombo.setForeground(TEXT);
        p.add(scaleUnitCombo);
        return p;
    }

    private JPanel buildStep6Controls() {
        JPanel p = flow();
        exportCsvBtn = actionBtn("💾 Export CSV");
        exportReportBtn = actionBtn("📄 Export HTML Report");
        exportCsvBtn.setEnabled(false);
        exportReportBtn.setEnabled(false);
        exportCsvBtn.addActionListener(e -> exportCSV());
        exportReportBtn.addActionListener(e -> exportReport());
        p.add(exportCsvBtn);
        p.add(exportReportBtn);
        return p;
    }

    // ── Footer: run button + progress ─────────────────────────────────────────
    private JPanel buildFooter() {
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setBackground(new Color(22, 22, 28));
        p.setBorder(new EmptyBorder(10, 16, 10, 16));

        runBtn = new JButton("▶▶  Run Full Pipeline");
        runBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        runBtn.setBackground(ACCENT);
        runBtn.setForeground(Color.WHITE);
        runBtn.setFocusPainted(false);
        runBtn.setBorderPainted(false);
        runBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        runBtn.addActionListener(e -> runPipeline());
        runBtn.setPreferredSize(new Dimension(200, 38));

        progressBar = new JProgressBar(0, 6);
        progressBar.setStringPainted(true);
        progressBar.setString("Ready");
        progressBar.setForeground(ACCENT);
        progressBar.setBackground(CARD_BG);

        statusLabel = label("Load an image and press Run.", SUBTEXT);

        JPanel left = new JPanel(new BorderLayout(0, 4));
        left.setOpaque(false);
        left.add(progressBar, BorderLayout.CENTER);
        left.add(statusLabel, BorderLayout.SOUTH);

        p.add(runBtn, BorderLayout.WEST);
        p.add(left, BorderLayout.CENTER);

        // Log area
        logArea = new JTextArea(3, 40);
        logArea.setBackground(new Color(18, 18, 22));
        logArea.setForeground(new Color(100, 200, 100));
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 10));
        logArea.setEditable(false);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setPreferredSize(new Dimension(0, 60));
        logScroll.setBorder(BorderFactory.createLineBorder(new Color(50, 50, 60)));
        p.add(logScroll, BorderLayout.SOUTH);

        return p;
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void browseImage() {
        FileDialog fd = new FileDialog((Frame) null, "Open Image", FileDialog.LOAD);
        fd.setFile("*.tif;*.tiff;*.png;*.jpg;*.bmp");
        fd.setVisible(true);
        if (fd.getFile() != null) {
            currentImage = IJ.openImage(fd.getDirectory() + fd.getFile());
            if (currentImage != null) updatePreview();
        }
    }

    private void useCurrentImage() {
        currentImage = IJ.getImage();
        if (currentImage != null) updatePreview();
        else JOptionPane.showMessageDialog(this, "No image is currently open.",
                "No Image", JOptionPane.WARNING_MESSAGE);
    }

    private void updatePreview() {
        if (currentImage == null) return;
        setStepDone(0);
        log("Loaded: " + currentImage.getTitle() +
            " (" + currentImage.getWidth() + "×" + currentImage.getHeight() + ")");

        // Scale to fit preview
        int pw = 320, ph = 240;
        Image scaled = currentImage.getImage()
                .getScaledInstance(pw, ph, Image.SCALE_SMOOTH);
        previewLabel.setIcon(new ImageIcon(scaled));
        previewLabel.setText(null);
    }

    private void runPipeline() {
        if (currentImage == null) {
            JOptionPane.showMessageDialog(this, "Please load an image first.",
                    "No Image", JOptionPane.WARNING_MESSAGE);
            return;
        }
        configureEngine();
        runBtn.setEnabled(false);
        progressBar.setValue(0);
        progressBar.setString("Running…");

        // Background thread
        new Thread(() -> {
            engine.run(currentImage, new PipelineEngine.ProgressListener() {
                @Override
                public void onStep(int step, String message) {
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(step);
                        progressBar.setString("Step " + step + "/6");
                        statusLabel.setText(message);
                        if (step > 0 && step <= stepIcons.length)
                            setStepDone(step - 1);
                        log("[Step " + step + "] " + message);
                    });
                }

                @Override
                public void onDone(List<ParticleGeometry> particles, ImagePlus outlined) {
                    SwingUtilities.invokeLater(() -> {
                        results = particles;
                        progressBar.setValue(6);
                        progressBar.setString("Done! " + particles.size() + " particles");
                        particleCountLabel.setText(String.valueOf(particles.size()));
                        setStepDone(5);
                        exportCsvBtn.setEnabled(true);
                        exportReportBtn.setEnabled(true);
                        runBtn.setEnabled(true);
                        log("✅ Analysis complete. " + particles.size() + " particles found.");
                    });
                }

                @Override
                public void onError(String message) {
                    SwingUtilities.invokeLater(() -> {
                        log("❌ " + message);
                        statusLabel.setText("Error: " + message);
                        runBtn.setEnabled(true);
                        progressBar.setString("Error");
                    });
                }
            });
        }, "MorphoVision-Pipeline").start();
    }

    private void configureEngine() {
        try { engine.setGaussianSigma(Double.parseDouble(sigmaField.getText())); } catch (Exception ignored) {}
        try { engine.setMinParticleSize(Double.parseDouble(minSizeField.getText())); } catch (Exception ignored) {}
        engine.setDoBackgroundSub(bgSubCheck.isSelected());
        if (threshMethodCombo.getSelectedIndex() == 1) {
            engine.setThresholdMethod("Manual");
            try { engine.setManualThreshold(Integer.parseInt(thresholdField.getText())); } catch (Exception ignored) {}
        } else {
            engine.setThresholdMethod("Otsu");
        }
        int scaleIdx = scaleUnitCombo.getSelectedIndex();
        try {
            double scaleVal = Double.parseDouble(scaleField.getText());
            if (scaleIdx == 1)      engine.setNmPerPixel(scaleVal);
            else if (scaleIdx == 2) engine.setUmPerPixel(scaleVal);
        } catch (Exception ignored) {}
    }

    private void exportCSV() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("particle_analysis.csv"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                GeometryExporter.exportCSV(results, engine.getUnit(), fc.getSelectedFile());
                log("✅ CSV saved: " + fc.getSelectedFile().getName());
                JOptionPane.showMessageDialog(this, "CSV saved successfully!", "Exported", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                log("❌ CSV export error: " + ex.getMessage());
            }
        }
    }

    private void exportReport() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("particle_analysis_report.html"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                List<BufferedImage> charts = new ArrayList<>();
                charts.add(PlotGenerator.areaHistogram(results, engine.getUnit()));
                charts.add(PlotGenerator.circularityHistogram(results));
                charts.add(PlotGenerator.aspectRatioHistogram(results));
                charts.add(PlotGenerator.scatterPlot(results, engine.getUnit()));

                double scaleVal = 1.0;
                try { scaleVal = Double.parseDouble(scaleField.getText()); } catch (Exception ignored) {}

                GeometryExporter.exportHTMLReport(results, charts,
                        currentImage != null ? currentImage.getTitle() : "Unknown",
                        engine.getUnit(), scaleVal, results.size(), fc.getSelectedFile());

                log("✅ Report saved: " + fc.getSelectedFile().getName());
                // Open in browser
                try { Desktop.getDesktop().open(fc.getSelectedFile()); } catch (Exception ignored) {}
                JOptionPane.showMessageDialog(this, "HTML report saved and opened in browser!", "Exported", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                log("❌ Report export error: " + ex.getMessage());
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setStepDone(int i) {
        if (i >= 0 && i < stepIcons.length)
            stepIcons[i].setText("✅");
    }

    private void log(String msg) {
        logArea.append(msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private JPanel dark(JPanel p) { p.setBackground(BG); return p; }

    private JPanel card(JPanel p) { p.setBackground(CARD_BG); return p; }

    private JPanel flow() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        p.setOpaque(false);
        return p;
    }

    private JLabel label(String txt, Color c) {
        JLabel l = new JLabel(txt);
        l.setForeground(c);
        return l;
    }

    private JTextField field(String val) {
        JTextField f = new JTextField(val, 7);
        f.setBackground(BTN_BG);
        f.setForeground(TEXT);
        f.setCaretColor(TEXT);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(80, 80, 100)),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));
        return f;
    }

    private JButton actionBtn(String txt) {
        JButton b = new JButton(txt);
        b.setBackground(BTN_BG);
        b.setForeground(TEXT);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setBackground(ACCENT); }
            public void mouseExited(MouseEvent e)  { b.setBackground(BTN_BG); }
        });
        return b;
    }
}
