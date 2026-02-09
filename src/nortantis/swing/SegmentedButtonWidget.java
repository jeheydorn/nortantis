package nortantis.swing;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class SegmentedButtonWidget
{
	private final List<JToggleButton> buttons;
	private final JPanel container;
	private final ButtonGroup group;

	public SegmentedButtonWidget(List<JToggleButton> buttons)
	{
		this.buttons = buttons;

		group = new ButtonGroup();
		for (JToggleButton button : buttons)
		{
			group.add(button);
		}

		container = new JPanel();
		container.setLayout(new WrapLayout(WrapLayout.LEFT));
		container.setBorder(BorderFactory.createEmptyBorder(-5, -5, -5, -5));
		for (JToggleButton button : buttons)
		{
			container.add(button);
		}
	}

	public void updateSegmentPositions()
	{
	}

	public RowHider addToOrganizer(GridBagOrganizer organizer, String label, String tooltip)
	{
		return organizer.addLabelAndComponent(label, tooltip, container);
	}
}
