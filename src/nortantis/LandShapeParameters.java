package nortantis;

import java.util.EnumMap;

/**
 * The tectonic plate settings that make each {@link LandShape} look the way it does. Every land-shape-specific knob used when creating
 * plates and their elevations lives here so that all shapes can be compared and tuned in one place.
 */
class LandShapeParameters
{
	/**
	 * How the base plate seeds are divided into continental and oceanic plates. Except for {@link #AllContinental}, each rule selects
	 * {@link LandShapeParameters#getMinContinentalPlateCount(int)} seeds, or more if {@link LandShapeParameters#minContinentalPlateFraction}
	 * requires it.
	 */
	enum SeedSelectionRule
	{
		/** The seeds farthest from the nearest map edge are continental. */
		FarthestFromEdge,
		/** The seeds closest to the nearest map edge are continental. */
		ClosestToEdge,
		/** Seeds chosen at random are continental. */
		Random,
		/**
		 * The seeds closest to a line segment through the map center along the map's longer axis are continental, so the selected area is a
		 * stadium (a rectangle with half-circle end caps) the same distance from all four sides of the map.
		 */
		NearestToCenterStadium,
		/** The seeds farthest along a random direction are continental, which selects a half-plane. */
		FarthestAlongRandomDirection,
		/** Every base seed is continental, except that with probability {@link #singleOceanicPlateProbability} one random seed is oceanic. */
		AllContinental
	}

	final SeedSelectionRule seedSelectionRule;

	/**
	 * The maximum number of extra oceanic plates added to large or many-region maps, as a fraction of the region count. 0 means no extra
	 * oceanic plates are added.
	 */
	final double maxExtraOceanicPlateRatio;

	/**
	 * Whether continental plates pay a higher cost to grow near the map edges, which tends to keep land away from the edges.
	 */
	final boolean biasContinentalGrowthAwayFromEdges;

	/**
	 * Multiplier applied to the elevation drop where two continental plates pull apart, for maps with the minimum region count. 1.0 applies
	 * the full drop. Lower values keep more of those boundaries above sea level, so continental land breaks apart into separate landmasses
	 * and inland seas less often. See {@link #getContinentalRiftScale(int)}.
	 */
	private final double continentalRiftScaleAtMinRegionCount;

	/**
	 * Like {@link #continentalRiftScaleAtMinRegionCount}, but for maps with the maximum region count. More regions means more plates and so
	 * more plate boundaries that can flood, which a lower value here can compensate for.
	 */
	private final double continentalRiftScaleAtMaxRegionCount;

	/**
	 * For {@link SeedSelectionRule#AllContinental}, the probability that one base plate is left oceanic.
	 */
	final double singleOceanicPlateProbability;

	/**
	 * The minimum fraction of the base plates that are continental. When this gives more continental plates than the region count, political
	 * region creation merges plates to reach the region count. Not used by {@link SeedSelectionRule#AllContinental}.
	 */
	final double minContinentalPlateFraction;

	/**
	 * The minimum number of continental plates. When this is more than the region count, political region creation merges plates to reach
	 * the region count. See {@link #getMinContinentalPlateCount(int)}.
	 */
	private final int minContinentalPlateCount;

	/**
	 * Multiplier applied to the elevation rise where two oceanic plates collide. 1.0 applies the full rise. Lower values raise fewer of those
	 * boundaries above sea level, so fewer long, narrow islands and peninsulas form along them.
	 */
	final double oceanicCollisionScale;

	/**
	 * The minimum number of continental plates per region. Values above 1 give political region creation spare plates to merge, so that
	 * regions do not have to be split when a continental plate ends up with too little land to form a region.
	 */
	private final double minContinentalPlatesPerRegion;

	/**
	 * When greater than 0, oceanic plates are added between the map edges and any continental seed closer than this to an edge, which keeps
	 * continental land from running into the edges. Measured in multiples of the typical distance between plate seeds.
	 */
	final double edgeGuardDistance;

	private static final EnumMap<LandShape, LandShapeParameters> parametersByShape = new EnumMap<>(LandShape.class);

