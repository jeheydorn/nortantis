package nortantis.editor;

import java.io.Serializable;
import java.util.Objects;

/**
 * Stores edits made to a polygon (a "center") of a map.
 */
@SuppressWarnings("serial")
public class CenterEdit implements Serializable
{
	public final boolean isWater;
	public final boolean isLake;
	/**
	 * If this is null, then the generated region color is used if region colors are enabled.
	 */
	public final Integer regionId;
	public final CenterIcon icon;
	public final CenterTrees trees;

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

	public CenterEdit copyWithIcon(CenterIcon icon)
	{
		return new CenterEdit(index, isWater, isLake, regionId, icon, trees);
	}

	public CenterEdit copyWithTrees(CenterTrees trees)
	{
		return new CenterEdit(index, isWater, isLake, regionId, icon, trees);
	}

	public CenterEdit copyWithRegionId(Integer regionId)
	{
		return new CenterEdit(index, isWater, isLake, regionId, icon, trees);
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
