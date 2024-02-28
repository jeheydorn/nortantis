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

import nortantis.util.Tuple2;

public class CityButtons
{
	public RowHider hider;
	private Map<String, List<Tuple2<String, JToggleButton>>> buttons;
	public JPanel typesPanel;

	public CityButtons()
	{
		this.buttons = new TreeMap<>();
	}
	
	public void clearButtons()
	{
		this.buttons = new TreeMap<>();
		typesPanel.removeAll();
	}

	public void addButton(String cityType, String cityFileNameWithoutWidthOrExtension, JToggleButton button)
	{
		if (!buttons.containsKey(cityType))
		{
			buttons.put(cityType, new ArrayList<>());
		}

		buttons.get(cityType).add(new Tuple2<>(cityFileNameWithoutWidthOrExtension, button));
	}

	public List<Tuple2<String, JToggleButton>> getIconNamesAndButtons(String cityType)
	{
		return buttons.get(cityType);
	}

	public Set<String> getCityTypes()
	{
		return buttons.keySet();
	}

	public Tuple2<String, String> getSelectedCity()
	{
		for (String cityType : getCityTypes())
		{
			for (Tuple2<String, JToggleButton> tuple : buttons.get(cityType))
			{
				if (tuple.getSecond().isSelected())
				{
					return new Tuple2<>(cityType, tuple.getFirst());
				}
			}
		}
		return null;
	}

	public void selectFirstButton()
	{
		unselectAllButtons();
		JToggleButton toggleButton = getIconNamesAndButtons(getCityTypes().iterator().next()).get(0).getSecond();
		toggleButton.setSelected(true);
		updateToggleButtonBorder(toggleButton);
	}

	private void unselectAllButtons()
	{
		for (String cityType : getCityTypes())
		{
			for (Tuple2<String, JToggleButton> tuple : buttons.get(cityType))
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
		for (String cityType : getCityTypes())
		{
			for (Tuple2<String, JToggleButton> tuple : buttons.get(cityType))
			{
				if (tuple.getSecond().isSelected() && tuple.getSecond() != buttonToIgnore)
				{
					tuple.getSecond().setSelected(false);
					updateToggleButtonBorder(tuple.getSecond());
				}
			}
		}
	}

	public boolean selectButtonIfPresent(String cityType, String cityIconNameWithoutWidthOrExtension)
	{
		unselectAllButtons();
		
		if (!buttons.containsKey(cityType))
		{
			return false;
		}
		
		for (Tuple2<String, JToggleButton> tuple : buttons.get(cityType))
		{
			if (tuple.getFirst().equals(cityIconNameWithoutWidthOrExtension))
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
}
