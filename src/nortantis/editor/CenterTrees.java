package nortantis.editor;

import java.util.Objects;

/**
 * The trees to draw at a center.
 *
 */
public class CenterTrees
{
	public final String artPack;
	public final String treeType;
	public final double density;
	public final long randomSeed;
	public final boolean isDormant;
	/**
	 * The specific colors to draw these trees with, or null to use the map's per-type tree colors (the normal case). This lets dormant
	 * trees reappear with the color they were originally drawn with rather than the current per-type tree color, and lets sub-map
	 * redistribution keep the colors of the source trees. See {@link IconColors}.
	 */
	public final IconColors colors;

	public CenterTrees(String artPack, String treeType, double density, long randomSeed)
	{
		this(artPack, treeType, density, randomSeed, false, null);
	}

	public CenterTrees(String artPack, String treeType, double density, long randomSeed, boolean isDormant, IconColors colors)
	{
		assert artPack != null;
		this.artPack = artPack;
		assert treeType != null;
		this.treeType = treeType;
		this.density = density;
		this.randomSeed = randomSeed;
		this.isDormant = isDormant;
		this.colors = colors;
	}

	/**
	 * Copies this CenterTrees with the given artPack and treeType.
	 *
	 * @param artPack The art pack the given treeType comes from. Must be an art pack that contains treeType.
	 * @param treeType A string identifying the folder name the tree images come from under the tree assets in the art pack.
	 * @return A copy with the given art pack and tree type
	 */
	public CenterTrees copyWithTreeType(String artPack, String treeType)
	{
		return new CenterTrees(artPack, treeType, density, randomSeed, isDormant, colors);
	}

	public CenterTrees copyWithArtPack(String artPack)
	{
		return new CenterTrees(artPack, treeType, density, randomSeed, isDormant, colors);
	}

	public CenterTrees copyWithIsDormant(boolean isDormant)
	{
		return new CenterTrees(artPack, treeType, density, randomSeed, isDormant, colors);
	}

	public CenterTrees copyWithColors(IconColors colors)
	{
		return new CenterTrees(artPack, treeType, density, randomSeed, isDormant, colors);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(artPack, density, isDormant, randomSeed, treeType, colors);
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		if (obj == null)
		{
			return false;
		}
		if (getClass() != obj.getClass())
		{
			return false;
		}
		CenterTrees other = (CenterTrees) obj;
		return Objects.equals(artPack, other.artPack) && Double.doubleToLongBits(density) == Double.doubleToLongBits(other.density) && isDormant == other.isDormant && randomSeed == other.randomSeed
				&& Objects.equals(treeType, other.treeType) && Objects.equals(colors, other.colors);
	}

}
