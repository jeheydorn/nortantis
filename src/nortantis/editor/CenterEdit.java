package nortantis.editor;

/**
 * Stores edits made to a polygon (a "center") of a map.
 */
public class CenterEdit
{
	public int regionId;
	public boolean isWater;
	
	public CenterEdit(int regionId, boolean isOcean)
	{
		this.regionId = regionId;
		this.isWater = isOcean;
	}
}
