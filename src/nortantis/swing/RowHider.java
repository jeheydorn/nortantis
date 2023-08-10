package nortantis.swing;

import java.awt.GridBagConstraints;

public class RowHider
{
	private GridBagConstraints lc;
	private GridBagConstraints cc;

	public RowHider(GridBagConstraints lc, GridBagConstraints cc)
	{
		this.lc = lc;
		this.cc = cc;
	}
	 
	public void setVisible(boolean visible)
	{
		if (visible)
		{
			lc.weighty = 1.0;
			cc.weighty = 1.0;
		}
		else
		{
			lc.weighty = 0.0;
			cc.weighty = 0.0;
		}
	}
}
