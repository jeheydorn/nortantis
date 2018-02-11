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
	public int regionId;
	
	public CenterEdit(boolean isOcean, int regionId)
	{
		this.isWater = isOcean;
		this.regionId = regionId;
	}
	
	public CenterEdit()
	{
	}
}
