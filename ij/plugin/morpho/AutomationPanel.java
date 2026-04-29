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
 * Left side has two tabs: Preview | Charts (charts appear after analysis).
 */
public class AutomationPanel extends JInternalFrame {

    // ── Colours ─────────────────────────────────────────────────────────────
    private static final Color BG       = new Color(28, 28, 34);
    private static final Color PANEL_BG = new Color(38, 38, 46);
    private static final Color CARD_BG  = new Color(48, 48, 58);
    private static final Color ACCENT   = new Color(45, 110, 200);
    private static final Color SUCCESS  = new Color(40, 160, 80);
    private static final Color TEXT     = new Color(220, 220, 230);
    private static final Color SUBTEXT  = new Color(140, 140, 155);
    private static final Color BTN_BG   = new Color(55, 55, 65);

    // ── State ────────────────────────────────────────────────────────────────
    private ImagePlus              currentImage;
    private List<ParticleGeometry> results   = new ArrayList<>();
    private List<BufferedImage>    charts    = new ArrayList<>();
    private final PipelineEngine   engine    = new PipelineEngine();

    // ── UI ────────────────────────────────────────────────────────────────────
    private JLabel       previewLabel;
    private JLabel       particleCountLabel;
    private JProgressBar progressBar;
    private JLabel       statusLabel;
    private JTextArea    logArea;
    private JTabbedPane  leftTabs;
    private JPanel       chartsPanel;

    private JLabel[] stepIcons;
    private static final String[] STEP_NAMES = {
        "Load Image", "Pre-process", "Threshold",
        "Detect Particles", "Measure Geometry", "Export Results"
    };

    private JTextField  sigmaField, minSizeField, thresholdField, scaleField;
    private JCheckBox   bgSubCheck;
    private JComboBox<String> threshMethodCombo, scaleUnitCombo;
    private JButton     runBtn, exportCsvBtn, exportReportBtn;

    // ── Constructor ──────────────────────────────────────────────────────────
    public AutomationPanel() {
        super("⚡ Automate Your Work — Particle Analysis Pipeline",
              true, true, true, true);
        setBackground(BG);
        buildUI();
        pack();
    }

    // ── Build UI ─────────────────────────────────────────────────────────────
    private void buildUI() {
        JPanel root = dark(new JPanel(new BorderLayout(0, 0)));
        setContentPane(root);
        root.add(buildHeader(),  BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildLeftPanel(), buildRightPanel());
        split.setDividerLocation(400);
        split.setDividerSize(4);
        split.setBackground(BG);
        root.add(split, BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);

        setPreferredSize(new Dimension(1050, 700));
    }

