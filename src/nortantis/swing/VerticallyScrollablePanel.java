package nortantis.swing;

import javax.swing.*;
import java.awt.*;

@SuppressWarnings("serial")
/***
 * A panel which can be shrunk below its preferred width (doing so will not cause a scroll bar to appear, but will instead shrink the
 * panel), but which cannot be shrunk below its preferred height (doing so will cause a scroll bar to appear).
 * 
 */
public class VerticallyScrollablePanel extends JPanel implements Scrollable
{
	public VerticallyScrollablePanel()
	{
	}

	@Override
	public Dimension getPreferredScrollableViewportSize()
	{
		return getPreferredSize();
	}

	@Override
	public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
	{
		return SwingHelper.sidePanelScrollSpeed;
	}

	@Override
	public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
	{
		return SwingHelper.sidePanelScrollSpeed;
	}

	@Override
	public boolean getScrollableTracksViewportWidth()
	{
		return true;
	}

	@Override
	public boolean getScrollableTracksViewportHeight()
	{
		// When the content is shorter than the viewport, stretch to fill the viewport's height so the leftover space is painted with this
		// panel's background rather than the viewport's own background, which is white under some look-and-feels (notably the native macOS
		// one). Panels placed in a scroll pane end with a weighty filler row, so the real components stay top-aligned as the panel grows.
		// When the content is taller than the viewport, report false so a vertical scroll bar appears instead.
		if (getParent() instanceof JViewport)
		{
			return getParent().getHeight() > getPreferredSize().height;
		}
		return false;
	}
}