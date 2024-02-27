package nortantis.swing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.swing.JPanel;
import javax.swing.JToggleButton;

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
		getIconNamesAndButtons(getCityTypes().iterator().next()).get(0).getSecond().setSelected(true);
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
				}
			}
		}
	}

	public boolean selectButtonIfPresent(String cityType, String cityIconNameWithoutWidthOrExtension)
	{
		unselectAllButtons();
		for (Tuple2<String, JToggleButton> tuple : buttons.get(cityType))
		{
			if (tuple.getFirst().equals(cityIconNameWithoutWidthOrExtension))
			{
				if (!tuple.getSecond().isSelected())
				{
					tuple.getSecond().setSelected(true);
				}
				return true;
			}
		}
		
		return false;
	}
}
