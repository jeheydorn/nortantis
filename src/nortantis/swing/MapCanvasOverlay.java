package nortantis.swing;

import nortantis.util.OSHelper;

import javax.swing.*;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import java.awt.*;

/**
 * Wraps the map editing scroll pane and layers transparent, click-through panels on top of it: a message strip near the top (welcome
 * text, "drawing map...", error messages) and, only at startup, a support panel near the bottom (donation/book links). Mouse events for
 * the map itself reach the scroll pane underneath - it is a sibling that fills the whole container and sits behind the strips in
 * z-order, so clicks outside the support panel's bounds simply hit it instead, and the message strip refuses hits entirely.
 */
@SuppressWarnings("serial")
public class MapCanvasOverlay extends JPanel
{
	private static final int topMargin = 24;
	private static final int bottomMargin = 24;
	private static final int sideMargin = 20;
	private static final int messageFontSize = 26;

	private final JScrollPane scrollPane;
	private JTextPane messagePanel;
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
	 * message), replacing any message currently shown. Lines too wide for the canvas are wrapped. Pass no lines to clear the message.
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
			messagePanel = createMessagePanel(lines);
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
			supportPanel = new SupportPanel(contentWidth, showAskCard, false);
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
		// doLayout only sets the bounds of our direct children; validate the support panel's subtree so its descendants (its links and card)
		// are laid out within those bounds too. Without this, when this runs while the window is already showing (for example returning to the
		// startup screen after cancelling a command-line open), those descendants stay at zero size until the next full validation pass, such
		// as a window resize.
		if (supportPanel != null)
		{
			supportPanel.validate();
		}
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

	/**
	 * Creates the text pane that shows the message, centered and wrapped to whatever width it is later given.
	 */
	private static JTextPane createMessagePanel(String[] lines)
	{
		JTextPane pane = new MessagePane();
		pane.setEditable(false);
		pane.setFocusable(false);
		pane.setOpaque(false);
		pane.setBorder(null);
		pane.setFont(chooseMessageFont(lines));
		pane.setForeground(UIManager.getColor("Label.foreground"));
		pane.setText(String.join("\n", lines));

		SimpleAttributeSet attributes = new SimpleAttributeSet();
		StyleConstants.setAlignment(attributes, StyleConstants.ALIGN_CENTER);
		StyleConstants.setLineSpacing(attributes, getLineSpacingThatRemovesLeading(pane));
		pane.getStyledDocument().setParagraphAttributes(0, pane.getDocument().getLength(), attributes, false);

		return pane;
	}

	/**
	 * A text pane that no mouse event can land on, so that it neither shows a text cursor nor swallows clicks meant for the map beneath it.
	 */
	@SuppressWarnings("serial")
	private static class MessagePane extends JTextPane
	{
		@Override
		public boolean contains(int x, int y)
		{
			return false;
		}
	}

	private static FontMetrics getMessageFontMetrics(JTextPane pane)
	{
		return pane.getFontMetrics(pane.getFont());
	}

	/**
	 * The paragraph line spacing, in the fraction-of-a-line-height units StyleConstants.setLineSpacing takes, that closes up the gap between
	 * lines by the font's leading. Decorative fonts such as Gabriola report a leading nearly as tall as the text itself, which without this
	 * reads as an oversized gap between wrapped lines.
	 */
	private static float getLineSpacingThatRemovesLeading(JTextPane pane)
	{
		FontMetrics metrics = getMessageFontMetrics(pane);
		return -metrics.getLeading() / (float) metrics.getHeight();
	}

	/**
	 * The height the message needs once wrapped to the given width. A negative line spacing shortens every line including the last, which
	 * the text pane's preferred height does not compensate for, so the leading taken off the last line is added back to keep it from being
	 * clipped.
	 */
	private int getMessagePanelHeight(int availableWidth)
	{
		messagePanel.setSize(availableWidth, Short.MAX_VALUE);
		return messagePanel.getPreferredSize().height + getMessageFontMetrics(messagePanel).getLeading();
	}

	@Override
	public void doLayout()
	{
		Dimension size = getSize();
		scrollPane.setBounds(0, 0, size.width, size.height);

		int messageBottom = topMargin;
		if (messagePanel != null)
		{
			// The message pane spans the canvas rather than being sized to its text, since it wraps and centers its own lines. How tall that
			// leaves it isn't known until here, because it depends on the width available, which changes as the window and the panels on either
			// side of the canvas are resized.
			int width = Math.max(1, size.width - sideMargin * 2);
			int height = getMessagePanelHeight(width);
			// Raised by the leading the text pane holds above its first line, so that the text itself starts at the top margin rather than the
			// blank space above it doing so.
			int y = topMargin - getMessageFontMetrics(messagePanel).getLeading();
			messagePanel.setBounds(sideMargin, y, width, height);
			messageBottom = y + height;
		}

		if (supportPanel != null)
		{
			Dimension preferred = supportPanel.getPreferredSize();
			int x = Math.max(sideMargin, (size.width - preferred.width) / 2);
			int y = Math.max(messageBottom, size.height - preferred.height - bottomMargin);
			supportPanel.setBounds(x, y, preferred.width, preferred.height);
		}
	}

	/**
	 * False while a message or support panel is showing, because those overlap the scroll pane. Swing's painting optimization assumes a
	 * container's children never overlap, so a repaint requested from inside the scroll pane (such as the map canvas repainting itself)
	 * would paint over the strips and leave them erased until something repainted this container. Returning false makes Swing start such
	 * repaints from here instead, so the strips are painted back on top. Painting stays fully optimized when neither strip is showing,
	 * which is the case whenever a map is open.
	 */
	@Override
	public boolean isOptimizedDrawingEnabled()
	{
		return messagePanel == null && supportPanel == null;
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
