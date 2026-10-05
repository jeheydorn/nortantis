package nortantis.swing;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import nortantis.swing.translation.Translation;
import nortantis.swing.SwingHelper;

public class ControlClickBehaviorWidget
{
	private final JToggleButton selectModeButton;
	private final JToggleButton unselectModeButton;

	public ControlClickBehaviorWidget()
	{
		ActionListener modeListener = new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (e != null && e.getSource() == selectModeButton)
				{
					unselectModeButton.setSelected(false);
					if (!selectModeButton.isSelected())
					{
						selectModeButton.setSelected(true);
					}
					selectModeButton.grabFocus();
				}
				else if (e != null && e.getSource() == unselectModeButton)
				{
					selectModeButton.setSelected(false);
					if (!unselectModeButton.isSelected())
					{
						unselectModeButton.setSelected(true);
					}
					unselectModeButton.grabFocus();
				}
			}
		};

		selectModeButton = new JToggleButton(Translation.get("iconsTool.ctrlClickBehavior.select"));
		selectModeButton.setSelected(true);
		selectModeButton.addActionListener(modeListener);

		unselectModeButton = new JToggleButton(Translation.get("iconsTool.ctrlClickBehavior.unselect"));
		unselectModeButton.addActionListener(modeListener);
	}

	public RowHider addToOrganizer(GridBagOrganizer organizer)
	{
		return addToOrganizer(organizer, "iconsTool.ctrlClickBehavior.help");
	}

	/**
	 * @param helpKey
	 *            The translation key of the row's help text, which says what is selected. Its parameter is the name of the command key.
	 */
	public RowHider addToOrganizer(GridBagOrganizer organizer, String helpKey)
	{
		JPanel container = new JPanel();
		container.setLayout(new WrapLayout(WrapLayout.LEFT));
		// Remove the horizontal and vertical gaps from the border around the elements.
		container.setBorder(BorderFactory.createEmptyBorder(-5, -5, -5, -5));
		container.add(selectModeButton);
		container.add(unselectModeButton);

		String keyName = SwingHelper.getCommandKeyName();
		return organizer.addLabelAndComponent(Translation.get("iconsTool.ctrlClickBehavior.label", keyName), Translation.get(helpKey, keyName), container);
	}

	public boolean isSelectMode()
	{
		return selectModeButton.isSelected();
	}

	@SuppressWarnings("unused")
	public boolean isUnselectMode()
	{
		return unselectModeButton.isSelected();
	}
}
