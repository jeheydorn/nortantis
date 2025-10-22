package nortantis.swing;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

import nortantis.IconType;
import nortantis.ImageCache;
import nortantis.editor.UserPreferences;
import nortantis.util.Tuple2;

public class NamedIconSelector
{
	public RowHider hider;
	private Map<String, List<Tuple2<String, UnscaledImageToggleButton>>> buttons;
	private JPanel container;
	public final IconType type;

	public NamedIconSelector(IconType type)
	{
		this.type = type;
		this.buttons = new TreeMap<>();
		container = new JPanel();
		container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
	}

	public void updateButtonList(String artPack, String customImagesPath)
	{
		Tuple2<String, String> selectedButton = getSelectedButton();
		clearButtons();

		boolean hasAtLeastOneImage = false;
		for (String groupId : ImageCache.getInstance(artPack, customImagesPath).getIconGroupNames(type))
		{
			JPanel buttonsPanel = new JPanel();
			buttonsPanel.setLayout(new WrapLayout());
			buttonsPanel.setBorder(new DynamicLineBorder("controlShadow", 1));
			for (String fileNameWithoutWidthOrExtension : ImageCache.getInstance(artPack, customImagesPath)
					.getIconGroupFileNamesWithoutWidthOrExtensionAsSet(type, groupId))
			{
				UnscaledImageToggleButton toggleButton = new UnscaledImageToggleButton();
				toggleButton.setToolTipText(fileNameWithoutWidthOrExtension);
				toggleButton.addActionListener(new ActionListener()
				{

					@Override
					public void actionPerformed(ActionEvent e)
					{
						if (!toggleButton.isSelected())
						{
							toggleButton.setSelected(true);
						}
						unselectAllButtonsExcept(toggleButton);
						NamedIconSelector.updateToggleButtonBorder(toggleButton);
					}
				});
				NamedIconSelector.updateToggleButtonBorder(toggleButton);

				addButton(groupId, fileNameWithoutWidthOrExtension, toggleButton);
				buttonsPanel.add(toggleButton);
				hasAtLeastOneImage = true;
			}

			// If at least one button was added for this group
			if (getTypes().contains(groupId))
			{	
				CollapsiblePanel collapsiblePanel = new CollapsiblePanel(type.toString() + "Type", groupId, buttonsPanel);
				container.add(collapsiblePanel);
			}
			
		}

		if (hasAtLeastOneImage)
		{
			if (selectedButton != null)
			{
				boolean found = selectButtonIfPresent(selectedButton.getFirst(), selectedButton.getSecond());
				if (!found)
				{
					selectFirstButton();
				}
			}
			else
			{
				selectFirstButton();
			}
		}
		else
		{
			container.add(new JLabel("<html>The art pack '" + artPack + "' has no " + type + ".</html>"));
		}
	}

	public void addtoOrganizer(GridBagOrganizer organizer, String labelText)
	{
		hider = organizer.addLeftAlignedComponentWithStackedLabel(labelText, "", container);
	}

	public void clearButtons()
	{
		this.buttons = new TreeMap<>();
		container.removeAll();
	}

	public void addButton(String type, String iconFileNameWithoutWidthOrExtension, UnscaledImageToggleButton button)
	{
		if (!buttons.containsKey(type))
		{
			buttons.put(type, new ArrayList<>());
		}

		buttons.get(type).add(new Tuple2<>(iconFileNameWithoutWidthOrExtension, button));
	}

	public List<Tuple2<String, UnscaledImageToggleButton>> getIconNamesAndButtons(String type)
	{
		return buttons.get(type);
	}

	public Set<String> getTypes()
	{
		return buttons.keySet();
	}

	public Tuple2<String, String> getSelectedButton()
	{
		for (String type : getTypes())
		{
			for (Tuple2<String, UnscaledImageToggleButton> tuple : buttons.get(type))
			{
				if (tuple.getSecond().isSelected())
				{
					return new Tuple2<>(type, tuple.getFirst());
				}
			}
		}
		return null;
	}

	public void selectFirstButton()
	{
		unselectAllButtons();
		UnscaledImageToggleButton toggleButton = getIconNamesAndButtons(getTypes().iterator().next()).get(0).getSecond();
		toggleButton.setSelected(true);
		updateToggleButtonBorder(toggleButton);
	}

	private void unselectAllButtons()
	{
		for (String type : getTypes())
		{
			for (Tuple2<String, UnscaledImageToggleButton> tuple : buttons.get(type))
			{
				if (tuple.getSecond().isSelected())
				{
					tuple.getSecond().setSelected(false);
					updateToggleButtonBorder(tuple.getSecond());
				}
			}
		}
	}

	public void unselectAllButtonsExcept(UnscaledImageToggleButton buttonToIgnore)
	{
		for (String type : getTypes())
		{
			for (Tuple2<String, UnscaledImageToggleButton> tuple : buttons.get(type))
			{
				if (tuple.getSecond().isSelected() && tuple.getSecond() != buttonToIgnore)
				{
					tuple.getSecond().setSelected(false);
					updateToggleButtonBorder(tuple.getSecond());
				}
			}
		}
	}

	public boolean selectButtonIfPresent(String type, String iconNameWithoutWidthOrExtension)
	{
		unselectAllButtons();

		if (!buttons.containsKey(type))
		{
			return false;
		}

		for (Tuple2<String, UnscaledImageToggleButton> tuple : buttons.get(type))
		{
			if (tuple.getFirst().equals(iconNameWithoutWidthOrExtension))
			{
				if (!tuple.getSecond().isSelected())
				{
					tuple.getSecond().setSelected(true);
					updateToggleButtonBorder(tuple.getSecond());
				}
				return true;
			}
		}

		return false;
	}

	public static void updateToggleButtonBorder(UnscaledImageToggleButton toggleButton)
	{
		final int width = 4;
		if (UserPreferences.getInstance().lookAndFeel == LookAndFeel.Dark || UserPreferences.getInstance().lookAndFeel == LookAndFeel.Light)
		{
			if (toggleButton.isSelected())
			{
				toggleButton.setBorder(BorderFactory.createLineBorder(ToolsPanel.getColorForToggledButtons(), width));
			}
			else
			{
				toggleButton.setBorder(BorderFactory.createEmptyBorder(width, width, width, width));
			}
		}
		else
		{
			toggleButton.setBorder(BorderFactory.createEmptyBorder(width, width, width, width));
		}
	}
}
