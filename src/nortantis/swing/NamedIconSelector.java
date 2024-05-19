package nortantis.swing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;

import nortantis.IconType;
import nortantis.util.Tuple2;

public class NamedIconSelector
{
	public RowHider hider;
	private Map<String, List<Tuple2<String, JToggleButton>>> buttons;
	public JPanel typesPanel;
	private Map<String, CollapsiblePanel> panels;
	public final IconType type;

	public NamedIconSelector(IconType type)
	{
		this.type = type;
		this.buttons = new TreeMap<>();
		panels = new TreeMap<>();
	}
	
	public void clearButtons()
	{
		this.buttons = new TreeMap<>();
		panels = new TreeMap<>();
		typesPanel.removeAll();
	}

	public void addButton(String type, String iconFileNameWithoutWidthOrExtension, JToggleButton button)
	{
		if (!buttons.containsKey(type))
		{
			buttons.put(type, new ArrayList<>());
		}

		buttons.get(type).add(new Tuple2<>(iconFileNameWithoutWidthOrExtension, button));
	}

	public List<Tuple2<String, JToggleButton>> getIconNamesAndButtons(String type)
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
			for (Tuple2<String, JToggleButton> tuple : buttons.get(type))
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
		JToggleButton toggleButton = getIconNamesAndButtons(getTypes().iterator().next()).get(0).getSecond();
		toggleButton.setSelected(true);
		updateToggleButtonBorder(toggleButton);
	}

	private void unselectAllButtons()
	{
		for (String type : getTypes())
		{
			for (Tuple2<String, JToggleButton> tuple : buttons.get(type))
			{
				if (tuple.getSecond().isSelected())
				{
					tuple.getSecond().setSelected(false);
					updateToggleButtonBorder(tuple.getSecond());
				}
			}
		}
	}
	
	public void unselectAllButtonsExcept(JToggleButton buttonToIgnore)
	{
		for (String type : getTypes())
		{
			for (Tuple2<String, JToggleButton> tuple : buttons.get(type))
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
		
		for (Tuple2<String, JToggleButton> tuple : buttons.get(type))
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
	
	public static void updateToggleButtonBorder(JToggleButton toggleButton)
	{
		if (UIManager.getLookAndFeel() instanceof FlatDarkLaf)
		{
			final int width = 4;
			if (toggleButton.isSelected())
			{
				int shade = 180;
				toggleButton.setBorder(BorderFactory.createLineBorder(new java.awt.Color(shade, shade, shade), width));
			}
			else
			{
				toggleButton.setBorder(BorderFactory.createEmptyBorder(width, width, width, width));
			}
		}	
	}
	
	public void addCollapsiblePanel(String type, CollapsiblePanel panel)
	{
		panels.put(type, panel);
	}
}
