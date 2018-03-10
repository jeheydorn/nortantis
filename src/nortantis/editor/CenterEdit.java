package nortantis.editor;

/**
 * Stores edits made to a polygon (a "center") of a map.
 */
public class CenterEdit
{
	public boolean isWater;
	/**
	 * If this is null, then the generated region color is used if region colors are enabled.
	 */
	public Integer regionId;
	
	public CenterEdit(boolean isWater, Integer regionId)
	{
		this.isWater = isWater;
		this.regionId = regionId;
	}
}
