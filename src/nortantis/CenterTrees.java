package nortantis;

import java.io.Serializable;

/**
 * The trees to draw at a center.
 *
 */
@SuppressWarnings("serial")
public class CenterTrees implements Serializable
{
	public String treeType;
	double density;
	long randomSeed;
	
	public CenterTrees(String treeType, double density, long seed)
	{
		this.treeType = treeType;
		this.density = density;
		this.randomSeed = seed;
	}
}
