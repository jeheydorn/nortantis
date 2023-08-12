package nortantis.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RowHider
{	
	private List<Component> components;
	private boolean isVisible;
	
	public RowHider(Component... components)
	{
		this.components = Arrays.asList(components);
		isVisible = true;
	}
	
	public RowHider(RowHider other1, RowHider other2)
	{
		this.components = new ArrayList<>(other1.components);
		this.components.addAll(other2.components);
		isVisible = true;
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
}
