package nortantis.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.image.BufferedImage;
import java.nio.file.Paths;

import javax.swing.JDialog;
import javax.swing.JLabel;

import nortantis.MapSettings;
import nortantis.util.AssetsPath;
import nortantis.util.ImageHelper;

public class AboutNortantisDialog extends JDialog
{
	public AboutNortantisDialog(MainWindow mainWindow)
	{
		super(mainWindow, "About Nortantis", Dialog.ModalityType.APPLICATION_MODAL);
		setResizable(false);
		setLayout(new BorderLayout());
		BufferedImage nortantisImage = ImageHelper.read(Paths.get(AssetsPath.get(), "internal", "taskbar icon medium size.png").toString());
		add(new ImagePanel(nortantisImage), BorderLayout.WEST);
		JLabel text = new JLabel("Nortantis version " + MapSettings.currentVersion + ".");
		add(text, BorderLayout.EAST);
		pack();
	}
}
