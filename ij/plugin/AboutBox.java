package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.io.*;
import java.net.URL;
import java.awt.image.*;

	/** This plugin implements the Help/About MorphoVision command.
	*/
	public class AboutBox implements PlugIn {
		private static final int SMALL_FONT=20, LARGE_FONT=55;
		private static final Color TEXT_COLOR = new Color(0xBB, 0x37, 0x37);

	public void run(String arg) {
		System.gc();
		int lines = 7;
		String[] text = new String[lines];
		text[0] = "MorphoVision System "+ImageJ.VERSION+ImageJ.BUILD;
		text[1] = "Advanced Morphological Analysis";
		text[2] = "Research Publication Tool";
		text[3] = "https://morphovision.org";
		text[4] = "Java "+System.getProperty("java.version")+(IJ.is64Bit()?" (64-bit)":" (32-bit)");
		text[5] = IJ.freeMemory();
		text[6] = "MorphoVision is open source";
		ImageProcessor ip = null;
		ImageJ ij = IJ.getInstance();
		if (ij==null)
			return;
		URL url = ij.getClass().getResource("/about.jpg");
		if (url!=null) {
			Image img = null;
			try {img = ij.createImage((ImageProducer)url.getContent());}
			catch(Exception e) {}
			if (img!=null) {
				ImagePlus sImp = new ImagePlus("", img);
				ip = sImp.getProcessor();
			}
		}
		if (ip==null) 
			ip =  new ColorProcessor(55,45);
		ip = ip.resize(ip.getWidth()*6, ip.getHeight()*6);
		ImagePlus imp = new ImagePlus("About MorphoVision", ip);
		int width = imp.getWidth();
		Overlay overlay = new Overlay();
		Font font = new Font("Jolly Lodger", Font.PLAIN, LARGE_FONT);
		int y  = 60;
		add(text[0], width-20, y, font, TextRoi.RIGHT, overlay);
		int xcenter = 410;
		font = new Font("SansSerif", Font.PLAIN, SMALL_FONT);
		y += 45;
		add(text[1], xcenter, y, font, TextRoi.CENTER, overlay);
		y += 27;
		add(text[2], xcenter, y, font, TextRoi.CENTER, overlay);
		y += 27;
		add(text[3], xcenter, y, font, TextRoi.CENTER, overlay);
		y += 27;
		add(text[4], xcenter, y, font, TextRoi.CENTER, overlay);
		if (IJ.maxMemory()>0L) {
			y += 27;
			add(text[5], xcenter, y, font, TextRoi.CENTER, overlay);
		}
		add(text[6], width-10, ip.getHeight()-10, font, TextRoi.RIGHT, overlay);
		imp.setOverlay(overlay);
		ImageWindow.centerNextImage();
		imp.show();
	}
	
	private void add(String text, int x, int y, Font font, int justification, Overlay overlay) {
		TextRoi roi = new TextRoi(text, x, y, font);
		roi.setStrokeColor(TEXT_COLOR);
		roi.setJustification(justification);
		overlay.add(roi);
	}

}
