package nortantis.editor;

import java.io.Serializable;
import java.util.Objects;

/**
 * The trees to draw at a center.
 *
 */
@SuppressWarnings("serial")
public class CenterTrees implements Serializable
{
	public String treeType;
	public double density;
	public long randomSeed;
	
	public CenterTrees(String treeType, double density, long seed)
	{
		this.treeType = treeType;
		this.density = density;
		this.randomSeed = seed;
	}
	
	public CenterTrees deepCopy()
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
