package nortantis.editor;

import java.io.Serializable;

/**
 * Stores edits made to a polygon (a "center") of a map.
 */
@SuppressWarnings("serial")
public class CenterEdit implements Serializable
{
	public boolean isWater;
	/**
	 * If this is null, then the generated region color is used if region colors are enabled.
	 */
	public Integer regionId;
	
	public final int index;
	
	public CenterEdit(int index, boolean isWater, Integer regionId)
	{
		this.isWater = isWater;
		this.regionId = regionId;
		this.index = index;
	}
}
