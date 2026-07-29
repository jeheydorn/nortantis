package nortantis.swing;

import nortantis.swing.translation.Translation;
import nortantis.util.OSHelper;

import javax.swing.*;
import java.awt.*;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
	private String[] displayedMessageLines;
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
		// Clearing this forces the labels to be rebuilt even when the wrapped text works out the same as what's already showing, so that a
		// look-and-feel change picks up the new text color.
		displayedMessageLines = null;
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
		// doLayout only sets the bounds of our direct children; validate their subtrees so their descendants (the support panel's links and
		// card, the message text) are laid out within those bounds too. Without this, when this runs while the window is already showing (for
		// example returning to the startup screen after cancelling a command-line open), those descendants stay at zero size until the next
		// full validation pass, such as a window resize.
		if (messagePanel != null)
		{
			messagePanel.validate();
		}
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
	 * Rebuilds the message labels for the given width, doing nothing if they would come out the same as the ones already showing.
	 */
	private void updateMessagePanel(int availableWidth)
	{
		String[] wrapped = wrapLines(currentMessageLines, availableWidth);
		if (Arrays.equals(wrapped, displayedMessageLines))
		{
			return;
		}
		displayedMessageLines = wrapped;

		if (messagePanel != null)
		{
			remove(messagePanel);
			messagePanel = null;
		}

		if (wrapped.length == 0)
		{
			return;
		}

		messagePanel = new JPanel();
		messagePanel.setOpaque(false);
		messagePanel.setLayout(new BoxLayout(messagePanel, BoxLayout.Y_AXIS));
		Color textColor = UIManager.getColor("Label.foreground");
		Font font = chooseMessageFont(currentMessageLines);
		for (String line : wrapped)
		{
			JLabel label = new JLabel(line);
			label.setForeground(textColor);
			label.setFont(font);
			label.setAlignmentX(CENTER_ALIGNMENT);
			messagePanel.add(label);
		}
		add(messagePanel, 0);
	}

	/**
	 * Breaks the given lines into as many lines as it takes for each to fit within maxWidth when drawn in the message font. Lines already
	 * narrow enough are returned unchanged, as is any single unbreakable run of text wider than maxWidth.
	 */
	private String[] wrapLines(String[] lines, int maxWidth)
	{
		if (lines == null || lines.length == 0)
		{
			return new String[0];
		}

		FontMetrics metrics = getFontMetrics(chooseMessageFont(lines));
		List<String> result = new ArrayList<>();
		for (String line : lines)
		{
			wrapLine(line, metrics, maxWidth, result);
		}
		return result.toArray(new String[0]);
	}

	private static void wrapLine(String line, FontMetrics metrics, int maxWidth, List<String> result)
	{
		if (maxWidth <= 0 || metrics.stringWidth(line) <= maxWidth)
		{
			result.add(line);
			return;
		}

		// BreakIterator rather than splitting on spaces because languages such as Chinese write without spaces between words, and so would
		// never find a place to break.
		BreakIterator breaker = BreakIterator.getLineInstance(Translation.getEffectiveLocale());
		breaker.setText(line);
		int lineStart = 0;
		int widestFittingBreak = BreakIterator.DONE;
		for (int breakIndex = breaker.first(); breakIndex != BreakIterator.DONE; breakIndex = breaker.next())
		{
			if (breakIndex <= lineStart)
			{
				continue;
			}

			if (metrics.stringWidth(line.substring(lineStart, breakIndex).trim()) <= maxWidth)
			{
				widestFittingBreak = breakIndex;
				continue;
			}

			if (widestFittingBreak == BreakIterator.DONE)
			{
				// Nowhere to break this run of text without splitting it mid-word, so let it overflow.
				widestFittingBreak = breakIndex;
			}
			result.add(line.substring(lineStart, widestFittingBreak).trim());
			lineStart = widestFittingBreak;
			widestFittingBreak = metrics.stringWidth(line.substring(lineStart, breakIndex).trim()) <= maxWidth ? breakIndex
					: BreakIterator.DONE;
		}

		String remainder = line.substring(lineStart).trim();
		if (!remainder.isEmpty())
		{
			result.add(remainder);
		}
	}

	@Override
	public void doLayout()
	{
		Dimension size = getSize();
		scrollPane.setBounds(0, 0, size.width, size.height);

		// Wrapping happens here rather than in setMessage because it depends on the width available, which isn't known until layout and
		// changes as the window and the panels on either side of the canvas are resized.
		updateMessagePanel(size.width - sideMargin * 2);

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
