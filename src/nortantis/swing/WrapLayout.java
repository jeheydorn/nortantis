package nortantis.swing;

import javax.swing.*;
import java.awt.*;

/**
 * FlowLayout subclass that fully supports wrapping of components.
 */
@SuppressWarnings("serial")
public class WrapLayout extends FlowLayout
{
	/**
	 * Constructs a new <code>WrapLayout</code> with a left alignment and a default 5-unit horizontal and vertical gap.
	 */
	public WrapLayout()
	{
		super();
	}

	/**
	 * Constructs a new <code>FlowLayout</code> with the specified alignment and a default 5-unit horizontal and vertical gap. The value of
	 * the alignment argument must be one of <code>WrapLayout</code>, <code>WrapLayout</code>, or <code>WrapLayout</code>.
	 * 
	 * @param align
	 *            the alignment value
	 */
	public WrapLayout(int align)
	{
		super(align);
	}

	/**
	 * Creates a new flow layout manager with the indicated alignment and the indicated horizontal and vertical gaps.
	 * <p>
	 * The value of the alignment argument must be one of <code>WrapLayout</code>, <code>WrapLayout</code>, or <code>WrapLayout</code>.
	 * 
	 * @param align
	 *            the alignment value
	 * @param hgap
	 *            the horizontal gap between components
	 * @param vgap
	 *            the vertical gap between components
	 */
	public WrapLayout(int align, int hgap, int vgap)
	{
		super(align, hgap, vgap);
	}

	/**
	 * Returns the preferred dimensions for this layout given the <i>visible</i> components in the specified target container.
	 * 
	 * @param target
	 *            the component which needs to be laid out
	 * @return the preferred dimensions to lay out the subcomponents of the specified container
	 */
	@Override
	public Dimension preferredLayoutSize(Container target)
	{
		return layoutSize(target, true);
	}

	/**
	 * Returns the minimum dimensions needed to layout the <i>visible</i> components contained in the specified target container.
	 * 
	 * @param target
	 *            the component which needs to be laid out
	 * @return the minimum dimensions to lay out the subcomponents of the specified container
	 */
	@Override
	public Dimension minimumLayoutSize(Container target)
	{
		Dimension minimum = layoutSize(target, false);
		minimum.width -= (getHgap() + 1);
		return minimum;
	}

