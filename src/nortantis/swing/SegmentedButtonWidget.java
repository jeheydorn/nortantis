package nortantis.swing;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class SegmentedButtonWidget
{
	private final List<JToggleButton> buttons;
	private final JPanel container;
	private final ButtonGroup group;
	private final WrapLayout wrapLayout;

	public SegmentedButtonWidget(List<JToggleButton> buttons)
	{
		this(buttons, false);
	}

	@SuppressWarnings("serial")
	public SegmentedButtonWidget(List<JToggleButton> buttons, boolean multiSelect)
	{
		this.buttons = buttons;

		if (multiSelect)
		{
			group = null;
		}
		else
		{
			group = new ButtonGroup();
			for (JToggleButton button : buttons)
			{
				group.add(button);
			}
		}

		for (JToggleButton button : buttons)
		{
			SwingHelper.reduceHorizontalMargin(button);
		}

		container = new JPanel()
		{
			@Override
			public Dimension getPreferredSize()
			{
				Dimension pref = super.getPreferredSize();

				// WrapLayout's preferredLayoutSize overestimates available width during
				// initial layout (parent-walk finds the viewport, but GridBag only gives
				// this component ~60% of that minus cell insets). Compute wrapping at a
				// tighter width so the correct multi-row height is returned.
				int width = getWidth();
				if (width <= 0)
				{
					width = (int) (SwingHelper.sidePanelPreferredWidth * 0.6) - 20;
				}
				int neededHeight = computeWrappedHeight(width);
				if (neededHeight > pref.height)
				{
					pref = new Dimension(pref.width, neededHeight);
				}

				return pref;
			}

			@Override
			public Dimension getMinimumSize()
			{
				// GridBagLayout may use minimum sizes to determine row heights.
				// Return preferred size so the row is always tall enough for wrapped
				// buttons.
				return getPreferredSize();
			}

			private int computeWrappedHeight(int containerWidth)
			{
				Insets insets = getInsets();
				int hgap = wrapLayout.getHgap();
				int vgap = wrapLayout.getVgap();
				int maxWidth = containerWidth - insets.left - insets.right - hgap * 2;
				int rowWidth = 0;
				int rowHeight = 0;
				int totalHeight = 0;

				for (int i = 0; i < getComponentCount(); i++)
				{
					Component c = getComponent(i);
					if (c.isVisible())
					{
						Dimension d = c.getPreferredSize();
						if (rowWidth > 0 && rowWidth + hgap + d.width > maxWidth)
						{
							if (totalHeight > 0)
							{
								totalHeight += vgap;
							}
							totalHeight += rowHeight;
							rowWidth = 0;
							rowHeight = 0;
						}
						if (rowWidth > 0)
						{
							rowWidth += hgap;
						}
						rowWidth += d.width;
						rowHeight = Math.max(rowHeight, d.height);
					}
				}
				if (totalHeight > 0)
				{
					totalHeight += vgap;
				}
				totalHeight += rowHeight;
				totalHeight += insets.top + insets.bottom + vgap * 2;
				return totalHeight;
			}
		};
		wrapLayout = new WrapLayout(WrapLayout.LEFT);
		container.setLayout(wrapLayout);
		container.setBorder(BorderFactory.createEmptyBorder(-5, -5, -5, -5));
		for (JToggleButton button : buttons)
		{
			container.add(button);
		}
	}

	public void setReserveScrollBarSpace(boolean reserveScrollBarSpace)
	{
		wrapLayout.setReserveScrollBarSpace(reserveScrollBarSpace);
	}

	public void updateSegmentPositions()
	{
	}

	public RowHider addToOrganizer(GridBagOrganizer organizer, String label, String tooltip)
	{
		return organizer.addLabelAndComponent(label, tooltip, container);
	}

	public RowHider addToOrganizer(GridBagOrganizer organizer, String label, String tooltip, int topInset)
	{
		return organizer.addLabelAndComponent(label, tooltip, container, topInset);
	}
}
