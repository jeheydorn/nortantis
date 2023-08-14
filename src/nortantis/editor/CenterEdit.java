package nortantis.editor;

import java.io.Serializable;
import java.util.Objects;

/**
 * Stores edits made to a polygon (a "center") of a map.
 */
@SuppressWarnings("serial")
public class CenterEdit implements Serializable
{
	public boolean isWater;
	public boolean isLake;
	/**
	 * If this is null, then the generated region color is used if region colors are enabled.
	 */
	public Integer regionId;
	public CenterIcon icon;
	public CenterTrees trees;
	
	
	public final int index;
	
	public CenterEdit(int index, boolean isWater, boolean isLake, Integer regionId, CenterIcon icon, CenterTrees trees)
	{
		this.isWater = isWater;
		this.regionId = regionId;
		this.index = index;
		this.icon = icon;
		this.trees = trees;
		this.isLake = isLake;
	}

	@Override
	public int hashCode()
	{
		return index;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CenterEdit other = (CenterEdit) obj;
		return Objects.equals(icon, other.icon) && index == other.index && isLake == other.isLake && isWater == other.isWater
				&& Objects.equals(regionId, other.regionId) && Objects.equals(trees, other.trees);
	}
}