	static
	{
		// Arguments: seed selection rule, max extra oceanic plate ratio, bias continental growth away from edges, continental rift scale at the
		// minimum and maximum region counts, single oceanic plate probability, min continental plate fraction, min continental plate count,
		// oceanic collision scale, min continental plates per region, edge guard distance.
		parametersByShape.put(LandShape.Continents, new LandShapeParameters(SeedSelectionRule.FarthestFromEdge, 0.9, true, 1.0, 1.0, 0.0, 0.0, 8, 0.5, 1.25, 0.6));
		parametersByShape.put(LandShape.Inland_Sea, new LandShapeParameters(SeedSelectionRule.ClosestToEdge, 0.0, false, 1.0, 1.0, 0.0, 0.0, 0, 1.0, 1.0, 0.0));
		parametersByShape.put(LandShape.Scattered, new LandShapeParameters(SeedSelectionRule.Random, 0.9, false, 1.0, 1.0, 0.0, 0.0, 0, 1.0, 1.0, 0.0));
		parametersByShape.put(LandShape.Supercontinent, new LandShapeParameters(SeedSelectionRule.NearestToCenterStadium, 0.0, true, 0.2, 0.2, 0.0, 0.0, 8, 0.5, 1.25, 1.0));
		parametersByShape.put(LandShape.Coastline, new LandShapeParameters(SeedSelectionRule.FarthestAlongRandomDirection, 0.0, false, 0.2, 0.2, 0.0, 0.45, 0, 1.0, 1.0, 0.0));
		parametersByShape.put(LandShape.Landlocked, new LandShapeParameters(SeedSelectionRule.AllContinental, 0.0, false, 0.45, 0.25, 0.2, 0.0, 0, 1.0, 1.0, 0.0));
		assert parametersByShape.size() == LandShape.values().length;
	}

	private LandShapeParameters(SeedSelectionRule seedSelectionRule, double maxExtraOceanicPlateRatio, boolean biasContinentalGrowthAwayFromEdges,
			double continentalRiftScaleAtMinRegionCount, double continentalRiftScaleAtMaxRegionCount, double singleOceanicPlateProbability,
			double minContinentalPlateFraction, int minContinentalPlateCount, double oceanicCollisionScale, double minContinentalPlatesPerRegion,
			double edgeGuardDistance)
	{
		this.seedSelectionRule = seedSelectionRule;
		this.maxExtraOceanicPlateRatio = maxExtraOceanicPlateRatio;
		this.biasContinentalGrowthAwayFromEdges = biasContinentalGrowthAwayFromEdges;
		this.continentalRiftScaleAtMinRegionCount = continentalRiftScaleAtMinRegionCount;
		this.continentalRiftScaleAtMaxRegionCount = continentalRiftScaleAtMaxRegionCount;
		this.singleOceanicPlateProbability = singleOceanicPlateProbability;
		this.minContinentalPlateFraction = minContinentalPlateFraction;
		this.minContinentalPlateCount = minContinentalPlateCount;
		this.oceanicCollisionScale = oceanicCollisionScale;
		this.minContinentalPlatesPerRegion = minContinentalPlatesPerRegion;
		this.edgeGuardDistance = edgeGuardDistance;
	}

	/**
	 * Returns how many continental plates to create before {@link #minContinentalPlateFraction} is applied: the region count times
	 * {@link #minContinentalPlatesPerRegion} rounded up, or {@link #minContinentalPlateCount} if that is larger.
	 */
	int getMinContinentalPlateCount(int regionCount)
	{
		return Math.max((int) Math.ceil(regionCount * minContinentalPlatesPerRegion), minContinentalPlateCount);
	}

	/**
	 * Returns the multiplier applied to the elevation drop where two continental plates pull apart, interpolated linearly by region count
	 * between {@link #continentalRiftScaleAtMinRegionCount} and {@link #continentalRiftScaleAtMaxRegionCount}.
	 */
	double getContinentalRiftScale(int regionCount)
	{
		if (continentalRiftScaleAtMinRegionCount == continentalRiftScaleAtMaxRegionCount)
		{
			return continentalRiftScaleAtMinRegionCount;
		}
		double t = (regionCount - SettingsGenerator.minRegionCount) / (double) (SettingsGenerator.maxRegionCount - SettingsGenerator.minRegionCount);
		t = Math.max(0.0, Math.min(1.0, t));
		return continentalRiftScaleAtMinRegionCount + t * (continentalRiftScaleAtMaxRegionCount - continentalRiftScaleAtMinRegionCount);
	}

	/**
	 * Returns the parameters for the given land shape. A null land shape, which maps created before land shapes existed have, uses the
	 * parameters for {@link LandShape#Continents}.
	 */
	static LandShapeParameters forLandShape(LandShape landShape)
	{
		return parametersByShape.get(landShape == null ? LandShape.Continents : landShape);
	}
}
