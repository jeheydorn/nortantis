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

		content.add(createRightPanel(textColumnWidth), BorderLayout.EAST);

		JPanel bottomPanel = new JPanel();
		content.add(bottomPanel, BorderLayout.SOUTH);
		bottomPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
		// Extra breathing room above the Close button so the support panel's links above it don't crowd the button.
		bottomPanel.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
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
	 * Builds the version/bug-report/support-panel column at a fixed width, with each row's vertical position computed and set directly
	 * (absolute positioning, no layout manager) rather than via BoxLayout. BoxLayout's alignmentX - tried multiple ways, including
	 * wrapping the version label in its own fixed-size, explicitly left-anchored sub-panel - never actually left-aligned the version
	 * label; it kept centering. Positioning everything explicitly sidesteps whatever was causing that.
	 */
	private static JComponent createRightPanel(int width)
	{
		int rowSpacing = 10;
		int y = 0;

		JLabel versionLabel = new JLabel(Translation.get("about.version", MapSettings.currentVersion));
		Dimension versionSize = versionLabel.getPreferredSize();
		versionLabel.setBounds(0, y, versionSize.width, versionSize.height);
		y += versionSize.height + rowSpacing;

		JComponent bugReportRow = createBugReportRow();
		Dimension bugReportSize = bugReportRow.getPreferredSize();
		bugReportRow.setBounds(0, y, width, bugReportSize.height);
		y += bugReportSize.height + rowSpacing;

		// The bug-report line comes before the support panel, since reporting a problem is a more immediate need than the support ask.
		SupportPanel supportPanel = new SupportPanel(width, true);
		Dimension supportPanelSize = supportPanel.getPreferredSize();
		supportPanel.setBounds(0, y, width, supportPanelSize.height);
		y += supportPanelSize.height;

		Dimension fixedSize = new Dimension(width, y);
		JPanel rightPanel = new JPanel(null)
		{
			@Override
			public Dimension getPreferredSize()
			{
				return fixedSize;
			}

			@Override
			public Dimension getMinimumSize()
			{
				return fixedSize;
			}

			@Override
			public Dimension getMaximumSize()
			{
				return fixedSize;
			}
		};
		rightPanel.setOpaque(false);
		rightPanel.add(versionLabel);
		rightPanel.add(bugReportRow);
		rightPanel.add(supportPanel);
		return rightPanel;
	}

	/**
	 * A left-aligned, word-wrapped line ending in an inline "here" hyperlink (with the URL shown on hover, like other links), rather than
	 * a separate raw-URL label below plain text.
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
		return new WrappedTextRow(textColumnWidth, FlowLayout.LEFT, 4, 4, items);
	}

	/**
	 * A {@link WrapLayout} flow of words/links wrapped to a fixed width, with fixed preferred/minimum/maximum size so its wrapped height
	 * can be measured once, up front, by the caller (see addWords-style usage in SupportPanel for the same pattern).
	 */
	private static class WrappedTextRow extends JPanel
	{
		private final Dimension fixedPreferredSize;

		WrappedTextRow(int width, int align, int hgap, int vgap, List<JComponent> items)
		{
			setOpaque(false);
			setLayout(new WrapLayout(align, hgap, vgap));
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
