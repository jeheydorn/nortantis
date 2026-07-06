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
		return false;
	}

	@Override
	public void addNotify()
	{
		super.addNotify();
		syncViewportBackground();
	}

	@Override
	public void setBackground(Color bg)
	{
		super.setBackground(bg);
		syncViewportBackground();
	}

	/**
	 * Paints the viewport's leftover area (the space below this panel when its content is shorter than the viewport) with this panel's
	 * background rather than the viewport's own background, which is white under some look-and-feels (notably the native macOS one). This
	 * keeps the panel's content top-aligned at its preferred height instead of stretching to fill the viewport.
	 */
	private void syncViewportBackground()
	{
		if (getParent() instanceof JViewport viewport)
		{
			viewport.setOpaque(true);
			Color background = getBackground();
			// Copy the color so a UIResource background is not replaced when the viewport's UI is reinstalled on a theme change.
			viewport.setBackground(background == null ? null : new Color(background.getRGB(), true));
		}
	}
}