package nortantis.swing;

import nortantis.MapSettings;
import nortantis.platform.awt.AwtBridge;
import nortantis.swing.translation.Translation;
import nortantis.util.Assets;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Paths;

@SuppressWarnings("serial")
public class AboutDialog extends JDialog
{
	public AboutDialog(MainWindow mainWindow)
	{
		super(mainWindow, Translation.get("about.title"), Dialog.ModalityType.APPLICATION_MODAL);
		setResizable(false);
		setLayout(new BorderLayout());
		JPanel content = new JPanel();
		add(content, BorderLayout.CENTER);
		content.setLayout(new BorderLayout());
		content.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		BufferedImage nortantisImage = AwtBridge.toBufferedImage(Assets.readImage(Paths.get(Assets.getAssetsPath(), "internal", "taskbar icon medium size.png").toString()));
		ImagePanel nortantisImagePanel = new ImagePanel(nortantisImage)
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				Graphics2D g2d = (Graphics2D) g;

				// Set rendering hints
				g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
				super.paintComponent(g);
			}
		};

		content.add(nortantisImagePanel, BorderLayout.WEST);

		JPanel rightPanel = new JPanel();
		rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
		rightPanel.setPreferredSize(new Dimension(nortantisImage.getWidth(), nortantisImage.getHeight()));
		JLabel text = new JLabel("<html>" + Translation.get("about.version", MapSettings.currentVersion) + "<html>");
		rightPanel.add(text);

		rightPanel.add(new JLabel(" "));

		rightPanel.add(new JLabel("<html>" + Translation.get("about.bugReport") + " </html>"));
		rightPanel.add(SwingHelper.createHyperlink("github.com/jeheydorn/nortantis/issues", "https://github.com/jeheydorn/nortantis/issues"));

		rightPanel.add(new JLabel(" "));
		rightPanel.add(new JLabel("<html>" + Translation.get("about.support") + "</html>"));
		rightPanel.add(SwingHelper.createHyperlink("jandjheydorn.com/", "https://jandjheydorn.com/"));

		rightPanel.add(Box.createVerticalGlue());

		content.add(rightPanel, BorderLayout.EAST);

		JPanel bottomPanel = new JPanel();
		content.add(bottomPanel, BorderLayout.SOUTH);
		bottomPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
		JButton closeButton = new JButton(Translation.get("about.close"));
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

}
