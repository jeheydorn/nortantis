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

		container = new JPanel();
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
}
