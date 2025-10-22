package nortantis.swing;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

public class ControlClickBehaviorWidget
{
	private JToggleButton selectModeButton;
	private JToggleButton unselectModeButton;
	private JPanel container;

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

		selectModeButton = new JToggleButton("Select");
		selectModeButton.setSelected(true);
		selectModeButton.addActionListener(modeListener);

		unselectModeButton = new JToggleButton("Unselect");
		unselectModeButton.addActionListener(modeListener);
	}

	public RowHider addToOrganizer(GridBagOrganizer organizer)
	{
		container = new JPanel();
		container.setLayout(new WrapLayout(WrapLayout.LEFT));
		// Remove the horizontal and vertical gaps from the border around the elements.
		container.setBorder(BorderFactory.createEmptyBorder(-5, -5, -5, -5));
		container.add(selectModeButton);
		container.add(unselectModeButton);

		return organizer.addLabelAndComponent("Ctrl-click behavior:",
				"Whether to add or remove icons from the selection when Ctrl is held while clicking or dragging the mosue.", container);
	}
	
	public boolean isSelectMode()
	{
		return selectModeButton.isSelected();
	}
	
	public boolean isUnselectMode()
	{
		return unselectModeButton.isSelected();
	}
}