    // ── Header ───────────────────────────────────────────────────────────────
    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(20, 50, 110));
        p.setBorder(new EmptyBorder(12, 20, 12, 20));
        JLabel title = new JLabel("⚡  Automated Particle Analysis Pipeline");
        title.setFont(new Font("SansSerif", Font.BOLD, 15));
        title.setForeground(Color.WHITE);
        JLabel sub = new JLabel("One click → detect all particles → measure geometry → view charts → export report");
        sub.setFont(new Font("SansSerif", Font.PLAIN, 11));
        sub.setForeground(new Color(180, 200, 240));
        JPanel txt = new JPanel(new GridLayout(2, 1, 0, 2));
        txt.setOpaque(false);
        txt.add(title); txt.add(sub);
        p.add(txt, BorderLayout.CENTER);
        return p;
    }

    // ── Left panel: tabs (Preview | Charts) + count ──────────────────────────
    private JPanel buildLeftPanel() {
        JPanel p = dark(new JPanel(new BorderLayout(0, 8)));
        p.setBorder(new EmptyBorder(10, 10, 8, 5));

        // Preview tab
        previewLabel = new JLabel("No image loaded", SwingConstants.CENTER);
        previewLabel.setForeground(SUBTEXT);
        previewLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
        previewLabel.setBorder(BorderFactory.createDashedBorder(
                new Color(80, 80, 100), 2, 4, 2, false));
        JScrollPane previewScroll = new JScrollPane(previewLabel);
        previewScroll.setBackground(CARD_BG);
        previewScroll.setBorder(null);

        // Charts tab – 2×2 grid of chart images
        chartsPanel = dark(new JPanel(new GridLayout(2, 2, 6, 6)));
        chartsPanel.setBorder(new EmptyBorder(4, 4, 4, 4));
        addPlaceholderCharts();
        JScrollPane chartsScroll = new JScrollPane(chartsPanel);
        chartsScroll.setBackground(BG);
        chartsScroll.setBorder(null);

        leftTabs = new JTabbedPane();
        leftTabs.setBackground(CARD_BG);
        leftTabs.setForeground(TEXT);
        leftTabs.addTab("📷 Preview",  previewScroll);
        leftTabs.addTab("📊 Charts",   chartsScroll);
        p.add(leftTabs, BorderLayout.CENTER);

        // Particle count card
        JPanel countCard = card(new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 6)));
        particleCountLabel = new JLabel("–");
        particleCountLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        particleCountLabel.setForeground(ACCENT);
        JLabel countLbl = new JLabel("particles detected");
        countLbl.setForeground(SUBTEXT);
        countLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        countCard.add(particleCountLabel);
        countCard.add(countLbl);
        p.add(countCard, BorderLayout.SOUTH);
        return p;
    }

    private void addPlaceholderCharts() {
        chartsPanel.removeAll();
        String[] names = {"Area Distribution", "Circularity Distribution",
                          "Aspect Ratio Distribution", "Area vs Circularity"};
        for (String name : names) {
            JLabel lbl = new JLabel(name + "\n(run pipeline to generate)", SwingConstants.CENTER);
            lbl.setForeground(SUBTEXT);
            lbl.setFont(new Font("SansSerif", Font.ITALIC, 11));
            lbl.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 85)));
            chartsPanel.add(lbl);
        }
        chartsPanel.revalidate();
        chartsPanel.repaint();
    }

    private void showCharts() {
        chartsPanel.removeAll();
        for (BufferedImage chart : charts) {
            JLabel img = new JLabel(new ImageIcon(chart.getScaledInstance(
                    180, 130, Image.SCALE_SMOOTH)));
            img.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 85)));
            chartsPanel.add(img);
        }
        chartsPanel.revalidate();
        chartsPanel.repaint();
        // Auto-switch to charts tab
        leftTabs.setSelectedIndex(1);
    }

    // ── Right panel: pipeline steps ──────────────────────────────────────────
    private JScrollPane buildRightPanel() {
        JPanel p = dark(new JPanel());
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(10, 5, 8, 10));

        stepIcons = new JLabel[STEP_NAMES.length];
        p.add(buildStepPanel(0, buildStep1Controls()));
        p.add(buildStepPanel(1, buildStep2Controls()));
        p.add(buildStepPanel(2, buildStep3Controls()));
        p.add(buildStepPanel(3, buildStep4Controls()));
        p.add(buildStepPanel(4, buildStep5Controls()));
        p.add(buildStepPanel(5, buildStep6Controls()));

        JScrollPane scroll = new JScrollPane(p);
        scroll.setBorder(null);
        scroll.setBackground(BG);
        return scroll;
    }

    private JPanel buildStepPanel(int idx, JPanel controls) {
        JPanel outer = card(new JPanel(new BorderLayout(0, 4)));
        outer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 999));
        outer.setBorder(new CompoundBorder(
                new EmptyBorder(0, 0, 8, 0),
                new CompoundBorder(
                    BorderFactory.createLineBorder(new Color(60, 60, 80), 1, true),
                    new EmptyBorder(9, 12, 9, 12))));

        JPanel hdr = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        hdr.setOpaque(false);
        stepIcons[idx] = new JLabel("⬜");
        JLabel nm = new JLabel("  " + (idx + 1) + ".  " + STEP_NAMES[idx]);
        nm.setFont(new Font("SansSerif", Font.BOLD, 12));
        nm.setForeground(TEXT);
        hdr.add(stepIcons[idx]);
        hdr.add(nm);
        outer.add(hdr, BorderLayout.NORTH);
        if (controls != null) outer.add(controls, BorderLayout.CENTER);
        return outer;
    }

    // ── Step controls ─────────────────────────────────────────────────────────
    private JPanel buildStep1Controls() {
        JPanel p = flow();
        JButton browse = btn("📂  Browse Image");
        browse.addActionListener(e -> browseImage());
        JButton useCur = btn("Use Current");
        useCur.addActionListener(e -> useCurrentImage());
        p.add(browse);
        p.add(label("  or  ", SUBTEXT));
        p.add(useCur);
        return p;
    }

    private JPanel buildStep2Controls() {
        JPanel p = new JPanel(new GridLayout(2, 2, 8, 4));
        p.setOpaque(false);
        p.add(label("Gaussian σ (0=skip):", SUBTEXT));
        sigmaField = field("1.0");
        p.add(sigmaField);
        bgSubCheck = new JCheckBox("Background Subtraction (for uneven lighting)", false);
        bgSubCheck.setForeground(SUBTEXT);
        bgSubCheck.setOpaque(false);
        bgSubCheck.setFont(new Font("SansSerif", Font.PLAIN, 10));
        JLabel note = label("OFF by default — enable only for uneven illumination images", SUBTEXT);
        note.setFont(new Font("SansSerif", Font.ITALIC, 9));
        p.add(bgSubCheck); p.add(note);
        return p;
    }

    private JPanel buildStep3Controls() {
        JPanel p = new JPanel(new GridLayout(2, 2, 8, 4));
        p.setOpaque(false);
        p.add(label("Method:", SUBTEXT));
        threshMethodCombo = new JComboBox<>(new String[]{"Otsu (Auto)", "Manual"});
        threshMethodCombo.setBackground(BTN_BG); threshMethodCombo.setForeground(TEXT);
        p.add(threshMethodCombo);
        p.add(label("Manual value (if Manual):", SUBTEXT));
        thresholdField = field("128");
        p.add(thresholdField);
        return p;
    }

    private JPanel buildStep4Controls() {
        JPanel p = new JPanel(new GridLayout(1, 4, 8, 4));
        p.setOpaque(false);
        p.add(label("Min size (px²):", SUBTEXT));
        minSizeField = field("1");
        p.add(minSizeField);
        p.add(label("Watershed: AUTO ✅", new Color(40, 180, 90)));
        p.add(label("(splits touching particles)", SUBTEXT));
        return p;
    }

    private JPanel buildStep5Controls() {
        JPanel p = new JPanel(new GridLayout(1, 4, 8, 4));
        p.setOpaque(false);
        p.add(label("Scale factor:", SUBTEXT));
        scaleField = field("1.0");
        p.add(scaleField);
        p.add(label("Unit:", SUBTEXT));
        scaleUnitCombo = new JComboBox<>(new String[]{"px (no calibration)", "nm/pixel", "µm/pixel"});
        scaleUnitCombo.setBackground(BTN_BG); scaleUnitCombo.setForeground(TEXT);
        p.add(scaleUnitCombo);
        return p;
    }

    private JPanel buildStep6Controls() {
        JPanel p = flow();
        exportCsvBtn    = btn("💾 Export CSV");
        exportReportBtn = btn("📄 Export HTML Report");
        exportCsvBtn.setEnabled(false);
        exportReportBtn.setEnabled(false);
        exportCsvBtn.addActionListener(e -> exportCSV());
        exportReportBtn.addActionListener(e -> exportReport());
        p.add(exportCsvBtn); p.add(exportReportBtn);
        return p;
    }

    // ── Footer ────────────────────────────────────────────────────────────────
    private JPanel buildFooter() {
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setBackground(new Color(22, 22, 28));
        p.setBorder(new EmptyBorder(8, 14, 8, 14));

        runBtn = new JButton("▶▶  Run Full Pipeline");
        runBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        runBtn.setBackground(ACCENT);
        runBtn.setForeground(Color.WHITE);
        runBtn.setFocusPainted(false);
        runBtn.setBorderPainted(false);
        runBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        runBtn.setPreferredSize(new Dimension(210, 36));
        runBtn.addActionListener(e -> runPipeline());

        progressBar = new JProgressBar(0, 6);
        progressBar.setStringPainted(true);
        progressBar.setString("Ready — load an image and press Run");
        progressBar.setForeground(ACCENT);
        progressBar.setBackground(CARD_BG);

        statusLabel = label("Tip: background subtraction is OFF — only enable for uneven-lit images.", SUBTEXT);
        statusLabel.setFont(new Font("SansSerif", Font.ITALIC, 10));

        JPanel mid = new JPanel(new BorderLayout(0, 3));
        mid.setOpaque(false);
        mid.add(progressBar, BorderLayout.CENTER);
        mid.add(statusLabel, BorderLayout.SOUTH);

        p.add(runBtn, BorderLayout.WEST);
        p.add(mid,    BorderLayout.CENTER);

        logArea = new JTextArea(3, 40);
        logArea.setBackground(new Color(18, 18, 22));
        logArea.setForeground(new Color(100, 200, 100));
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 10));
        logArea.setEditable(false);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setPreferredSize(new Dimension(0, 58));
        logScroll.setBorder(BorderFactory.createLineBorder(new Color(50, 50, 60)));
        p.add(logScroll, BorderLayout.SOUTH);
        return p;
    }

    // ── Actions ───────────────────────────────────────────────────────────────
    private void browseImage() {
        FileDialog fd = new FileDialog((Frame) null, "Open Image", FileDialog.LOAD);
        fd.setVisible(true);
        if (fd.getFile() != null) {
            currentImage = IJ.openImage(fd.getDirectory() + fd.getFile());
            if (currentImage != null) { updatePreview(); setStepDone(0); }
        }
    }

    private void useCurrentImage() {
        currentImage = IJ.getImage();
        if (currentImage != null) { updatePreview(); setStepDone(0); }
        else showWarn("No image is currently open in MorphoVision.");
    }

    private void updatePreview() {
        int pw = 360, ph = 260;
        Image scaled = currentImage.getImage().getScaledInstance(pw, ph, Image.SCALE_SMOOTH);
        previewLabel.setIcon(new ImageIcon(scaled));
        previewLabel.setText(null);
        log("Loaded: " + currentImage.getTitle() +
            " [" + currentImage.getWidth() + "×" + currentImage.getHeight() + "]");
        leftTabs.setSelectedIndex(0);
    }

    private void runPipeline() {
        if (currentImage == null) { showWarn("Please load an image first."); return; }
        configureEngine();
        runBtn.setEnabled(false);
        progressBar.setValue(0);
        progressBar.setString("Running pipeline…");
        charts.clear();
        addPlaceholderCharts();

        new Thread(() -> engine.run(currentImage, new PipelineEngine.ProgressListener() {
            @Override public void onStep(int step, String msg) {
                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(step);
                    progressBar.setString("Step " + step + "/6");
                    statusLabel.setText(msg);
                    if (step > 0 && step <= stepIcons.length) setStepDone(step - 1);
                    log("[" + step + "] " + msg);
                });
            }
            @Override public void onDone(List<ParticleGeometry> particles, ImagePlus outlined) {
                SwingUtilities.invokeLater(() -> {
                    results = particles;
                    progressBar.setValue(6);
                    progressBar.setString("Done!  " + particles.size() + " particles detected");
                    particleCountLabel.setText(String.valueOf(particles.size()));
                    setStepDone(5);
                    exportCsvBtn.setEnabled(true);
                    exportReportBtn.setEnabled(true);
                    runBtn.setEnabled(true);
                    log("✅ " + particles.size() + " particles measured.");

                    // Generate and display charts immediately
                    generateAndShowCharts();
                });
            }
            @Override public void onError(String msg) {
                SwingUtilities.invokeLater(() -> {
                    log("❌ " + msg);
                    statusLabel.setText("Error: " + msg);
                    runBtn.setEnabled(true);
                    progressBar.setString("Error — check log");
                });
            }
        }), "MorphoVision-Pipeline").start();
    }

    private void generateAndShowCharts() {
        if (results.isEmpty()) return;
        charts.clear();
        try {
            charts.add(PlotGenerator.areaHistogram(results, engine.getUnit()));
            charts.add(PlotGenerator.circularityHistogram(results));
            charts.add(PlotGenerator.aspectRatioHistogram(results));
            charts.add(PlotGenerator.scatterPlot(results, engine.getUnit()));
            showCharts();
            log("📊 Charts generated — see Charts tab.");
        } catch (Exception e) {
            log("⚠ Chart generation error: " + e.getMessage());
        }
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
        try {
            double sv = Double.parseDouble(scaleField.getText());
            int si = scaleUnitCombo.getSelectedIndex();
            if (si == 1)      engine.setNmPerPixel(sv);
            else if (si == 2) engine.setUmPerPixel(sv);
        } catch (Exception ignored) {}
    }

    private void exportCSV() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("particle_analysis.csv"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                GeometryExporter.exportCSV(results, engine.getUnit(), fc.getSelectedFile());
                log("✅ CSV saved: " + fc.getSelectedFile().getName());
                JOptionPane.showMessageDialog(this, "CSV saved!", "Done", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) { log("❌ " + ex.getMessage()); }
        }
    }

    private void exportReport() {
        if (charts.isEmpty()) generateAndShowCharts();
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("particle_analysis_report.html"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                double sv = 1.0;
                try { sv = Double.parseDouble(scaleField.getText()); } catch (Exception ignored) {}
                GeometryExporter.exportHTMLReport(results, charts,
                        currentImage != null ? currentImage.getTitle() : "Unknown",
                        engine.getUnit(), sv, results.size(), fc.getSelectedFile());
                log("✅ Report saved: " + fc.getSelectedFile().getName());
                try { Desktop.getDesktop().open(fc.getSelectedFile()); } catch (Exception ignored) {}
            } catch (Exception ex) { log("❌ " + ex.getMessage()); }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private void setStepDone(int i) {
        if (i >= 0 && i < stepIcons.length) stepIcons[i].setText("✅");
    }

    private void log(String msg) {
        logArea.append(msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void showWarn(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Warning", JOptionPane.WARNING_MESSAGE);
    }

    private JPanel dark(JPanel p) { p.setBackground(BG); return p; }
    private JPanel card(JPanel p) { p.setBackground(CARD_BG); return p; }

    private JPanel flow() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        p.setOpaque(false); return p;
    }

    private JLabel label(String t, Color c) {
        JLabel l = new JLabel(t); l.setForeground(c); return l;
    }

    private JTextField field(String v) {
        JTextField f = new JTextField(v, 7);
        f.setBackground(BTN_BG); f.setForeground(TEXT); f.setCaretColor(TEXT);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(80, 80, 100)),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));
        return f;
    }

    private JButton btn(String t) {
        JButton b = new JButton(t);
        b.setBackground(BTN_BG); b.setForeground(TEXT);
        b.setFocusPainted(false); b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setBackground(ACCENT); }
            public void mouseExited(MouseEvent e)  { b.setBackground(BTN_BG); }
        });
        return b;
    }
}
