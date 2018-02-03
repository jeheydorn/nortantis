package nortantis.editor;

/**
 * Stores edits made to a polygon (a "center") of a map.
 */
public class CenterEdit
{
	public boolean isWater;
	
	public CenterEdit(boolean isOcean)
	{
		this.isWater = isOcean;
	}
	
	public CenterEdit()
	{
		
	}
}
