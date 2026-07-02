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
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("serial")
public class AboutDialog extends JDialog
{
	/**
	 * Width to wrap the bug-report line and the support panel to. Chosen to keep the dialog close to its original overall size (image
	 * width + a modest text column), rather than the much wider {@link SupportPanel#defaultContentWidth} used for the roomier map canvas
	 * overlay.
	 */
	private static final int textColumnWidth = 320;

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
		JLabel text = new JLabel("<html>" + Translation.get("about.version", MapSettings.currentVersion) + "<html>");
		rightPanel.add(text);

		rightPanel.add(new JLabel(" "));

		// The bug-report line comes before the support panel, since reporting a problem is a more immediate need than the support ask.
		rightPanel.add(createBugReportRow());

		rightPanel.add(new JLabel(" "));
		rightPanel.add(new SupportPanel(textColumnWidth, true));

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

	/**
	 * A centered, word-wrapped line ending in an inline "here" hyperlink (with the URL shown on hover, like other links), rather than a
	 * separate raw-URL label below plain text.
	 */
	private static JComponent createBugReportRow()
	{
		List<JComponent> items = new ArrayList<>();
		for (String word : Translation.get("about.bugReport").trim().split("\\s+"))
		{
			if (!word.isEmpty())
			{
				items.add(new JLabel(word));
			}
		}
		items.add(SwingHelper.createHyperlink(Translation.get("about.bugReportLinkText"), "https://github.com/jeheydorn/nortantis/issues"));
		return new WrappedTextRow(textColumnWidth, 4, 4, items);
	}

	/**
	 * A centered {@link WrapLayout} flow of words/links wrapped to a fixed width, with fixed preferred/minimum/maximum size so it behaves
	 * correctly as a direct child of a BoxLayout container. Without the fixed maximum size, a null-layout JPanel defaults to
	 * (Integer.MAX_VALUE, Integer.MAX_VALUE), which breaks BoxLayout's size calculations (see SupportPanel.getMaximumSize for the same
	 * fix, needed there for the same reason).
	 */
	private static class WrappedTextRow extends JPanel
	{
		private final Dimension fixedPreferredSize;

		WrappedTextRow(int width, int hgap, int vgap, List<JComponent> items)
		{
			setOpaque(false);
			setLayout(new WrapLayout(FlowLayout.CENTER, hgap, vgap));
			for (JComponent item : items)
			{
				add(item);
			}
			setSize(width, 1);
			fixedPreferredSize = new Dimension(width, super.getPreferredSize().height);
		}

		@Override
		public Dimension getPreferredSize()
		{
			return fixedPreferredSize;
		}

		@Override
		public Dimension getMinimumSize()
		{
			return fixedPreferredSize;
		}

		@Override
		public Dimension getMaximumSize()
		{
			return fixedPreferredSize;
		}
	}
}
