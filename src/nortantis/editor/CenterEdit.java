package nortantis.editor;

import java.io.Serializable;

import nortantis.CenterIcon;
import nortantis.CenterTrees;

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
	public CenterIcon icon;
	public CenterTrees trees;
	
	
	public final int index;
	
	public CenterEdit(int index, boolean isWater, Integer regionId, CenterIcon icon, CenterTrees trees)
	{
		this.isWater = isWater;
		this.regionId = regionId;
		this.index = index;
		this.icon = icon;
		this.trees = trees;
	}
}
