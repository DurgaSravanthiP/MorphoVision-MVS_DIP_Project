package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;
import ij.plugin.filter.ParticleAnalyzer;
import ij.io.Opener;
import ij.process.AutoThresholder.Method;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/**
 * MorphoWizard: Unified Research Dashboard for MorphoVision.
 */
public class MorphoWizard implements PlugIn, ActionListener {
    private JFrame frame;
    private JPanel mainPanel;
    private CardLayout cardLayout;
    
    private ImagePlus currentImp;
    private JLabel imageLabel;
    
    public void run(String arg) {
        frame = new JFrame("MorphoVision Research Wizard");
        frame.setSize(1000, 700);
        frame.setLayout(new BorderLayout());
        
        // Sidebar
        JPanel sidebar = new JPanel();
        sidebar.setPreferredSize(new Dimension(200, 700));
        sidebar.setBackground(MorphoTheme.BACKGROUND);
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        
        String[] steps = {"1. Load Image", "2. Pre-process", "3. Analyze", "4. Results"};
        for (String step : steps) {
            JButton btn = new JButton(step);
            btn.setAlignmentX(Component.CENTER_ALIGNMENT);
            btn.setMaximumSize(new Dimension(180, 40));
            btn.setBackground(MorphoTheme.ACCENT);
            btn.setForeground(Color.WHITE);
            sidebar.add(Box.createVerticalStrut(10));
            sidebar.add(btn);
        }
        
        frame.add(sidebar, BorderLayout.WEST);
        
        // Main Content
        mainPanel = new JPanel();
        cardLayout = new CardLayout();
        mainPanel.setLayout(cardLayout);
        
        mainPanel.add(createLoadPanel(), "LOAD");
        mainPanel.add(createProcessPanel(), "PROCESS");
        
        frame.add(mainPanel, BorderLayout.CENTER);
        
        MorphoTheme.apply(frame);
        frame.setVisible(true);
    }
    
    private JPanel createLoadPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        JButton loadBtn = new JButton("Open Microparticle Image");
        loadBtn.addActionListener(e -> {
            Opener opener = new Opener();
            currentImp = opener.openImage("");
            if (currentImp != null) {
                currentImp.show();
                cardLayout.show(mainPanel, "PROCESS");
            }
        });
        p.add(loadBtn);
        return p;
    }
    
    private JPanel createProcessPanel() {
        JPanel p = new JPanel(new BorderLayout());
        JLabel header = new JLabel("Step 2: Adjust Threshold", JLabel.CENTER);
        header.setFont(MorphoTheme.TITLE_FONT);
        header.setForeground(MorphoTheme.ACCENT);
        p.add(header, BorderLayout.NORTH);
        
        JButton analyzeBtn = new JButton("Run MorphoAnalysis");
        analyzeBtn.setBackground(MorphoTheme.ACCENT);
        analyzeBtn.setForeground(Color.WHITE);
        analyzeBtn.addActionListener(e -> runAnalysis());
        p.add(analyzeBtn, BorderLayout.SOUTH);
        
        return p;
    }
    
    private void runAnalysis() {
        if (currentImp == null) return;
        
        // Auto-threshold if not already set
        ImageProcessor ip = currentImp.getProcessor();
        if (ip.getMinThreshold() == ImageProcessor.NO_THRESHOLD) {
            ip.setAutoThreshold(Method.Default, true, ImageProcessor.RED_LUT);
        }
        
        int options = ParticleAnalyzer.SHOW_RESULTS + ParticleAnalyzer.EXCLUDE_EDGE_PARTICLES + 
                      ParticleAnalyzer.SHOW_CURVATURE + ParticleAnalyzer.SHOW_FRACTAL +
                      ParticleAnalyzer.ADD_TO_MANAGER;
        int measurements = Measurements.AREA + Measurements.PERIMETER + Measurements.CIRCULARITY;
        
        ResultsTable rt = new ResultsTable();
        ParticleAnalyzer pa = new ParticleAnalyzer(options, measurements, rt, 10.0, 999999.0);
        pa.analyze(currentImp);
        
        rt.show("Results");
        
        // Show Interactive Plot
        MorphoPlotter plotter = new MorphoPlotter("Particle Distribution", rt);
        plotter.show();
    }

    public void actionPerformed(ActionEvent e) {
        // Handle sidebar clicks
    }
}
