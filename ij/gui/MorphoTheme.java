package ij.gui;
import java.awt.*;

/**
 * Modern Light Theme for MorphoVision.
 */
public class MorphoTheme {
    public static final Color BACKGROUND = new Color(245, 246, 247);
    public static final Color TOOLBAR_BG = new Color(255, 255, 255);
    public static final Color ACCENT = new Color(0xBB, 0x37, 0x37);
    public static final Color TEXT_PRIMARY = new Color(33, 37, 41);
    public static final Color TEXT_SECONDARY = new Color(108, 117, 125);
    
    public static final Font MAIN_FONT = new Font("SansSerif", Font.PLAIN, 13);
    public static final Font TITLE_FONT = new Font("Jolly Lodger", Font.BOLD, 24);
    
    public static void apply(Component c) {
        c.setFont(MAIN_FONT);
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                apply(child);
            }
        }
    }
}
