package nortantis.swing;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RowHider
{
	private List<Component> components;
	private boolean isVisible;

	public RowHider(Component... components)
	{
		this.components = new ArrayList<>(components.length);
		this.components.addAll(Arrays.asList(components));
		isVisible = true;
	}

	public RowHider(RowHider other1, RowHider other2)
	{
		this.components = new ArrayList<>(other1.components);
		this.components.addAll(other2.components);
		isVisible = true;
	}

	public void add(RowHider other)
	{
		components.addAll(other.components);
	}

	public void setVisible(boolean visible)
	{
		isVisible = visible;

		for (Component comp : components)
		{
			comp.setVisible(visible);
		}
	}

	public boolean isVisible()
	{
		return isVisible;
	}

	/**
	 * Enables or disables every component in the row, including the components inside them.
	 */
	public void setEnabled(boolean enabled)
	{
		for (Component comp : components)
		{
			SwingHelper.setEnabled(comp, enabled);
		}
	}
}
