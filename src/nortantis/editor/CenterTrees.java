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

	public CenterTrees(String artPack, String treeType, double density, long randomSeed)
	{
		this(artPack, treeType, density, randomSeed, false);
	}

	public CenterTrees(String artPack, String treeType, double density, long randomSeed, boolean isDormant)
	{
		assert artPack != null;
		this.artPack = artPack;
		assert treeType != null;
		this.treeType = treeType;
		this.density = density;
		this.randomSeed = randomSeed;
		this.isDormant = isDormant;
	}

	public CenterTrees copyWithTreeType(String treeType)
	{
		return new CenterTrees(artPack, treeType, density, randomSeed, isDormant);
	}

	public CenterTrees copyWithArtPack(String artPack)
	{
		return new CenterTrees(artPack, treeType, density, randomSeed, isDormant);
	}

	public CenterTrees copyWithIsDormant(boolean isDormant)
	{
		return new CenterTrees(artPack, treeType, density, randomSeed, isDormant);
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
		return Objects.equals(artPack, other.artPack) && Double.doubleToLongBits(density) == Double.doubleToLongBits(other.density)
				&& isDormant == other.isDormant && randomSeed == other.randomSeed && Objects.equals(treeType, other.treeType);
	}

}
