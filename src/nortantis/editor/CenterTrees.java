package nortantis.editor;

import java.util.Objects;

/**
 * The trees to draw at a center.
 *
 */
public class CenterTrees
{
	public final String treeType;
	public final double density;
	public final long randomSeed;

	public CenterTrees(String treeType, double density, long randomSeed)
	{
		this.treeType = treeType;
		this.density = density;
		this.randomSeed = randomSeed;
	}
	
	public CenterTrees copyWithTreeType(String treeType)
	{
		return new CenterTrees(treeType, density, randomSeed);
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
		CenterTrees other = (CenterTrees) obj;
		return Double.doubleToLongBits(density) == Double.doubleToLongBits(other.density) && randomSeed == other.randomSeed
				&& Objects.equals(treeType, other.treeType);
	}
}
