package nortantis.swing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

import nortantis.MapSettings;
import nortantis.util.AssetsPath;
import nortantis.util.ImageHelper;
import nortantis.util.Logger;

@SuppressWarnings("serial")
public class AboutDialog extends JDialog
{
	public AboutDialog(MainWindow mainWindow)
	{
		super(mainWindow, "About Nortantis", Dialog.ModalityType.APPLICATION_MODAL);
		setResizable(false);
		setLayout(new BorderLayout());
		JPanel content = new JPanel();
		add(content, BorderLayout.CENTER);
		content.setLayout(new BorderLayout());
		content.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		BufferedImage nortantisImage = ImageHelper.read(Paths.get(AssetsPath.getInstallPath(), "internal", "taskbar icon medium size.png").toString());
		content.add(new ImagePanel(nortantisImage), BorderLayout.WEST);

		JPanel rightPanel = new JPanel();
		rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
		rightPanel.setPreferredSize(new Dimension(nortantisImage.getWidth(), nortantisImage.getHeight()));
		JLabel text = new JLabel("<html>" + "Nortantis version " + MapSettings.currentVersion + "." + "" + "<html>");
		rightPanel.add(text);
		
		rightPanel.add(new JLabel(" "));

		rightPanel.add(new JLabel("<html>If you have encountered a bug and wish to report it, you may do so at the Nortantis project's"
				+ " GitHub issue tracker here: </html>"));
		rightPanel.add(createHyperlink("github.com/jeheydorn/nortantis/issues", "https://github.com/jeheydorn/nortantis/issues"));
		
		rightPanel.add(new JLabel(" "));
		rightPanel.add(new JLabel("<html>If you have enjoyed Nortantis and wish to support it, and you like clean, happy, fantasy "
				+ "romance novels, then please consider purchasing one of my books listed at:</html>"));
		rightPanel.add(createHyperlink("jandjheydorn.com/", "https://jandjheydorn.com/"));

		rightPanel.add(Box.createVerticalGlue());

		content.add(rightPanel, BorderLayout.EAST);
		
		JPanel bottomPanel = new JPanel();
		content.add(bottomPanel, BorderLayout.SOUTH);
		bottomPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
		JButton closeButton = new JButton("<html><u>C</u>lose</html>");
		closeButton.setMnemonic(KeyEvent.VK_C);
		closeButton.addActionListener(new ActionListener()
		{	
			@Override
			public void actionPerformed(ActionEvent e)
			{
				dispose();
			}
		});
		bottomPanel.add(closeButton);
		
		pack();
	}

	private JLabel createHyperlink(String text, String URL)
	{
		JLabel link = new JLabel(text);
		link.setForeground(new Color(26, 113, 228));
		link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		link.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				try
				{
					Desktop.getDesktop().browse(new URI(URL));
				}
				catch (IOException | URISyntaxException ex)
				{
					Logger.printError("Error while trying to open URL: " + URL, ex);
				}
			}
		});
		return link;
	}
}