	/**
	 * Returns the minimum or preferred dimension needed to layout the target container.
	 *
	 * @param target
	 *            target to get layout size for
	 * @param preferred
	 *            should preferred size be calculated
	 * @return the dimension to layout the target container
	 */
	private Dimension layoutSize(Container target, boolean preferred)
	{
		synchronized (target.getTreeLock())
		{
			int hgap = getHgap();
			int vgap = getVgap();
			Insets insets = target.getInsets();
			int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2);
			int maxWidth = computeMaxWidth(target, horizontalInsetsAndGap);

			// Fit components into the allowed width

			Dimension dim = new Dimension(0, 0);
			int rowWidth = 0;
			int rowHeight = 0;

			int nmembers = target.getComponentCount();

			for (int i = 0; i < nmembers; i++)
			{
				Component m = target.getComponent(i);

				if (m.isVisible())
				{
					Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();

					// Can't add the component to current row. Start a new row.

					if (rowWidth > 0 && rowWidth + hgap + d.width > maxWidth)
					{
						addRow(dim, rowWidth, rowHeight);
						rowWidth = 0;
						rowHeight = 0;
					}

					// Add a horizontal gap for all components after the first

					if (rowWidth != 0)
					{
						rowWidth += hgap;
					}

					rowWidth += d.width;
					rowHeight = Math.max(rowHeight, d.height);
				}
			}

			addRow(dim, rowWidth, rowHeight);

			dim.width += horizontalInsetsAndGap;
			dim.height += insets.top + insets.bottom + vgap * 2;

			return dim;
		}
	}

	/**
	 * Lays out the container by wrapping components to the next row when they would exceed the container width. This
	 * override fixes a bug in FlowLayout where the horizontal gap is not included in the wrap check, causing components
	 * to be clipped on the right edge.
	 */
	@Override
	public void layoutContainer(Container target)
	{
		synchronized (target.getTreeLock())
		{
			Insets insets = target.getInsets();
			int hgap = getHgap();
			int vgap = getVgap();
			int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2);
			int maxWidth = computeMaxWidth(target, horizontalInsetsAndGap);
			int nmembers = target.getComponentCount();

			boolean ltr = target.getComponentOrientation().isLeftToRight();
			int align = getAlignment();

			// Pass 1: Assign positions row by row

			int y = insets.top + vgap;
			int rowStart = 0;

			// x tracks the current placement position within the row (before alignment adjustment)
			int x = 0;
			int rowHeight = 0;

			for (int i = 0; i < nmembers; i++)
			{
				Component m = target.getComponent(i);
				if (m.isVisible())
				{
					Dimension d = m.getPreferredSize();
					m.setSize(d.width, d.height);

					if (x > 0 && x + hgap + d.width > maxWidth)
					{
						// Finish current row
						moveComponents(target, insets.left + hgap, y, maxWidth - x, rowHeight, rowStart, i, ltr,
								align);
						y += vgap + rowHeight;
						x = 0;
						rowHeight = 0;
						rowStart = i;
					}

					if (x > 0)
					{
						x += hgap;
					}

					x += d.width;
					rowHeight = Math.max(rowHeight, d.height);
				}
			}

			// Finish last row
			moveComponents(target, insets.left + hgap, y, maxWidth - x, rowHeight, rowStart, nmembers, ltr, align);
		}
	}

	/**
	 * Positions components within a single row, applying alignment and vertical centering.
	 */
	private void moveComponents(Container target, int x, int y, int extraWidth, int rowHeight, int rowStart,
			int rowEnd, boolean ltr, int align)
	{
		switch (align)
		{
		case FlowLayout.CENTER:
			x += extraWidth / 2;
			break;
		case FlowLayout.RIGHT:
			// Fall through to TRAILING
		case FlowLayout.TRAILING:
			if (ltr)
			{
				x += extraWidth;
			}
			break;
		case FlowLayout.LEFT:
			// Fall through to LEADING
		case FlowLayout.LEADING:
			if (!ltr)
			{
				x += extraWidth;
			}
			break;
		}

		int hgap = getHgap();

		for (int i = rowStart; i < rowEnd; i++)
		{
			Component m = target.getComponent(i);
			if (m.isVisible())
			{
				int cy = y + (rowHeight - m.getHeight()) / 2;
				if (ltr)
				{
					m.setLocation(x, cy);
				}
				else
				{
					m.setLocation(target.getWidth() - x - m.getWidth(), cy);
				}
				x += m.getWidth() + hgap;
			}
		}
	}

	/**
	 * Computes the maximum content width available for laying out components, accounting for the container's current
	 * width and any ancestor scroll pane whose vertical scrollbar reduces available space.
	 */
	private int computeMaxWidth(Container target, int horizontalInsetsAndGap)
	{
		// When the container width = 0, the preferred width of the container has not yet been calculated so walk up the
		// parent chain to find a container with a known width.
		int targetWidth = target.getSize().width;
		Container container = target;

		while (container.getSize().width == 0 && container.getParent() != null)
		{
			container = container.getParent();
		}

		targetWidth = container.getSize().width;

		if (targetWidth == 0)
			targetWidth = Integer.MAX_VALUE;

		int maxWidth = targetWidth - horizontalInsetsAndGap;

		// When inside a scroll pane with a visible vertical scrollbar, cap maxWidth to the viewport width so that the
		// preferred size calculation and actual layout agree on available space.
		JScrollPane scrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, target);
		if (scrollPane != null && scrollPane.getVerticalScrollBar().isVisible())
		{
			int viewportWidth = scrollPane.getViewport().getWidth();
			if (viewportWidth > 0)
			{
				int viewportMaxWidth = viewportWidth - horizontalInsetsAndGap;
				maxWidth = Math.min(maxWidth, viewportMaxWidth);
			}
		}

		return maxWidth;
	}

	/*
	 * A new row has been completed. Use the dimensions of this row to update the preferred size for the container.
	 *
	 * @param dim update the width and height when appropriate
	 *
	 * @param rowWidth the width of the row to add
	 *
	 * @param rowHeight the height of the row to add
	 */
	private void addRow(Dimension dim, int rowWidth, int rowHeight)
	{
		dim.width = Math.max(dim.width, rowWidth);

		if (dim.height > 0)
		{
			dim.height += getVgap();
		}

		dim.height += rowHeight;
	}
}