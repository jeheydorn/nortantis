package nortantis.swing;

import java.awt.Dimension;
import java.awt.Rectangle;

import javax.swing.JPanel;
import javax.swing.Scrollable;

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
		return false;
	}
}