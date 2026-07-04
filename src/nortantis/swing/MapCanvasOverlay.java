package nortantis.swing;

import nortantis.util.OSHelper;

import javax.swing.*;
import java.awt.*;

/**
 * Wraps the map editing scroll pane and layers transparent, click-through panels on top of it: a message strip near the top (welcome
 * text, "drawing map...", error messages) and, only at startup, a support panel near the bottom (donation/book links). Neither strip
 * spans the full canvas, so mouse events for the map itself fall through the empty space between them to the scroll pane underneath -
 * the scroll pane is a sibling that fills the whole container and sits behind the strips in z-order, so clicks outside the strips'
 * bounds simply hit it instead.
 */
@SuppressWarnings("serial")
public class MapCanvasOverlay extends JPanel
{
	private static final int topMargin = 24;
	private static final int bottomMargin = 24;
	private static final int sideMargin = 20;
	private static final int messageFontSize = 26;

	private final JScrollPane scrollPane;
	private JPanel messagePanel;
	private SupportPanel supportPanel;
	private String[] currentMessageLines;
	private int supportPanelContentWidth;
	private boolean supportPanelShowAskCard;

	public MapCanvasOverlay(JScrollPane scrollPane)
	{
		this.scrollPane = scrollPane;
		setOpaque(false);
		setLayout(null);
		add(scrollPane);
	}

	/**
	 * Shows a centered, multi-line message near the top of the canvas (e.g. the welcome text, "drawing map...", or a draw-failure
	 * message), replacing any message currently shown. Pass no lines to clear the message.
	 */
	public void setMessage(String... lines)
	{
		currentMessageLines = lines;

		if (messagePanel != null)
		{
			remove(messagePanel);
			messagePanel = null;
		}

		if (lines != null && lines.length > 0)
		{
			messagePanel = new JPanel();
			messagePanel.setOpaque(false);
			messagePanel.setLayout(new BoxLayout(messagePanel, BoxLayout.Y_AXIS));
			Color textColor = UIManager.getColor("Label.foreground");
			Font font = chooseMessageFont(lines);
			for (String line : lines)
			{
				JLabel label = new JLabel(line);
				label.setForeground(textColor);
				label.setFont(font);
				label.setAlignmentX(CENTER_ALIGNMENT);
				messagePanel.add(label);
			}
			add(messagePanel, 0);
		}

		relayoutAndRepaint();
	}

	/**
	 * Shows (or, if false, hides) the support panel near the bottom of the canvas. Only intended to be shown at startup, before a map is
	 * open. Takes the panel's constructor parameters, rather than a pre-built panel, so {@link #handleLookAndFeelChange()} can rebuild it
	 * with the same parameters later.
	 */
	public void setSupportPanel(boolean show, int contentWidth, boolean showAskCard)
	{
		if (supportPanel != null)
		{
			remove(supportPanel);
			supportPanel = null;
		}

		if (show)
		{
			supportPanelContentWidth = contentWidth;
			supportPanelShowAskCard = showAskCard;
			supportPanel = new SupportPanel(contentWidth, showAskCard);
			add(supportPanel, 0);
		}

		relayoutAndRepaint();
	}

	/**
	 * Rebuilds the message and/or support panel so their colors (baked in at construction time, e.g. the support panel's card
	 * background/border, which are computed from the current look and feel but not re-read on every paint) pick up a look-and-feel change
	 * made while they're already showing. Without this, they'd keep the old theme's colors until the app is relaunched.
	 */
	public void handleLookAndFeelChange()
	{
		if (messagePanel != null)
		{
			setMessage(currentMessageLines);
		}
		if (supportPanel != null)
		{
			setSupportPanel(true, supportPanelContentWidth, supportPanelShowAskCard);
		}
	}

	/**
	 * Repositions children and repaints immediately, rather than via revalidate() (which defers layout to a later pass). A child's size
	 * changing here - e.g. the support panel growing when its checkbox is added - moves where other children need to be repainted, so
	 * layout must be resolved before repaint() runs, or repaint() targets the stale (pre-layout) bounds and part of the changed area is
	 * never redrawn until something else (e.g. a window resize) forces a full repaint.
	 */
	private void relayoutAndRepaint()
	{
		doLayout();
		repaint();
	}

	private static Font chooseMessageFont(String[] lines)
	{
		Font font = new Font(OSHelper.getDecorativeFontFamilyName(), Font.PLAIN, messageFontSize);
		if (font.canDisplayUpTo(String.join("", lines)) != -1)
		{
			font = new Font(Font.SERIF, Font.PLAIN, messageFontSize);
		}
		return font;
	}

	@Override
	public void doLayout()
	{
		Dimension size = getSize();
		scrollPane.setBounds(0, 0, size.width, size.height);

		int messageBottom = topMargin;
		if (messagePanel != null)
		{
			Dimension preferred = messagePanel.getPreferredSize();
			int x = Math.max(sideMargin, (size.width - preferred.width) / 2);
			messagePanel.setBounds(x, topMargin, preferred.width, preferred.height);
			messageBottom = topMargin + preferred.height;
		}

		if (supportPanel != null)
		{
			Dimension preferred = supportPanel.getPreferredSize();
			int x = Math.max(sideMargin, (size.width - preferred.width) / 2);
			int y = Math.max(messageBottom, size.height - preferred.height - bottomMargin);
			supportPanel.setBounds(x, y, preferred.width, preferred.height);
		}
	}

	@Override
	public Dimension getPreferredSize()
	{
		return scrollPane.getPreferredSize();
	}

	@Override
	public Dimension getMinimumSize()
	{
		return scrollPane.getMinimumSize();
	}
}
