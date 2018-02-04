package nortantis.editor;

import java.awt.Color;

/**
 * Stores edits made to a polygon (a "center") of a map.
 */
public class CenterEdit
{
	public boolean isWater;
	/**
	 * If this is null, then the generated region color is used if region colors are enabled.
	 */
	public Color regionColor;
	
	public CenterEdit(boolean isOcean, Color regionColor)
	{
		this.isWater = isOcean;
		this.regionColor = regionColor;
	}
	
	public CenterEdit()
	{
	}
}
