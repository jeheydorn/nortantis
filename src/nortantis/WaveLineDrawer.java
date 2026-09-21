package nortantis;

import nortantis.MapSettings.ConcentricLineMode;
import nortantis.MapSettings.OceanWaves;
import nortantis.MapSettings.WaveLineShape;
import nortantis.WorldGraph.CoastlineCurve;
import nortantis.geom.FloatPoint;
import nortantis.geom.Point;
import nortantis.geom.Rectangle;
import nortantis.graph.voronoi.Center;
import nortantis.platform.Color;
import nortantis.platform.DrawQuality;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.Painter;
import nortantis.platform.PixelReader;
import nortantis.util.Helper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.DoubleSupplier;
import java.util.function.DoubleUnaryOperator;

/**
 * Draws the rows of short horizontal wave lines that the "Wave lines" ocean wave style stacks outside the concentric line around coastlines,
 * and the rows of wave dashes that the "Ripples" style draws there instead.
 *
 * Every random value used here is a function of the row index and a position along the row in resolution-invariant units, never of where
 * a coastline or a stroke starts. That keeps each stroke determined by what is near it, so an incremental draw produces the same strokes as
 * a full draw, and the same map at a different resolution gets the same strokes, scaled.
 *
 * Sizes in "units" are multiplied by the unrounded size multiplier to get pixels.
 */
public class WaveLineDrawer
{
	/**
	 * How many standard deviations the random value that picks a wave line's reach may be from the mean before it is clamped.
	 */
	private static final double maxReachStandardDeviations = 2.0;
	/**
	 * At the highest length variation, the most a wave line's reach can differ from the wave line length, as a fraction of that length.
	 */
	private static final double maxLengthVariationAsFractionOfLength = 0.9;
	private static final double amplitudeAsFractionOfRowHeight = 0.22;
	private static final double wavelengthAsMultipleOfRowHeight = 1.6;
	private static final double maxJitterAmplitudeAsFractionOfRowSpacing = 0.3;
	private static final double jitterControlPointSpacingAsMultipleOfWavelength = 1.0;
	/**
	 * With jitter on, the most a crest is moved along its row, as a fraction of the wavelength. This must stay small enough that crests never
	 * move past each other; see sampleSmoothNoise for the slope bound this relies on.
	 */
	private static final double maxCrestShiftInWavelengths = 0.35;
	/**
	 * With jitter on, the lowest a crest can be, as a fraction of the amplitude.
	 */
	private static final double minCrestHeightFraction = 0.3;
	private static final int cornerBisectionIterations = 20;
	/**
	 * Catmull-Rom interpolation of values in [-1, 1] can reach up to this magnitude between control points.
	 */
	private static final double catmullRomMaxOvershoot = 1.25;
	/**
	 * Space kept between neighboring rows however far they are shifted.
	 */
	private static final double minGapBetweenRows = 0.5;
	private static final double minPieceLength = 3.0;
	private static final double minBreakDrawLengthInWavelengths = 0.75;
	private static final double maxBreakDrawLengthInWavelengths = 5.0;
	private static final double minBreakSkipLengthInWavelengths = 0.2;
	private static final double maxBreakSkipLengthInWavelengths = 0.6;
	/**
	 * The smallest space a break leaves between the ends of the strokes on either side of it, as a multiple of the stroke width. Narrower
	 * breaks read as a flaw in a line rather than as a lifted pen.
	 */
	private static final double minBreakGapAsMultipleOfStrokeWidth = 1.0;
	private static final double lengthNoiseControlPointSpacingAsMultipleOfRowSpacing = 6.0;
	private static final int bisectionIterations = 8;
	/**
	 * Where rows stop short of a concentric line that isn't drawn, each end is pulled back from it by up to this many wavelengths more than
	 * keeps its round cap clear of it, so that the rows don't all end along one curve.
	 */
	private static final double maxLineEndPullBackInWavelengths = 1.5;

	/**
	 * For wave dashes, the Gaussian blur of the land that shapes where they end is this wide horizontally, as a fraction of the wave line
	 * length. See {@link DashLens}.
	 */
	private static final double dashLensSigmaAsFractionOfLength = 0.6;
	/**
	 * How many times wider the blur that shapes wave dashes is horizontally than vertically. The rows run horizontally, so this makes wave
	 * dashes reach much farther to the sides of land than above and below it.
	 */
	private static final double dashLensAnisotropy = 3.0;
	/**
	 * The blur is computed on a grid of blocks this many times smaller than its vertical standard deviation, which keeps it fast without
	 * showing the blocks.
	 */
	private static final double dashLensBlocksPerVerticalSigma = 3.0;
	private static final int minDashLensBlockSize = 2;
	/**
	 * Wave dashes always reach at least this fraction of their reach from the concentric line, measured directly, so that small islands, which
	 * the blur makes little of, still get some.
	 */
	private static final double minDashReachAsFractionOfReach = 0.45;
	/**
	 * Rows pass over the concentric line where it keeps less than this many wavelengths of the row clear and touches no land.
	 * See {@link #passOverGrazedLine}.
	 */
	private static final double maxGrazeToPassOverInWavelengths = 1.5;
	/**
	 * How far apart, as a multiple of the row spacing, the random values are that vary how far wave dashes reach.
	 */
	private static final double dashReachNoiseSpacingAsMultipleOfRowSpacing = 3.0;
	/**
	 * The random value that varies how far wave dashes reach is scaled by this before it picks a reach, so that it spans the reach
	 * distribution's range.
	 */
	private static final double dashReachNoiseScale = 2.5;
	/**
	 * Each row of wave dashes is one unbroken stroke out to between these fractions of the way to where the dashes end, and breaks into
	 * dashes past that.
	 */
	private static final double minSolidDashFraction = 0.2;
	private static final double maxSolidDashFraction = 0.5;
	private static final double solidDashNoiseSpacingInWavelengths = 4.0;
	private static final double minDashLengthInWavelengths = 0.7;
	private static final double maxDashLengthInWavelengths = 1.8;
	private static final double minDashGapAsMultipleOfStrokeWidth = 2.5;
	private static final double maxDashGapAsMultipleOfStrokeWidth = 4.0;
	private static final double minExtraDashGapInWavelengths = 0.1;
	private static final double maxExtraDashGapInWavelengths = 0.6;
	/**
	 * At the far end of wave dashes, each dash is shortened by this fraction of its length.
	 */
	private static final double dashShrinkAtEnd = 0.55;
	/**
	 * The gap after a dash grows by this fraction for each fraction of the way out the dash is.
	 */
	private static final double dashGapGrowth = 2.0;
	/**
	 * Besides a gap, the first dash after the unbroken part of a row starts up to this many wavelengths past it.
	 */
	private static final double maxFirstDashOffsetInWavelengths = 0.3;
	/**
	 * Dashes cut shorter than this many wavelengths where their series ends are left out.
	 */
	private static final double minDashLengthToDrawInWavelengths = 0.5;
	/**
	 * A dash is drawn with probability (1 - fraction of the way out) raised to this power, so dashes thin out toward where they end.
	 */
	private static final double dashKeepProbabilityExponent = 0.8;
	/**
	 * Each end of a dash narrows over this fraction of half the dash's length, the way a pen thins as it lifts off the paper.
	 */
	private static final double dashTaperAsFractionOfHalfLength = 0.35;
	/**
	 * The outer end of the unbroken part of a row of wave dashes narrows over this many wavelengths.
	 */
	private static final double solidDashTaperInWavelengths = 0.4;
	/**
	 * How wide the tip of a tapered stroke is, as a fraction of the stroke width.
	 */
	private static final double taperTipWidthFraction = 0.15;
	/**
	 * How much of its height a wave loses by the tip of a tapered stroke.
	 */
	private static final double taperFlattening = 0.5;
	/**
	 * How much a tapered stroke's width varies along it, as a fraction of its width, like the changing pressure of a pen.
	 */
	private static final double taperPressureVariation = 0.12;
	private static final double taperPressureNoiseSpacing = 12.0;
	private static final int roundCapSegments = 6;

	private static final int outsideLevel = 0;
	private static final int bandLevel = 128;
	private static final int keepOutLevel = 255;

	private static final byte outsideClass = 0;
	private static final byte bandClass = 1;
	private static final byte keepOutClass = 2;

	private static final long rowShiftSalt = 0x5A17C0DE01L;
	private static final long rowPhaseSalt = 0x5A17C0DE02L;
	private static final long leftEndLengthSalt = 0x5A17C0DE03L;
	private static final long jitterSalt = 0x5A17C0DE04L;
	private static final long breakSalt = 0x5A17C0DE05L;
	private static final long crestShiftSalt = 0x5A17C0DE06L;
	private static final long crestHeightSalt = 0x5A17C0DE07L;
	private static final long rightEndLengthSalt = 0x5A17C0DE08L;
	private static final long dashReachSalt = 0x5A17C0DE09L;
	private static final long solidDashSalt = 0x5A17C0DE0AL;
	private static final long dashSalt = 0x5A17C0DE0BL;
	private static final long dashKeepSalt = 0x5A17C0DE0CL;
	private static final long taperPressureSalt = 0x5A17C0DE0DL;
	private static final long lineEndSalt = 0x5A17C0DE0EL;

	private final MapSettings settings;
	private final double resolutionScale;
	private final double sizeMultiplier;
	private final double strokeWidth;
	private final double strokeWidthInUnits;
	/**
	 * Whether to draw wave dashes rather than wave lines.
	 */
	private final boolean isDashes;
	private final double rowSpacing;
	/**
	 * Whether rows stop short of a concentric line that isn't drawn, so that their ends there show.
	 */
	private final boolean areLineEndsVisible;
	private final double amplitude;
	private final double wavelength;
	private final double jitterAmplitude;
	private final double jitterFraction;
	private final double minRowSeparation;
	private final double maxRowShift;
	private final ReachDistribution reachDistribution;
	/**
	 * Reused by {@link #calcDistanceBeyondReach}, which is called for every pixel of every row's band.
	 */
	private final SegmentGrid.Nearest nearestOnCurve = new SegmentGrid.Nearest();
	/**
	 * For wave dashes, the blurred land that shapes where they end, built for the area being drawn.
	 */
	private DashLens dashLens;
	/**
	 * The bounds of the whole map, in graph coordinates.
	 */
	private Rectangle mapBounds;

	public WaveLineDrawer(MapSettings settings, double resolutionScale)
	{
		this.settings = settings;
		this.resolutionScale = resolutionScale;
		sizeMultiplier = MapCreator.calcSizeMultiplierFromResolutionScale(resolutionScale);
		strokeWidth = calcStrokeWidth(settings, resolutionScale);
		strokeWidthInUnits = calcStrokeWidthInUnits(settings);
		isDashes = settings.oceanWavesType == OceanWaves.WaveDashes;
		rowSpacing = calcRowSpacing(settings);
		areLineEndsVisible = settings.getWaveRowStyle().lineMode() == ConcentricLineMode.HiddenRowsKeepDistance;
		amplitude = calcAmplitude(settings);
		wavelength = calcWavelength(settings);
		jitterAmplitude = calcJitterAmplitude(settings);
		jitterFraction = calcJitterFraction(settings);
		minRowSeparation = calcMinRowSeparation(settings);
		maxRowShift = calcMaxRowShift(settings);
		reachDistribution = ReachDistribution.create(settings);
	}

	private static double calcStrokeWidth(MapSettings settings, double resolutionScale)
	{
		return MapCreator.calcConcentricWaveVisibleLineWidth(settings, resolutionScale);
	}

	private static double calcStrokeWidthInUnits(MapSettings settings)
	{
		return calcStrokeWidth(settings, 1.0) / MapCreator.calcSizeMultiplierFromResolutionScale(1.0);
	}

	private static double calcAmplitude(MapSettings settings)
	{
		return settings.getWaveRowStyle().rowHeight() * amplitudeAsFractionOfRowHeight;
	}

	private static double calcWavelength(MapSettings settings)
	{
		return settings.getWaveRowStyle().rowHeight() * wavelengthAsMultipleOfRowHeight;
	}

	/**
	 * The distance between the baselines of neighboring rows before row spacing variation, in units: the least that keeps them from touching,
	 * plus the row gap.
	 */
	private static double calcRowSpacing(MapSettings settings)
	{
		return calcMinRowSeparation(settings) + calcSpaceBetweenRowsWithoutJitter(settings);
	}

	/**
	 * The space between neighboring rows, in units, that neither their waves, stroke width nor the gap kept between them use.
	 */
	private static double calcSpaceBetweenRowsWithoutJitter(MapSettings settings)
	{
		return Math.max(0, settings.getWaveRowStyle().rowGap());
	}

	/**
	 * The row gap that spaces rows of the given height as far apart as the given row spacing did when row spacing set both the height of
	 * rows and the distance between them.
	 *
	 * @param lineWidth
	 *            The line width, in pixels at resolution 1.
	 */
	public static int calcRowGapMatchingRowSpacing(int rowSpacing, double lineWidth)
	{
		double strokeWidthInUnits = lineWidth / MapCreator.calcSizeMultiplierFromResolutionScale(1.0);
		double minRowSeparation = rowSpacing * amplitudeAsFractionOfRowHeight + strokeWidthInUnits + minGapBetweenRows;
		return (int) Math.max(0, Math.round(rowSpacing - minRowSeparation));
	}

	/**
	 * The most a row's jitter moves it up or down, in units, which is what a row gets when it is evenly spaced from both neighbors. See
	 * {@link #getRowJitterAmplitude}.
	 */
	private static double calcJitterAmplitude(MapSettings settings)
	{
		// Jitter moves both neighboring rows, so each gets half of the space between them.
		return calcJitterFraction(settings)
				* Math.min(calcRowSpacing(settings) * maxJitterAmplitudeAsFractionOfRowSpacing, calcSpaceBetweenRowsWithoutJitter(settings) / 2.0);
	}

	/**
	 * How much of the jitter the settings ask for, from 0 when jitter is off to 1 at the highest jitter level.
	 */
	private static double calcJitterFraction(MapSettings settings)
	{
		if (!settings.getWaveRowStyle().jitter())
		{
			return 0.0;
		}
		return Math.max(0, Math.min(MapSettings.maxJitterLevel, settings.getWaveRowStyle().jitterLevel())) / (double) MapSettings.maxJitterLevel;
	}

	/**
	 * How close, in units, the baselines of neighboring rows can be without the rows touching, before jitter.
	 */
	private static double calcMinRowSeparation(MapSettings settings)
	{
		// The upper row reaches down by half its stroke width. The lower row reaches up by its waves and half its stroke width.
		return calcAmplitude(settings) + calcStrokeWidthInUnits(settings) + minGapBetweenRows;
	}

	/**
	 * The largest distance, in units, that a row can be shifted up or down from its evenly spaced position. See {@link #getRowY}.
	 */
	private static double calcMaxRowShift(MapSettings settings)
	{
		double largestShift = calcSpaceBetweenRowsWithoutJitter(settings);
		return largestShift * Math.max(0, Math.min(MapSettings.maxWaveLineVariation, settings.getWaveRowStyle().rowSpacingVariation())) / (double) MapSettings.maxWaveLineVariation;
	}

	/**
	 * The farthest a wave line can reach out from the concentric line, in units.
	 */
	private static double calcMaxReach(MapSettings settings)
	{
		ReachDistribution distribution = ReachDistribution.create(settings);
		return distribution == null ? 0.0 : distribution.getMaxReach();
	}

	/**
	 * The distance, in pixels, from a coastline curve to the farthest a wave line or wave dash can reach.
	 */
	private static double calcBandRadius(MapSettings settings, double resolutionScale)
	{
		if (settings.oceanWavesType == OceanWaves.WaveDashes)
		{
			// Past the blur's reach, no land adds to it, so wave dashes can't reach there.
			DashLens lens = DashLens.create(settings, resolutionScale);
			double lensRadius = lens == null ? 0.0 : lens.calcSupport() + calcConcentricLineJitter(settings, resolutionScale);
			return Math.max(lensRadius, calcMinDashBandRadius(settings, resolutionScale));
		}
		return calcInnerEdgeRadius(settings, resolutionScale) + calcMaxReach(settings) * MapCreator.calcSizeMultiplierFromResolutionScale(resolutionScale);
	}

	/**
	 * The distance, in pixels, from a coastline curve to where rows start: the outer edge of the concentric line, or of the coastline for
	 * rows that reach the shore.
	 */
	private static double calcInnerEdgeRadius(MapSettings settings, double resolutionScale)
	{
		if (settings.getWaveRowStyle().lineMode() == ConcentricLineMode.HiddenRowsReachShore)
		{
			return settings.coastlineWidth * resolutionScale / 2.0;
		}
		return MapCreator.calcWaveLinesConcentricLineOuterWidth(settings, resolutionScale) / 2.0;
	}

	/**
	 * The distance, in pixels, from a coastline curve to the farthest wave dashes reach where only their minimum reach keeps them.
	 */
	private static double calcMinDashBandRadius(MapSettings settings, double resolutionScale)
	{
		return calcInnerEdgeRadius(settings, resolutionScale)
				+ calcMaxReach(settings) * minDashReachAsFractionOfReach * MapCreator.calcSizeMultiplierFromResolutionScale(resolutionScale);
	}

	private static double calcConcentricLineJitter(MapSettings settings, double resolutionScale)
	{
		return MapCreator.calcJitter(settings, resolutionScale);
	}

	/**
	 * How far, in pixels, a change on the map can move what wave lines and their concentric line draw: the band around the changed part of the
	 * coastline, and how far strokes in it reach off their rows. Incremental draws must pad each side of the area they draw by at least this
	 * much.
	 */
	public static double calcEffectsPadding(MapSettings settings, double resolutionScale)
	{
		double sizeMultiplier = MapCreator.calcSizeMultiplierFromResolutionScale(resolutionScale);
		double concentricLinePadding = MapCreator.calcWaveLinesConcentricLineOuterWidth(settings, resolutionScale) + calcConcentricLineJitter(settings, resolutionScale);
		// Whether a row passes over a graze of the concentric line depends on the row up to a graze's length away.
		double bandPadding = calcBandRadius(settings, resolutionScale) + calcConcentricLineJitter(settings, resolutionScale)
				+ maxGrazeToPassOverInWavelengths * calcWavelength(settings) * sizeMultiplier;
		if (settings.oceanWavesType == OceanWaves.WaveDashes)
		{
			// How far out a point is depends on its run up to a band radius away, whether a dash is drawn depends on its middle, so a change
			// can reach half a dash farther, and the blur is computed in blocks.
			DashLens lens = DashLens.create(settings, resolutionScale);
			bandPadding += calcBandRadius(settings, resolutionScale) + maxDashLengthInWavelengths * calcWavelength(settings) * sizeMultiplier / 2.0
					+ (lens == null ? 0.0 : 2.0 * lens.blockSize);
		}
		// How far a stroke's waves, jitter, row shift and width reach off its row.
		double offRowPadding = (calcAmplitude(settings) + calcJitterAmplitude(settings) + calcMaxRowShift(settings)) * sizeMultiplier + calcStrokeWidth(settings, resolutionScale);
		return Math.max(concentricLinePadding, bandPadding + offRowPadding);
	}

	/**
	 * The shortest a break may be, in units. Round caps make each stroke reach half its width past the end of its piece, so the space a break
	 * leaves is its length less the stroke width.
	 */
	private double calcMinBreakLength()
	{
		return strokeWidthInUnits * (1.0 + minBreakGapAsMultipleOfStrokeWidth);
	}

	/**
	 * How far a stroke's inner end reaches under the concentric line, in pixels.
	 */
	private static double calcOverhang(double strokeWidth)
	{
		return strokeWidth + 2.0;
	}

	/**
	 * Draws wave lines or wave dashes in white into target. Whether a point is part of one depends only on the coastlines and land within a
	 * band radius of it, so the strokes drawn depend only on the map, not on what area is drawn, except within {@link #calcEffectsPadding} of
	 * the drawn area's edges.
	 *
	 * @param target
	 *            A grayscale image covering drawBounds.
	 * @param curves
	 *            The lines the concentric line is drawn along, including every one that passes through drawBounds.
	 * @param landMask
	 *            A binary image covering drawBounds that is white on land.
	 * @param centersToDraw
	 *            The centers overlapping drawBounds, or null to use all centers.
	 * @param drawBounds
	 *            The area of the map the target covers, in graph coordinates.
	 */
	public void drawWaveLines(Image target, WorldGraph graph, List<CoastlineCurve> curves, Image landMask, Collection<Center> centersToDraw, Rectangle drawBounds)
	{
		if (reachDistribution == null)
		{
			return;
		}

		int width = landMask.getWidth();
		int height = landMask.getHeight();
		double concentricLineOuterRadius = calcInnerEdgeRadius(settings, resolutionScale);
		mapBounds = graph.bounds;
		double bandRadius = calcBandRadius(settings, resolutionScale);
		if (isDashes)
		{
			dashLens = DashLens.create(settings, resolutionScale);
			dashLens.build(landMask, drawBounds, graph.bounds);
		}

		try (Image guide = Image.create(width, height, ImageType.Grayscale8Bit))
		{
			drawGuide(guide, graph, curves, concentricLineOuterRadius, bandRadius, centersToDraw, drawBounds);

			SegmentGrid segmentGrid = new SegmentGrid(curves, drawBounds, bandRadius + 1.0);

			try (PixelReader guidePixels = guide.createPixelReader(); PixelReader landPixels = landMask.createPixelReader(); Painter p = target.createPainter(DrawQuality.High))
			{
				p.setColor(Color.white);
				p.setBasicStroke((float) strokeWidth);

				// Include rows whose waves, jitter and stroke width reach into the target from just outside it.
				double rowReach = (amplitude + jitterAmplitude) * sizeMultiplier + strokeWidth;
				byte[] classes = new byte[width];
				boolean[] isLand = new boolean[width];
				int firstRow = (int) Math.floor(((drawBounds.y - rowReach) / sizeMultiplier - maxRowShift) / rowSpacing);
				int lastRow = (int) Math.ceil(((drawBounds.y + drawBounds.height + rowReach) / sizeMultiplier + maxRowShift) / rowSpacing);
				for (int row = firstRow; row <= lastRow; row++)
				{
					double yInGraph = getRowY(row) * sizeMultiplier;
					// A row just off the top or bottom of the map can still reach onto it, so it is placed using the nearest row of pixels on the
					// map. That way a full draw, which has no pixels off the map, and an incremental draw that reaches past the map's edge place it
					// the same way. What it draws off the map is clipped.
					double yOnMap = Math.max(graph.bounds.y, Math.min(graph.bounds.y + graph.bounds.height - 1.0, yInGraph));
					int pixelRow = (int) Math.floor(yOnMap - drawBounds.y);
					if (pixelRow < 0 || pixelRow >= height)
					{
						continue;
					}

					for (int x = 0; x < width; x++)
					{
						double xInGraph = x + drawBounds.x;
						isLand[x] = false;
						if (xInGraph < graph.bounds.x || xInGraph >= graph.bounds.x + graph.bounds.width)
						{
							// Strokes that reach the map's left or right edge continue past it, the same as at the edge of a full draw.
							classes[x] = keepOutClass;
							isLand[x] = true;
						}
						else if (landPixels.getNormalizedPixelLevel(x, pixelRow) > 0.5f)
						{
							classes[x] = keepOutClass;
							isLand[x] = true;
						}
						else
						{
							int level = guidePixels.getGrayLevel(x, pixelRow);
							classes[x] = level > (bandLevel + keepOutLevel) / 2 ? keepOutClass : level > (outsideLevel + bandLevel) / 2 ? bandClass : outsideClass;
						}
					}

					passOverGrazedLine(classes, isLand, maxGrazeToPassOverInWavelengths * wavelength * sizeMultiplier);
					if (isDashes)
					{
						drawDashRow(p, row, yInGraph, classes, segmentGrid, concentricLineOuterRadius, bandRadius, drawBounds);
					}
					else
					{
						drawRow(p, row, yInGraph, classes, segmentGrid, concentricLineOuterRadius, drawBounds);
					}
				}
			}
		}
	}

	/**
	 * Draws where wave lines may go: a band reaching as far out from each coastline curve as a wave line can, and inside it, the area covered
	 * by the concentric line and everything between it and the coast, which wave lines are kept out of.
	 */
	private void drawGuide(Image guide, WorldGraph graph, List<CoastlineCurve> curves, double concentricLineOuterRadius, double bandRadius, Collection<Center> centers,
			Rectangle bounds)
	{
		try (Painter p = guide.createPainter())
		{
			p.setColor(Color.create(bandLevel, bandLevel, bandLevel));
			p.setBasicStroke((float) (2.0 * bandRadius));
			graph.drawCoastlineCurves(p, curves, 0, false, bounds, null);

			Color keepOutColor = Color.create(keepOutLevel, keepOutLevel, keepOutLevel);
			p.setColor(keepOutColor);
			p.setStrokeToSolidLineWithNoEndDecorations((float) (2.0 * concentricLineOuterRadius));
			graph.drawCoastlineCurves(p, curves, 0, false, bounds, null);

			if (!settings.drawOceanEffectsInLakes)
			{
				graph.drawPolygons(p, centers, bounds, c -> c.isLake ? keepOutColor : null);
			}
		}
	}

	/**
	 * The y coordinate of a row's baseline, in units.
	 *
	 * Even rows are shifted randomly by up to maxRowShift. Odd rows are also shifted randomly, but kept far enough from the even rows on
	 * either side of them not to touch. Because maxRowShift leaves room for an odd row between any two even rows, this allows twice the shift
	 * that shifting every row independently would, while each row's position still depends only on its own index and its neighbors'.
	 */
	private double getRowY(int row)
	{
		if (maxRowShift == 0.0)
		{
			return row * rowSpacing;
		}

		double y = getShiftedRowY(row);
		if (Math.floorMod(row, 2) == 0)
		{
			return y;
		}
		double highest = getShiftedRowY(row - 1) + minRowSeparation;
		double lowest = getShiftedRowY(row + 1) - minRowSeparation;
		return Math.max(highest, Math.min(lowest, y));
	}

	private double getShiftedRowY(int row)
	{
		return row * rowSpacing + maxRowShift * (2.0 * uniform(rowShiftSalt, row, 0) - 1.0);
	}

	/**
	 * The most jitter moves a row up or down, in units. Each row gets at most half the space left between it and each neighbor, so the jitter
	 * of two neighboring rows can't make them touch. Rows that row spacing variation moved close to a neighbor get less jitter, and rows that
	 * are evenly spaced get the full amount.
	 */
	private double getRowJitterAmplitude(int row)
	{
		if (jitterAmplitude == 0.0)
		{
			return 0.0;
		}
		double y = getRowY(row);
		double spaceAbove = y - getRowY(row - 1) - minRowSeparation;
		double spaceBelow = getRowY(row + 1) - y - minRowSeparation;
		return Math.min(jitterAmplitude, Math.max(0.0, Math.min(spaceAbove, spaceBelow) / 2.0));
	}

	/**
	 * Draws the strokes of one row.
	 *
	 * @param classes
	 *            For each pixel along the row in drawBounds, whether it is outside the band, in it, or kept clear of wave lines.
	 */
	private void drawRow(Painter p, int row, double yInGraph, byte[] classes, SegmentGrid segmentGrid, double concentricLineOuterRadius, Rectangle drawBounds)
	{
		int width = classes.length;
		double overhang = calcOverhang(strokeWidth);
		double rowJitterAmplitude = getRowJitterAmplitude(row);
		RowLengthNoise lengthNoise = new RowLengthNoise(row);
		BreakPattern breakPattern = null;

		int x = 0;
		while (x < width)
		{
			if (classes[x] != bandClass)
			{
				x++;
				continue;
			}
			int runStart = x;
			while (x < width && classes[x] == bandClass)
			{
				x++;
			}
			int runEnd = x;

			// A run that starts or ends where wave lines are kept out reaches the concentric line there, and one that starts or ends at the
			// edge of the area being drawn continues past it.
			boolean reachesLineAtStart = runStart == 0 || classes[runStart - 1] == keepOutClass;
			boolean reachesLineAtEnd = runEnd == width || classes[runEnd] == keepOutClass;
			boolean isStartVisible = isVisibleLineEnd(classes, runStart - 1, drawBounds);
			boolean isEndVisible = isVisibleLineEnd(classes, runEnd, drawBounds);

			List<double[]> stretches = new ArrayList<>();
			double stretchStart = 0.0;
			boolean isInStretch = false;
			double previousX = Double.NaN;
			double previousDistanceBeyondReach = 0.0;
			for (int pixel = runStart; pixel < runEnd; pixel++)
			{
				double xInGraph = pixel + drawBounds.x;
				double distanceBeyondReach = calcDistanceBeyondReach(row, xInGraph, yInGraph, segmentGrid, concentricLineOuterRadius, lengthNoise);
				boolean isTouchingLine = (pixel == runStart && reachesLineAtStart) || (pixel == runEnd - 1 && reachesLineAtEnd);
				boolean isWithinReach = distanceBeyondReach <= 0.0 || isTouchingLine;

				if (isWithinReach && !isInStretch)
				{
					stretchStart = pixel == runStart && reachesLineAtStart ? (isStartVisible ? xInGraph + calcLineEndPullBack(row, xInGraph) : xInGraph - overhang)
							: Double.isNaN(previousX) || isTouchingLine ? xInGraph
									: findReachCrossing(row, previousX, previousDistanceBeyondReach, xInGraph, distanceBeyondReach, yInGraph, segmentGrid, concentricLineOuterRadius, lengthNoise);
					isInStretch = true;
				}
				else if (!isWithinReach && isInStretch)
				{
					double stretchEnd = Double.isNaN(previousX) ? xInGraph
							: findReachCrossing(row, previousX, previousDistanceBeyondReach, xInGraph, distanceBeyondReach, yInGraph, segmentGrid, concentricLineOuterRadius, lengthNoise);
					stretches.add(new double[] { stretchStart, stretchEnd });
					isInStretch = false;
				}

				previousX = xInGraph;
				previousDistanceBeyondReach = distanceBeyondReach;
			}

			if (isInStretch)
			{
				double runEndInGraph = runEnd + drawBounds.x;
				double stretchEnd = !reachesLineAtEnd ? previousX
						: isEndVisible ? runEndInGraph - calcLineEndPullBack(row, runEndInGraph) : runEndInGraph + overhang;
				stretches.add(new double[] { stretchStart, stretchEnd });
			}

			breakPattern = drawStretches(p, row, yInGraph, rowJitterAmplitude, stretches, breakPattern, drawBounds);
		}
	}

	/**
	 * Draws one row of wave dashes: an unbroken stroke from the concentric line out to part of the way to where the dashes end, then dashes
	 * that get shorter, farther apart, and more often left out the farther out they are.
	 *
	 * How far out a point is, which decides where the unbroken part ends and which dashes are drawn, is measured along its run of the row,
	 * the way an artist spaces marks across the water they have to cover. See {@link #calcRunFraction}. Every choice depends only on the map
	 * within a band radius of each point, so that drawing part of the map gives the same dashes as drawing all of it.
	 *
	 * @param classes
	 *            For each pixel along the row in drawBounds, whether it is outside the band, in it, or kept clear of wave dashes.
	 * @param bandRadius
	 *            How far along a run to look for its ends.
	 */
	private void drawDashRow(Painter p, int row, double yInGraph, byte[] classes, SegmentGrid segmentGrid, double concentricLineOuterRadius, double bandRadius,
			Rectangle drawBounds)
	{
		int width = classes.length;
		double overhang = calcOverhang(strokeWidth);
		double minGap = calcMinBreakLength() * sizeMultiplier;
		double rowJitterAmplitude = getRowJitterAmplitude(row);
		BreakPattern breakPattern = null;

		boolean[] isInside = new boolean[width];
		double[] fractions = new double[width];
		for (int x = 0; x < width; x++)
		{
			if (classes[x] == bandClass)
			{
				fractions[x] = calcDashFraction(x + drawBounds.x, yInGraph, segmentGrid, concentricLineOuterRadius);
				isInside[x] = fractions[x] < 1.0;
			}
		}

		int x = 0;
		while (x < width)
		{
			if (!isInside[x])
			{
				x++;
				continue;
			}
			int runStart = x;
			while (x < width && isInside[x])
			{
				x++;
			}
			int runEnd = x;

			// A run that starts or ends where wave dashes are kept out reaches the concentric line there, and one that starts or ends at the
			// edge of the area being drawn continues past it.
			boolean reachesLineAtStart = runStart == 0 || classes[runStart - 1] == keepOutClass;
			boolean reachesLineAtEnd = runEnd == width || classes[runEnd] == keepOutClass;
			boolean isStartVisible = isVisibleLineEnd(classes, runStart - 1, drawBounds);
			boolean isEndVisible = isVisibleLineEnd(classes, runEnd, drawBounds);
			double runStartInGraph = runStart + drawBounds.x;
			double runEndInGraph = runEnd + drawBounds.x;
			DoubleUnaryOperator runFraction = xInGraph -> calcRunFraction(xInGraph, runStartInGraph, runEndInGraph, reachesLineAtStart, reachesLineAtEnd, bandRadius,
					() -> calcDashFraction(xInGraph, yInGraph, segmentGrid, concentricLineOuterRadius));
			double[] runFractions = new double[runEnd - runStart];
			for (int pixel = runStart; pixel < runEnd; pixel++)
			{
				int pixelIndex = pixel;
				runFractions[pixel - runStart] = calcRunFraction(pixel + drawBounds.x, runStartInGraph, runEndInGraph, reachesLineAtStart, reachesLineAtEnd, bandRadius,
						() -> fractions[pixelIndex]);
			}

			// The unbroken part of the row is wherever a point is less far out than the row's solid fraction there. Each stretch of it also
			// records whether its start and end are free ends, which taper, rather than ends under the concentric line or past the edge of the
			// area being drawn. Ends where a concentric line that isn't drawn would be are free, and pulled back from it.
			List<double[]> stretches = new ArrayList<>();
			boolean isStretchStartFree = false;
			double stretchStart = 0.0;
			boolean isInStretch = false;
			double previousX = Double.NaN;
			double previousDifference = 0.0;
			for (int pixel = runStart; pixel < runEnd; pixel++)
			{
				double xInGraph = pixel + drawBounds.x;
				double difference = runFractions[pixel - runStart] - getSolidDashFraction(row, xInGraph / sizeMultiplier);
				boolean isTouchingLine = (pixel == runStart && reachesLineAtStart) || (pixel == runEnd - 1 && reachesLineAtEnd);
				boolean isSolid = difference <= 0.0 || isTouchingLine;

				if (isSolid && !isInStretch)
				{
					boolean isAtLine = pixel == runStart && reachesLineAtStart;
					isStretchStartFree = !isAtLine || isStartVisible;
					stretchStart = isAtLine ? (isStartVisible ? xInGraph + calcLineEndPullBack(row, xInGraph) : xInGraph - overhang)
							: Double.isNaN(previousX) || isTouchingLine ? xInGraph
									: findSolidDashCrossing(row, previousX, previousDifference, xInGraph, difference, runFraction);
					isInStretch = true;
				}
				else if (!isSolid && isInStretch)
				{
					double stretchEnd = Double.isNaN(previousX) ? xInGraph
							: findSolidDashCrossing(row, previousX, previousDifference, xInGraph, difference, runFraction);
					stretches.add(new double[] { stretchStart, stretchEnd, isStretchStartFree ? 1.0 : 0.0, 1.0 });
					isInStretch = false;
				}

				previousX = xInGraph;
				previousDifference = difference;
			}
			if (isInStretch)
			{
				double stretchEnd = !reachesLineAtEnd ? previousX
						: isEndVisible ? runEndInGraph - calcLineEndPullBack(row, runEndInGraph) : runEndInGraph + overhang;
				stretches.add(new double[] { stretchStart, stretchEnd, isStretchStartFree ? 1.0 : 0.0, reachesLineAtEnd && !isEndVisible ? 0.0 : 1.0 });
			}
			breakPattern = drawStretches(p, row, yInGraph, rowJitterAmplitude, stretches, breakPattern, drawBounds);

			// Dashes past the unbroken part. Each series of them starts just past a free end of the unbroken part and runs outward, the way a
			// pen carries on across the water after lifting. The unbroken part's ends depend only on the map near them, so the dashes do too.
			RunDashes runDashes = new RunDashes(p, row, yInGraph, rowJitterAmplitude, runStartInGraph, runEndInGraph, reachesLineAtStart && !isStartVisible,
					reachesLineAtEnd && !isEndVisible, reachesLineAtStart, reachesLineAtEnd, bandRadius, runFraction, drawBounds);
			if (stretches.isEmpty())
			{
				runDashes.drawWithoutUnbrokenPart();
			}
			for (int i = 0; i < stretches.size(); i++)
			{
				double[] stretch = stretches.get(i);
				// Dashes between two unbroken parts, such as across a channel, meet halfway.
				double lowerLimit = i == 0 ? runStartInGraph : (stretches.get(i - 1)[1] + stretch[0]) / 2.0;
				double upperLimit = i == stretches.size() - 1 ? runEndInGraph : (stretch[1] + stretches.get(i + 1)[0]) / 2.0;
				if (stretch[2] > 0.0)
				{
					runDashes.drawSeries(stretch[0], -1.0, lowerLimit, true);
				}
				if (stretch[3] > 0.0)
				{
					runDashes.drawSeries(stretch[1], 1.0, upperLimit, true);
				}
			}
		}
	}

	/**
	 * Draws the dashes in one run of a row of wave dashes.
	 */
	private class RunDashes
	{
		private final Painter p;
		private final int row;
		private final double yInGraph;
		private final double rowJitterAmplitude;
		private final double runStartInGraph;
		private final double runEndInGraph;
		/**
		 * Whether the run's start is under the concentric line or past the edge of the area being drawn, so that a dash there doesn't taper.
		 */
		private final boolean isStartHidden;
		private final boolean isEndHidden;
		private final boolean reachesLineAtStart;
		private final boolean reachesLineAtEnd;
		private final double searchDistance;
		private final DoubleUnaryOperator runFraction;
		private final Rectangle drawBounds;

		RunDashes(Painter p, int row, double yInGraph, double rowJitterAmplitude, double runStartInGraph, double runEndInGraph, boolean isStartHidden,
				boolean isEndHidden, boolean reachesLineAtStart, boolean reachesLineAtEnd, double searchDistance, DoubleUnaryOperator runFraction,
				Rectangle drawBounds)
		{
			this.p = p;
			this.row = row;
			this.yInGraph = yInGraph;
			this.rowJitterAmplitude = rowJitterAmplitude;
			this.runStartInGraph = runStartInGraph;
			this.runEndInGraph = runEndInGraph;
			this.isStartHidden = isStartHidden;
			this.isEndHidden = isEndHidden;
			this.reachesLineAtStart = reachesLineAtStart;
			this.reachesLineAtEnd = reachesLineAtEnd;
			this.searchDistance = searchDistance;
			this.runFraction = runFraction;
			this.drawBounds = drawBounds;
		}

		/**
		 * Draws dashes in a run that has no unbroken part, such as one just above or below an island. A short run gets one series from its
		 * start. A long one is split at fixed places on the map, with a series in each piece, so that its dashes don't depend on where its far
		 * ends are.
		 */
		void drawWithoutUnbrokenPart()
		{
			if (runEndInGraph - runStartInGraph <= searchDistance && !(reachesLineAtStart && reachesLineAtEnd))
			{
				drawSeries(runStartInGraph, 1.0, runEndInGraph, false);
				return;
			}
			double pieceLength = searchDistance / 2.0;
			for (double pieceStart = Math.floor(runStartInGraph / pieceLength) * pieceLength; pieceStart < runEndInGraph; pieceStart += pieceLength)
			{
				double start = Math.max(pieceStart, runStartInGraph);
				drawSeries(start, 1.0, Math.min(pieceStart + pieceLength, runEndInGraph), false);
			}
		}

		/**
		 * Draws a series of dashes along the row from anchor toward limit, each shorter, farther from the last, and more likely left out than
		 * the one before as they get farther out. The series is random, but depends only on the row and the anchor.
		 *
		 * @param direction
		 *            1 to go right from the anchor, or -1 to go left.
		 * @param isAfterUnbrokenPart
		 *            Whether the anchor is the free end of an unbroken part of the row, which the first dash keeps a gap from.
		 */
		void drawSeries(double anchor, double direction, double limit, boolean isAfterUnbrokenPart)
		{
			Random rand = random(dashSalt, row, Double.doubleToLongBits(anchor) ^ Double.doubleToLongBits(direction));
			double wavelengthInPixels = wavelength * sizeMultiplier;
			double maxDistance = Math.min(Math.abs(limit - anchor), searchDistance);
			double position = isAfterUnbrokenPart
					? strokeWidth * rand.nextDouble(minDashGapAsMultipleOfStrokeWidth, maxDashGapAsMultipleOfStrokeWidth)
							+ wavelengthInPixels * rand.nextDouble(0.0, maxFirstDashOffsetInWavelengths)
					: wavelengthInPixels * rand.nextDouble(0.0, 1.0);
			while (position < maxDistance)
			{
				double near = anchor + direction * position;
				double fraction = Math.max(0.0, Math.min(1.0, runFraction.applyAsDouble(near)));
				double length = wavelengthInPixels * rand.nextDouble(minDashLengthInWavelengths, maxDashLengthInWavelengths) * (1.0 - dashShrinkAtEnd * fraction);
				double gap = strokeWidth * rand.nextDouble(minDashGapAsMultipleOfStrokeWidth, maxDashGapAsMultipleOfStrokeWidth)
						+ wavelengthInPixels * rand.nextDouble(minExtraDashGapInWavelengths, maxExtraDashGapInWavelengths) * (1.0 + dashGapGrowth * fraction);
				boolean isKept = rand.nextDouble() < Math.pow(1.0 - fraction, dashKeepProbabilityExponent);
				double drawnLength = Math.min(length, maxDistance - position);
				if (isKept && drawnLength >= minDashLengthToDrawInWavelengths * wavelengthInPixels)
				{
					double far = anchor + direction * (position + drawnLength);
					drawDash(Math.min(near, far), Math.max(near, far));
				}
				position += length + gap;
			}
		}

		/**
		 * Draws one dash, tapered at both ends, except an end cut off where the run reaches the line or the edge of the area being drawn,
		 * since the stroke continues under the line or past the edge.
		 */
		private void drawDash(double start, double end)
		{
			boolean isStartFree = !(start <= runStartInGraph && isStartHidden);
			boolean isEndFree = !(end >= runEndInGraph && isEndHidden);
			double taperLength = dashTaperAsFractionOfHalfLength * (end - start) / 2.0 / sizeMultiplier;
			drawPiece(p, row, yInGraph, rowJitterAmplitude, start / sizeMultiplier, end / sizeMultiplier, drawBounds,
					new StrokeTaper(start / sizeMultiplier, end / sizeMultiplier, taperLength, isStartFree, isEndFree));
		}
	}

	/**
	 * Whether a row's pixel is where the row stops at a concentric line that isn't drawn, so that the end of a stroke there shows. Pixels
	 * off the area being drawn or off the map are where strokes continue instead.
	 */
	private boolean isVisibleLineEnd(byte[] classes, int x, Rectangle drawBounds)
	{
		if (!areLineEndsVisible || x < 0 || x >= classes.length || classes[x] != keepOutClass)
		{
			return false;
		}
		double xInGraph = x + drawBounds.x;
		return xInGraph >= mapBounds.x && xInGraph < mapBounds.x + mapBounds.width;
	}

	/**
	 * How far, in pixels, to pull the end of a stroke back from where a row stops at a concentric line that isn't drawn: enough to keep its
	 * round cap clear of where the line would be, plus a random amount.
	 */
	private double calcLineEndPullBack(int row, double xInGraph)
	{
		double random = uniform(lineEndSalt, row, (long) Math.floor(xInGraph / sizeMultiplier));
		// Squaring favors ends near the line, with an occasional one well short of it.
		return strokeWidth / 2.0 + maxLineEndPullBackInWavelengths * wavelength * sizeMultiplier * random * random;
	}

	/**
	 * Lets a row pass over the concentric line where it only grazes the line's outer edge, such as just above the top of land that sticks up
	 * or out: a stretch of the row that is kept clear, shorter than maxLength, touches no land, and has water on both sides is treated as part
	 * of the band instead. Otherwise the row would stop at both sides of the graze, where the line is too thin along the row to hide the ends
	 * of the strokes, which leaves what looks like a wave with a piece missing.
	 *
	 * @param isLand
	 *            For each pixel along the row, whether it is land or off the map.
	 */
	private static void passOverGrazedLine(byte[] classes, boolean[] isLand, double maxLength)
	{
		int x = 0;
		while (x < classes.length && classes[x] == keepOutClass)
		{
			x++;
		}
		while (x < classes.length)
		{
			if (classes[x] != keepOutClass)
			{
				x++;
				continue;
			}
			int start = x;
			boolean touchesLand = false;
			while (x < classes.length && classes[x] == keepOutClass)
			{
				touchesLand |= isLand[x];
				x++;
			}
			if (x < classes.length && !touchesLand && x - start < maxLength)
			{
				Arrays.fill(classes, start, x, bandClass);
			}
		}
	}

	/**
	 * For wave dashes, how far out a point is by the measure that decides where they end: 0 at the concentric line, and 1 where wave dashes
	 * end. Past that it is more than 1.
	 *
	 * Where wave dashes end is shaped by {@link DashLens}, and varies randomly by position. They also always reach at least a fraction of
	 * that directly from the line.
	 */
	private double calcDashFraction(double xInGraph, double yInGraph, SegmentGrid segmentGrid, double concentricLineOuterRadius)
	{
		double noise = Helper.sampleSmoothNoise(hash(dashReachSalt, 0, 0), xInGraph / sizeMultiplier, yInGraph / sizeMultiplier,
				rowSpacing * dashReachNoiseSpacingAsMultipleOfRowSpacing);
		double reach = reachDistribution.getReach(dashReachNoiseScale * noise) * sizeMultiplier;
		double lensFraction = (dashLens.sampleEquivalentDistance(xInGraph, yInGraph) - concentricLineOuterRadius) / reach;
		segmentGrid.findNearest(xInGraph, yInGraph, nearestOnCurve);
		double directFraction = (nearestOnCurve.distance - concentricLineOuterRadius) / (reach * minDashReachAsFractionOfReach);
		return Math.max(0.0, Math.min(lensFraction, directFraction));
	}

	/**
	 * How far out a point in a run of wave dashes is, from 0 at the concentric line to 1 at the run's far end, measured along the run.
	 *
	 * A run that reaches the line at one end is measured from that end. One that reaches the line at both ends, such as across a narrow
	 * channel, is measured from the nearer end to its middle, and one that reaches it at neither, such as just above or below an island, from
	 * its middle out to both ends.
	 *
	 * Only the parts of a run within searchDistance of the point count, which keeps each point's fraction dependent only on what is near it.
	 * Points with neither end of their run that close use the fallback instead.
	 */
	private static double calcRunFraction(double xInGraph, double runStartInGraph, double runEndInGraph, boolean isStartAtLine, boolean isEndAtLine, double searchDistance,
			DoubleSupplier fallback)
	{
		double fromStart = xInGraph - runStartInGraph;
		double fromEnd = runEndInGraph - xInGraph;
		boolean isStartNear = fromStart < searchDistance;
		boolean isEndNear = fromEnd < searchDistance;
		if (!isStartNear && !isEndNear)
		{
			return fallback.getAsDouble();
		}
		fromStart = Math.min(fromStart, searchDistance);
		fromEnd = Math.min(fromEnd, searchDistance);
		double length = fromStart + fromEnd;
		if (length <= 0.0)
		{
			return 0.0;
		}
		boolean isMeasuredFromStart = isStartNear && isStartAtLine;
		boolean isMeasuredFromEnd = isEndNear && isEndAtLine;
		if (isMeasuredFromStart && isMeasuredFromEnd)
		{
			return Math.min(fromStart, fromEnd) / (length / 2.0);
		}
		if (isMeasuredFromStart)
		{
			return fromStart / length;
		}
		if (isMeasuredFromEnd)
		{
			return fromEnd / length;
		}
		return 1.0 - 2.0 * Math.min(fromStart, fromEnd) / length;
	}

	/**
	 * How far out, as a fraction of the way to where wave dashes end, a row of wave dashes stays unbroken at a point along it.
	 */
	private double getSolidDashFraction(int row, double xInUnits)
	{
		double middle = (minSolidDashFraction + maxSolidDashFraction) / 2.0;
		double halfRange = (maxSolidDashFraction - minSolidDashFraction) / 2.0;
		return middle + halfRange * sampleSmoothNoise(solidDashSalt, row, xInUnits, wavelength * solidDashNoiseSpacingInWavelengths);
	}

	/**
	 * Finds where along a row the unbroken part of a row of wave dashes ends, between a point in it and one past it.
	 *
	 * @param differenceWithin
	 *            How much farther out than the row's solid fraction the point in the unbroken part is, which is zero or less.
	 */
	private double findSolidDashCrossing(int row, double xWithin, double differenceWithin, double xBeyond, double differenceBeyond, DoubleUnaryOperator runFraction)
	{
		if (differenceWithin <= 0.0 == differenceBeyond <= 0.0)
		{
			return xBeyond;
		}
		double within = differenceWithin <= 0.0 ? xWithin : xBeyond;
		double beyond = differenceWithin <= 0.0 ? xBeyond : xWithin;
		for (int i = 0; i < bisectionIterations; i++)
		{
			double middle = (within + beyond) / 2.0;
			if (runFraction.applyAsDouble(middle) <= getSolidDashFraction(row, middle / sizeMultiplier))
			{
				within = middle;
			}
			else
			{
				beyond = middle;
			}
		}
		return within;
	}

	/**
	 * Draws the strokes of one run, joining any that a dip in how far wave lines reach left separated by a space too small to read as the ends
	 * of two strokes.
	 *
	 * @return The row's break pattern, created if it did not exist yet.
	 */
	private BreakPattern drawStretches(Painter p, int row, double yInGraph, double rowJitterAmplitude, List<double[]> stretches, BreakPattern breakPattern,
			Rectangle drawBounds)
	{
		double minGap = calcMinBreakLength() * sizeMultiplier;
		for (int i = 0; i < stretches.size(); i++)
		{
			double[] first = stretches.get(i);
			double end = first[1];
			double[] last = first;
			while (i + 1 < stretches.size() && stretches.get(i + 1)[0] - end < minGap)
			{
				i++;
				last = stretches.get(i);
				end = last[1];
			}
			// Wave dashes' stretches say which of their ends are free, and those ends taper.
			StrokeTaper taper = first.length < 4 ? null
					: new StrokeTaper(first[0] / sizeMultiplier, end / sizeMultiplier, solidDashTaperInWavelengths * wavelength, first[2] > 0.0, last[3] > 0.0);
			breakPattern = drawStretch(p, row, yInGraph, rowJitterAmplitude, first[0], end, taper, breakPattern, drawBounds);
		}
		return breakPattern;
	}

	/**
	 * Draws one stroke, in the pieces the row's breaks leave of it.
	 *
	 * @param taper
	 *            How the stroke's ends narrow, or null to draw it at its full width throughout.
	 * @return The row's break pattern, created if it did not exist yet.
	 */
	private BreakPattern drawStretch(Painter p, int row, double yInGraph, double rowJitterAmplitude, double start, double end, StrokeTaper taper, BreakPattern breakPattern,
			Rectangle drawBounds)
	{
		if (end <= start)
		{
			return breakPattern;
		}

		double startInUnits = start / sizeMultiplier;
		double endInUnits = end / sizeMultiplier;
		// Wave dashes break up only past their unbroken part, into dashes, so they get none of wave lines' breaks.
		if (DebugFlags.disableWaveLineBreaks() || isDashes)
		{
			drawPiece(p, row, yInGraph, rowJitterAmplitude, startInUnits, endInUnits, drawBounds, taper);
			return breakPattern;
		}

		BreakPattern pattern = breakPattern == null ? new BreakPattern(row) : breakPattern;
		pattern.extendTo(endInUnits);
		for (double[] drawInterval : pattern.drawIntervals)
		{
			drawPiece(p, row, yInGraph, rowJitterAmplitude, Math.max(startInUnits, drawInterval[0]), Math.min(endInUnits, drawInterval[1]), drawBounds, taper);
		}
		return pattern;
	}

	/**
	 * How much farther a point on a row is from the concentric line than the wave line there reaches, in pixels. A point is part of a wave
	 * line when this is zero or less.
	 *
	 * Which end's random reach applies depends on which way the line lies along the row: a point with the line to its left is in the part of
	 * a stroke that runs rightward from the line, so it uses the reach of right ends. The two blend where the line is directly above or
	 * below, which is the middle of a stroke rather than either of its ends.
	 */
	private double calcDistanceBeyondReach(int row, double xInGraph, double yInGraph, SegmentGrid segmentGrid, double concentricLineOuterRadius, RowLengthNoise lengthNoise)
	{
		segmentGrid.findNearest(xInGraph, yInGraph, nearestOnCurve);
		double alongRow = nearestOnCurve.distance <= 0.0 ? 0.0 : Math.max(-1.0, Math.min(1.0, (xInGraph - nearestOnCurve.pointX) / nearestOnCurve.distance));
		double rightEndWeight = (1.0 + alongRow) / 2.0;
		double xInUnits = xInGraph / sizeMultiplier;
		double noise = rightEndWeight * lengthNoise.sample(xInUnits, false) + (1.0 - rightEndWeight) * lengthNoise.sample(xInUnits, true);
		return nearestOnCurve.distance - concentricLineOuterRadius - reachDistribution.getReach(noise) * sizeMultiplier;
	}

	/**
	 * Finds where along a row a wave line's reach ends, between a point within reach and one beyond it.
	 */
	private double findReachCrossing(int row, double xWithin, double distanceWithin, double xBeyond, double distanceBeyond, double yInGraph, SegmentGrid segmentGrid,
			double concentricLineOuterRadius, RowLengthNoise lengthNoise)
	{
		double within = distanceWithin <= 0.0 ? xWithin : xBeyond;
		double beyond = distanceWithin <= 0.0 ? xBeyond : xWithin;
		if (distanceWithin <= 0.0 == distanceBeyond <= 0.0)
		{
			return xBeyond;
		}
		for (int i = 0; i < bisectionIterations; i++)
		{
			double middle = (within + beyond) / 2.0;
			if (calcDistanceBeyondReach(row, middle, yInGraph, segmentGrid, concentricLineOuterRadius, lengthNoise) <= 0.0)
			{
				within = middle;
			}
			else
			{
				beyond = middle;
			}
		}
		return within;
	}

	/**
	 * The random values along one row that decide how far its wave lines reach, kept so that the same control points aren't drawn from the
	 * random number generator again for every pixel of the row.
	 */
	private class RowLengthNoise
	{
		private final int row;
		private final Map<Long, Double> leftEndValues = new HashMap<>();
		private final Map<Long, Double> rightEndValues = new HashMap<>();

		RowLengthNoise(int row)
		{
			this.row = row;
		}

		double sample(double xInUnits, boolean isLeftEnd)
		{
			double controlPointSpacing = rowSpacing * lengthNoiseControlPointSpacingAsMultipleOfRowSpacing;
			double position = xInUnits / controlPointSpacing;
			long index = (long) Math.floor(position);
			double t = position - index;
			// The squares of these weights sum to 1, so the blend of two independent standard normal values is itself standard normal.
			double weight0 = Math.cos(t * Math.PI / 2.0);
			double weight1 = Math.sin(t * Math.PI / 2.0);
			return weight0 * getControlValue(index, isLeftEnd) + weight1 * getControlValue(index + 1, isLeftEnd);
		}

		private double getControlValue(long index, boolean isLeftEnd)
		{
			Map<Long, Double> values = isLeftEnd ? leftEndValues : rightEndValues;
			return values.computeIfAbsent(index, i -> random(isLeftEnd ? leftEndLengthSalt : rightEndLengthSalt, row, i).nextGaussian());
		}
	}

	/**
	 * The distances wave lines reach out from the concentric line, in units: the wave line length plus a normally distributed variation,
	 * clamped at maxReachStandardDeviations. The variation is scaled so that the clamp lands at the length variation's share of
	 * maxLengthVariationAsFractionOfLength times the length, which keeps the distances symmetric around the length, so their average is the
	 * length, and never lets them reach zero.
	 */
	private record ReachDistribution(double mean, double standardDeviation)
	{
		/**
		 * @return The distribution for the settings' wave line length and length variation, or null if wave lines have no length.
		 */
		static ReachDistribution create(MapSettings settings)
		{
			if (settings.getWaveRowStyle().length() <= 0)
			{
				return null;
			}
			double variation = Math.max(0, Math.min(MapSettings.maxWaveLineVariation, settings.getWaveRowStyle().lengthVariation())) / (double) MapSettings.maxWaveLineVariation;
			double maxDeviation = settings.getWaveRowStyle().length() * maxLengthVariationAsFractionOfLength * variation;
			return new ReachDistribution(settings.getWaveRowStyle().length(), maxDeviation / maxReachStandardDeviations);
		}

		double getReach(double standardNormalValue)
		{
			double clamped = Math.max(-maxReachStandardDeviations, Math.min(maxReachStandardDeviations, standardNormalValue));
			return mean + standardDeviation * clamped;
		}

		double getMaxReach()
		{
			return getReach(maxReachStandardDeviations);
		}
	}

	/**
	 * A row's alternating pattern of drawn and skipped lengths, in units. The pattern always starts at the left edge of the map, so it is the
	 * same no matter which part of the row is being drawn. Everything left of the map counts as drawn.
	 */
	private class BreakPattern
	{
		final List<double[]> drawIntervals = new ArrayList<>();
		private final Random rand;
		private boolean isDrawing;
		private double position;

		BreakPattern(int row)
		{
			rand = random(breakSalt, row, 0);
			isDrawing = rand.nextBoolean();
			if (!isDrawing)
			{
				drawIntervals.add(new double[] { Double.NEGATIVE_INFINITY, 0.0 });
			}
		}

		void extendTo(double xInUnits)
		{
			while (position < xInUnits)
			{
				if (isDrawing)
				{
					// Squaring favors shorter pieces, with an occasional long one.
					double random = rand.nextDouble();
					double length = wavelength * (minBreakDrawLengthInWavelengths + (maxBreakDrawLengthInWavelengths - minBreakDrawLengthInWavelengths) * random * random);
					drawIntervals.add(new double[] { drawIntervals.isEmpty() ? Double.NEGATIVE_INFINITY : position, position + length });
					position += length;
				}
				else
				{
					position += Math.max(calcMinBreakLength(), wavelength * rand.nextDouble(minBreakSkipLengthInWavelengths, maxBreakSkipLengthInWavelengths));
				}
				isDrawing = !isDrawing;
			}
		}
	}

	/**
	 * Draws the part of a row's stroke from startInUnits to endInUnits, if it is long enough to draw.
	 *
	 * @param taper
	 *            How the whole stroke this is part of narrows toward its ends, or null to draw it at the stroke width with round caps.
	 */
	private void drawPiece(Painter p, int row, double yInGraph, double rowJitterAmplitude, double startInUnits, double endInUnits, Rectangle drawBounds, StrokeTaper taper)
	{
		if (endInUnits - startInUnits < minPieceLength)
		{
			return;
		}

		WaveLineShape shape = settings.getWaveRowStyle().shape();
		double amplitudeInPixels = amplitude * sizeMultiplier;

		// Sample on a grid of fixed map positions, plus the ends and the shape's corners, so a stroke is drawn from the same points no matter
		// what area is being drawn.
		double step = Math.max(0.5 / sizeMultiplier, wavelength / 20.0);
		List<Double> samples = new ArrayList<>();
		samples.add(startInUnits);
		for (long gridIndex = (long) Math.floor(startInUnits / step) + 1; gridIndex * step < endInUnits; gridIndex++)
		{
			samples.add(gridIndex * step);
		}
		samples.add(endInUnits);
		addCornerSamples(samples, row, shape.getCornerPhases());

		List<FloatPoint> points = new ArrayList<>(samples.size());
		double[] widths = taper == null ? null : new double[samples.size()];
		for (int i = 0; i < samples.size(); i++)
		{
			double xInUnits = samples.get(i);
			double phase = getPhase(row, xInUnits);
			double height = getWaveHeight(shape, row, phase);
			double y = yInGraph - drawBounds.y + sampleJitter(row, rowJitterAmplitude, xInUnits) * sizeMultiplier;
			if (taper != null)
			{
				double amount = taper.getAmount(xInUnits);
				// The wave flattens toward its middle height, so a tapered stroke's tips point along it rather than up or down.
				height = 0.5 + (height - 0.5) * (1.0 - taperFlattening * amount);
				double pressure = 1.0 + taperPressureVariation * sampleSmoothNoise(taperPressureSalt, row, xInUnits, taperPressureNoiseSpacing);
				widths[i] = strokeWidth * pressure * (1.0 - (1.0 - taperTipWidthFraction) * amount);
			}
			points.add(new FloatPoint((float) (xInUnits * sizeMultiplier - drawBounds.x), (float) (y - amplitudeInPixels * height)));
		}

		if (taper == null)
		{
			p.drawPolylineFloat(points);
		}
		else
		{
			p.fillPolygonFloat(createOutline(points, widths));
		}
	}

	/**
	 * The outline of a stroke through the given points whose width at each point is given, with round caps.
	 */
	private static List<FloatPoint> createOutline(List<FloatPoint> points, double[] widths)
	{
		int count = points.size();
		double[] normalXs = new double[count];
		double[] normalYs = new double[count];
		for (int i = 0; i < count; i++)
		{
			FloatPoint before = points.get(Math.max(0, i - 1));
			FloatPoint after = points.get(Math.min(count - 1, i + 1));
			double dx = after.x - before.x;
			double dy = after.y - before.y;
			double length = Math.sqrt(dx * dx + dy * dy);
			normalXs[i] = length == 0.0 ? 0.0 : -dy / length;
			normalYs[i] = length == 0.0 ? 1.0 : dx / length;
		}

		List<FloatPoint> outline = new ArrayList<>(2 * count + 2 * roundCapSegments);
		for (int i = 0; i < count; i++)
		{
			outline.add(offset(points.get(i), normalXs[i], normalYs[i], widths[i] / 2.0));
		}
		addRoundCap(outline, points.get(count - 1), normalXs[count - 1], normalYs[count - 1], widths[count - 1] / 2.0);
		for (int i = count - 1; i >= 0; i--)
		{
			outline.add(offset(points.get(i), -normalXs[i], -normalYs[i], widths[i] / 2.0));
		}
		addRoundCap(outline, points.get(0), -normalXs[0], -normalYs[0], widths[0] / 2.0);
		return outline;
	}

	private static FloatPoint offset(FloatPoint point, double directionX, double directionY, double distance)
	{
		return new FloatPoint((float) (point.x + directionX * distance), (float) (point.y + directionY * distance));
	}

	/**
	 * Adds the points of a half circle around the end of a stroke, from the side the normal points to around to the other side.
	 */
	private static void addRoundCap(List<FloatPoint> outline, FloatPoint end, double normalX, double normalY, double radius)
	{
		// The normal turned clockwise points out of the end.
		double outX = normalY;
		double outY = -normalX;
		for (int segment = 1; segment < roundCapSegments; segment++)
		{
			double angle = Math.PI * segment / roundCapSegments;
			double cos = Math.cos(angle);
			double sin = Math.sin(angle);
			outline.add(new FloatPoint((float) (end.x + radius * (normalX * cos + outX * sin)), (float) (end.y + radius * (normalY * cos + outY * sin))));
		}
	}

	/**
	 * How a stroke narrows toward its free ends: to a thin tip, with its waves flattening, the way a pen stroke does as the pen lifts. It
	 * depends only on where the stroke's ends are, so every piece of a stroke that breaks cut into pieces tapers the same way.
	 *
	 * @param startInUnits
	 *            Where the whole stroke starts.
	 * @param endInUnits
	 *            Where the whole stroke ends.
	 * @param lengthInUnits
	 *            How far from each free end the narrowing reaches.
	 */
	private record StrokeTaper(double startInUnits, double endInUnits, double lengthInUnits, boolean isStartFree, boolean isEndFree)
	{
		/**
		 * How far a point on the stroke has narrowed, from 0 at full width to 1 at a free end's tip.
		 */
		double getAmount(double xInUnits)
		{
			double length = Math.min(lengthInUnits, (endInUnits - startInUnits) / 2.0);
			if (length <= 0.0)
			{
				return 0.0;
			}
			double fromStart = isStartFree ? 1.0 - (xInUnits - startInUnits) / length : 0.0;
			double fromEnd = isEndFree ? 1.0 - (endInUnits - xInUnits) / length : 0.0;
			double amount = Math.max(0.0, Math.min(1.0, Math.max(fromStart, fromEnd)));
			return amount * amount * (3.0 - 2.0 * amount);
		}
	}

	/**
	 * Adds a sample at each of the shape's corners between the first and last of the given sorted samples, so the corners stay sharp, and
	 * leaves the samples sorted.
	 */
	private void addCornerSamples(List<Double> samples, int row, double[] cornerPhases)
	{
		if (cornerPhases.length == 0)
		{
			return;
		}

		List<Double> corners = new ArrayList<>();
		for (int i = 1; i < samples.size(); i++)
		{
			double left = samples.get(i - 1);
			double right = samples.get(i);
			double leftPhase = getPhase(row, left);
			double rightPhase = getPhase(row, right);
			for (double cornerPhase : cornerPhases)
			{
				// The phase always increases along a row, so each corner phase crossed between two samples is crossed exactly once.
				for (double corner = Math.floor(leftPhase - cornerPhase) + 1.0 + cornerPhase; corner <= rightPhase; corner += 1.0)
				{
					double low = left;
					double high = right;
					for (int iteration = 0; iteration < cornerBisectionIterations; iteration++)
					{
						double middle = (low + high) / 2.0;
						if (getPhase(row, middle) < corner)
						{
							low = middle;
						}
						else
						{
							high = middle;
						}
					}
					corners.add(high);
				}
			}
		}
		samples.addAll(corners);
		Collections.sort(samples);
	}

	/**
	 * How many wavelengths along the shape a point on a row is, including the row's random starting phase and, with jitter on, the random
	 * shifting of its crests.
	 */
	private double getPhase(int row, double xInUnits)
	{
		double phase = xInUnits / wavelength + uniform(rowPhaseSalt, row, 0);
		phase += jitterFraction * maxCrestShiftInWavelengths * sampleSmoothNoise(crestShiftSalt, row, xInUnits, wavelength * jitterControlPointSpacingAsMultipleOfWavelength);
		return phase;
	}

	/**
	 * The height of the wave at a phase, from 0 to 1. With jitter on, each whole-number phase gets its own random height, and heights blend
	 * between them, so neighboring crests stand at different heights.
	 */
	private double getWaveHeight(WaveLineShape shape, int row, double phase)
	{
		long period = (long) Math.floor(phase);
		double fraction = phase - period;
		double height = shape.evaluate(fraction);
		if (jitterFraction > 0.0 && shape.hasCrests())
		{
			double startScale = getCrestHeightScale(row, period);
			double endScale = getCrestHeightScale(row, period + 1);
			height *= startScale + (endScale - startScale) * fraction;
		}
		return height;
	}

	private double getCrestHeightScale(int row, long period)
	{
		double lowestCrest = 1.0 - jitterFraction * (1.0 - minCrestHeightFraction);
		return lowestCrest + (1.0 - lowestCrest) * uniform(crestHeightSalt, row, period);
	}

	/**
	 * A smooth random vertical offset along a row, in units.
	 */
	private double sampleJitter(int row, double rowJitterAmplitude, double xInUnits)
	{
		if (rowJitterAmplitude == 0.0)
		{
			return 0.0;
		}
		return rowJitterAmplitude * sampleSmoothNoise(jitterSalt, row, xInUnits, wavelength * jitterControlPointSpacingAsMultipleOfWavelength);
	}

	/**
	 * A smooth random value from -1 to 1 along a row: a Catmull-Rom curve through random values at fixed map positions controlPointSpacing
	 * apart. Each value is at least half of the full strength in one direction or the other, so the curve keeps moving noticeably rather than
	 * sometimes lingering near zero. Its slope stays under 2.5 / controlPointSpacing.
	 */
	private double sampleSmoothNoise(long salt, int row, double xInUnits, double controlPointSpacing)
	{
		double position = xInUnits / controlPointSpacing;
		long index = (long) Math.floor(position);
		double t = position - index;
		double v0 = getNoiseControlValue(salt, row, index - 1);
		double v1 = getNoiseControlValue(salt, row, index);
		double v2 = getNoiseControlValue(salt, row, index + 1);
		double v3 = getNoiseControlValue(salt, row, index + 2);
		double catmullRom = 0.5 * (2.0 * v1 + (-v0 + v2) * t + (2.0 * v0 - 5.0 * v1 + 4.0 * v2 - v3) * t * t + (-v0 + 3.0 * v1 - 3.0 * v2 + v3) * t * t * t);
		return catmullRom / catmullRomMaxOvershoot;
	}

	private double getNoiseControlValue(long salt, int row, long index)
	{
		double value = 2.0 * uniform(salt, row, index) - 1.0;
		return value < 0.0 ? value / 2.0 - 0.5 : value / 2.0 + 0.5;
	}

	private long hash(long salt, int row, long index)
	{
		return Helper.mixSeed(Helper.mixSeed(Helper.mixSeed(settings.backgroundRandomSeed ^ salt) + row) + index);
	}

	/**
	 * A uniform random value from 0 (inclusive) to 1 (exclusive) that depends only on its arguments.
	 */
	private double uniform(long salt, int row, long index)
	{
		return (hash(salt, row, index) >>> 11) * 0x1.0p-53;
	}

	private Random random(long salt, int row, long index)
	{
		return new Random(hash(salt, row, index));
	}

	/**
	 * Shapes where wave dashes end. The land is blurred with a Gaussian that is much wider horizontally than vertically, and wave dashes reach
	 * out to where the blurred land drops below the level that a straight coast running north to south has at the wave dashes' reach. That
	 * rounds off headlands, fills small bays, gives small islands less than long coasts, and makes land sit in a wide, flat lens of wave
	 * dashes, the way an artist sums up a shape.
	 *
	 * To look up how far out a point is, the blurred land is turned into an equivalent distance: how far from a straight north-to-south coast
	 * a point with the same blurred value would be. The blur is computed on a grid of blocks aligned to the map, so that drawing part of the
	 * map uses the same blocks as drawing all of it, and approximated by three box blurs in each direction.
	 */
	private static class DashLens
	{
		final int blockSize;
		private final int radiusX;
		private final int radiusY;
		/**
		 * The horizontal standard deviation, in pixels, of the Gaussian the box blurs approximate.
		 */
		private final double sigmaX;
		private float[] equivalentDistances;
		private int firstColumn;
		private int firstRow;
		private int columns;
		private int rows;

		private DashLens(int blockSize, int radiusX, int radiusY)
		{
			this.blockSize = blockSize;
			this.radiusX = radiusX;
			this.radiusY = radiusY;
			// Three box blurs of radius r have a variance of r * (r + 1).
			sigmaX = Math.sqrt(radiusX * (radiusX + 1.0)) * blockSize;
		}

		/**
		 * @return The lens for the settings, or null if wave lines have no length.
		 */
		static DashLens create(MapSettings settings, double resolutionScale)
		{
			if (settings.getWaveRowStyle().length() <= 0)
			{
				return null;
			}
			double sigmaX = settings.getWaveRowStyle().length() * dashLensSigmaAsFractionOfLength * MapCreator.calcSizeMultiplierFromResolutionScale(resolutionScale);
			double sigmaY = sigmaX / dashLensAnisotropy;
			int blockSize = Math.max(minDashLensBlockSize, (int) Math.floor(sigmaY / dashLensBlocksPerVerticalSigma));
			return new DashLens(blockSize, calcBoxRadius(sigmaX / blockSize), calcBoxRadius(sigmaY / blockSize));
		}

		/**
		 * The radius of box blur that, applied three times, best approximates a Gaussian with the given standard deviation.
		 */
		private static int calcBoxRadius(double sigma)
		{
			return Math.max(1, (int) Math.round((Math.sqrt(4.0 * sigma * sigma + 1.0) - 1.0) / 2.0));
		}

		/**
		 * How far, in pixels, land can be from a point and still change the blur there.
		 */
		double calcSupport()
		{
			return (3 * radiusX + 1) * blockSize;
		}

		/**
		 * Blurs the land in the given area.
		 *
		 * @param landMask
		 *            An image covering drawBounds that is white on land.
		 * @param mapBounds
		 *            The bounds of the whole map. Blocks past its edges take the value of the nearest block on the map, the same as the blur
		 *            does at the edges of a drawing of the whole map.
		 */
		void build(Image landMask, Rectangle drawBounds, Rectangle mapBounds)
		{
			int width = landMask.getWidth();
			int height = landMask.getHeight();
			firstColumn = Math.floorDiv((int) Math.floor(drawBounds.x), blockSize);
			firstRow = Math.floorDiv((int) Math.floor(drawBounds.y), blockSize);
			columns = Math.floorDiv((int) Math.floor(drawBounds.x) + width - 1, blockSize) - firstColumn + 1;
			rows = Math.floorDiv((int) Math.floor(drawBounds.y) + height - 1, blockSize) - firstRow + 1;
			int mapFirstColumn = Math.floorDiv((int) Math.floor(mapBounds.x), blockSize) - firstColumn;
			int mapLastColumn = Math.floorDiv((int) Math.ceil(mapBounds.x + mapBounds.width) - 1, blockSize) - firstColumn;
			int mapFirstRow = Math.floorDiv((int) Math.floor(mapBounds.y), blockSize) - firstRow;
			int mapLastRow = Math.floorDiv((int) Math.ceil(mapBounds.y + mapBounds.height) - 1, blockSize) - firstRow;

			float[] sums = new float[columns * rows];
			int[] counts = new int[columns * rows];
			try (PixelReader landPixels = landMask.createPixelReader())
			{
				for (int y = 0; y < height; y++)
				{
					double yInGraph = y + drawBounds.y;
					if (yInGraph < mapBounds.y || yInGraph >= mapBounds.y + mapBounds.height)
					{
						continue;
					}
					int row = Math.floorDiv((int) Math.floor(yInGraph), blockSize) - firstRow;
					for (int x = 0; x < width; x++)
					{
						double xInGraph = x + drawBounds.x;
						if (xInGraph < mapBounds.x || xInGraph >= mapBounds.x + mapBounds.width)
						{
							continue;
						}
						int index = row * columns + Math.floorDiv((int) Math.floor(xInGraph), blockSize) - firstColumn;
						sums[index] += landPixels.getNormalizedPixelLevel(x, y) > 0.5f ? 1f : 0f;
						counts[index]++;
					}
				}
			}

			float[] values = new float[columns * rows];
			for (int row = 0; row < rows; row++)
			{
				int sourceRow = Math.max(0, Math.min(rows - 1, Math.max(mapFirstRow, Math.min(mapLastRow, row))));
				for (int column = 0; column < columns; column++)
				{
					int sourceColumn = Math.max(0, Math.min(columns - 1, Math.max(mapFirstColumn, Math.min(mapLastColumn, column))));
					int source = sourceRow * columns + sourceColumn;
					values[row * columns + column] = counts[source] == 0 ? 0f : sums[source] / counts[source];
				}
			}

			float[] buffer = new float[values.length];
			for (int pass = 0; pass < 3; pass++)
			{
				boxBlurRows(values, buffer, radiusX);
				boxBlurColumns(buffer, values, radiusY);
			}

			equivalentDistances = new float[values.length];
			for (int i = 0; i < values.length; i++)
			{
				double land = Math.max(1e-9, Math.min(1.0 - 1e-9, values[i]));
				// A straight coast blurred by a Gaussian has this much land at a distance d from it: the chance a normal value exceeds d / sigmaX.
				equivalentDistances[i] = (float) (sigmaX * inverseStandardNormalCumulativeDistribution(1.0 - land));
			}
		}

		private void boxBlurRows(float[] source, float[] target, int radius)
		{
			for (int row = 0; row < rows; row++)
			{
				int offset = row * columns;
				double sum = 0.0;
				for (int column = -radius; column <= radius; column++)
				{
					sum += source[offset + Math.max(0, Math.min(columns - 1, column))];
				}
				for (int column = 0; column < columns; column++)
				{
					target[offset + column] = (float) (sum / (2 * radius + 1));
					sum += source[offset + Math.min(columns - 1, column + radius + 1)] - source[offset + Math.max(0, column - radius)];
				}
			}
		}

		private void boxBlurColumns(float[] source, float[] target, int radius)
		{
			for (int column = 0; column < columns; column++)
			{
				double sum = 0.0;
				for (int row = -radius; row <= radius; row++)
				{
					sum += source[Math.max(0, Math.min(rows - 1, row)) * columns + column];
				}
				for (int row = 0; row < rows; row++)
				{
					target[row * columns + column] = (float) (sum / (2 * radius + 1));
					sum += source[Math.min(rows - 1, row + radius + 1) * columns + column] - source[Math.max(0, row - radius) * columns + column];
				}
			}
		}

		/**
		 * The equivalent distance from a straight north-to-south coast, in pixels, at a point in the map, interpolated between the middles of
		 * blocks. It is negative on land.
		 */
		double sampleEquivalentDistance(double xInGraph, double yInGraph)
		{
			double column = Math.max(0.0, Math.min(columns - 1.0, xInGraph / blockSize - 0.5 - firstColumn));
			double row = Math.max(0.0, Math.min(rows - 1.0, yInGraph / blockSize - 0.5 - firstRow));
			int column0 = (int) column;
			int row0 = (int) row;
			int column1 = Math.min(columns - 1, column0 + 1);
			int row1 = Math.min(rows - 1, row0 + 1);
			double weightX = column - column0;
			double weightY = row - row0;
			double top = equivalentDistances[row0 * columns + column0] * (1.0 - weightX) + equivalentDistances[row0 * columns + column1] * weightX;
			double bottom = equivalentDistances[row1 * columns + column0] * (1.0 - weightX) + equivalentDistances[row1 * columns + column1] * weightX;
			return top * (1.0 - weightY) + bottom * weightY;
		}

		/**
		 * The inverse of the standard normal cumulative distribution function, using Acklam's rational approximation, which has a relative error
		 * under 1.2e-9.
		 */
		private static double inverseStandardNormalCumulativeDistribution(double probability)
		{
			final double[] a = { -3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02, 1.383577518672690e+02, -3.066479806614716e+01,
					2.506628277459239e+00 };
			final double[] b = { -5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02, 6.680131188771972e+01, -1.328068155288572e+01 };
			final double[] c = { -7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00, -2.549732539343734e+00, 4.374664141464968e+00,
					2.938163982698783e+00 };
			final double[] d = { 7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00, 3.754408661907416e+00 };
			final double lowTail = 0.02425;
			if (probability < lowTail)
			{
				double q = Math.sqrt(-2.0 * Math.log(probability));
				return (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1.0);
			}
			if (probability > 1.0 - lowTail)
			{
				double q = Math.sqrt(-2.0 * Math.log(1.0 - probability));
				return -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1.0);
			}
			double q = probability - 0.5;
			double r = q * q;
			return (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q / (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1.0);
		}
	}

	/**
	 * Finds the distance from a point to the nearest of a set of line segments, for points within a fixed distance of some segment.
	 */
	private static class SegmentGrid
	{
		/**
		 * Cells this size hold few enough segments that looking up a distance scans only a handful of them, while the grid stays small enough
		 * to build quickly.
		 */
		private static final double cellSize = 16.0;

		private final double maxDistance;
		private final double originX;
		private final double originY;
		private final int columns;
		private final int rows;
		private final float[][] segmentsByCell;
		private final int[] segmentCountsByCell;

		/**
		 * @param bounds
		 *            The area, in graph coordinates, where distances will be looked up.
		 * @param maxDistance
		 *            Distances up to this are exact. Larger distances are reported as this value.
		 */
		SegmentGrid(List<CoastlineCurve> curves, Rectangle bounds, double maxDistance)
		{
			this.maxDistance = maxDistance;
			originX = bounds.x - maxDistance;
			originY = bounds.y - maxDistance;
			columns = (int) Math.ceil((bounds.width + 2.0 * maxDistance) / cellSize) + 1;
			rows = (int) Math.ceil((bounds.height + 2.0 * maxDistance) / cellSize) + 1;
			segmentsByCell = new float[columns * rows][];
			segmentCountsByCell = new int[columns * rows];

			for (CoastlineCurve curve : curves)
			{
				List<Point> points = curve.points();
				for (int i = 1; i < points.size(); i++)
				{
					add(points.get(i - 1), points.get(i));
				}
				if (curve.isPolygon())
				{
					add(points.get(points.size() - 1), points.get(0));
				}
			}
		}

		private void add(Point a, Point b)
		{
			int minColumn = Math.max(0, (int) Math.floor((Math.min(a.x, b.x) - originX) / cellSize));
			int maxColumn = Math.min(columns - 1, (int) Math.floor((Math.max(a.x, b.x) - originX) / cellSize));
			int minRow = Math.max(0, (int) Math.floor((Math.min(a.y, b.y) - originY) / cellSize));
			int maxRow = Math.min(rows - 1, (int) Math.floor((Math.max(a.y, b.y) - originY) / cellSize));
			for (int row = minRow; row <= maxRow; row++)
			{
				for (int column = minColumn; column <= maxColumn; column++)
				{
					int cell = row * columns + column;
					float[] segments = segmentsByCell[cell];
					int count = segmentCountsByCell[cell];
					if (segments == null)
					{
						segments = new float[16];
						segmentsByCell[cell] = segments;
					}
					else if ((count + 1) * 4 > segments.length)
					{
						segments = Arrays.copyOf(segments, segments.length * 2);
						segmentsByCell[cell] = segments;
					}
					segments[count * 4] = (float) a.x;
					segments[count * 4 + 1] = (float) a.y;
					segments[count * 4 + 2] = (float) b.x;
					segments[count * 4 + 3] = (float) b.y;
					segmentCountsByCell[cell] = count + 1;
				}
			}
		}

		/**
		 * The nearest point on any segment, and its distance, for points within maxDistance of a segment. Farther points report maxDistance as
		 * their distance, with the x of the nearest point found, if any.
		 */
		void findNearest(double x, double y, Nearest result)
		{
			int column = (int) Math.floor((x - originX) / cellSize);
			int row = (int) Math.floor((y - originY) / cellSize);
			double minDistanceSquared = maxDistance * maxDistance;
			result.distance = maxDistance;
			result.pointX = x;

			int maxRing = (int) Math.ceil(maxDistance / cellSize) + 1;
			for (int ring = 0; ring <= maxRing; ring++)
			{
				// Segments in cells this far out are at least this far away, so once something nearer has been found, the rest can't beat it.
				if (result.distance <= (ring - 1) * cellSize)
				{
					return;
				}

				for (int r = row - ring; r <= row + ring; r++)
				{
					if (r < 0 || r >= rows)
					{
						continue;
					}
					boolean isEdgeRow = r == row - ring || r == row + ring;
					for (int c = column - ring; c <= column + ring; c += isEdgeRow ? 1 : 2 * ring)
					{
						if (c < 0 || c >= columns)
						{
							continue;
						}
						float[] segments = segmentsByCell[r * columns + c];
						if (segments == null)
						{
							continue;
						}
						int count = segmentCountsByCell[r * columns + c];
						for (int i = 0; i < count; i++)
						{
							double distanceSquared = distanceSquaredToSegment(x, y, segments[i * 4], segments[i * 4 + 1], segments[i * 4 + 2], segments[i * 4 + 3]);
							if (distanceSquared < minDistanceSquared)
							{
								minDistanceSquared = distanceSquared;
								result.distance = Math.sqrt(distanceSquared);
								result.pointX = closestPointXOnSegment(x, y, segments[i * 4], segments[i * 4 + 1], segments[i * 4 + 2], segments[i * 4 + 3]);
							}
						}
					}
				}
			}
		}

		/**
		 * Where {@link #findNearest} puts its result, so that calling it for every pixel of a row doesn't allocate.
		 */
		static class Nearest
		{
			double distance;
			double pointX;
		}

		private static double closestPointXOnSegment(double x, double y, double ax, double ay, double bx, double by)
		{
			double dx = bx - ax;
			double dy = by - ay;
			double lengthSquared = dx * dx + dy * dy;
			double t = lengthSquared == 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, ((x - ax) * dx + (y - ay) * dy) / lengthSquared));
			return ax + t * dx;
		}

		private static double distanceSquaredToSegment(double x, double y, double ax, double ay, double bx, double by)
		{
			double dx = bx - ax;
			double dy = by - ay;
			double lengthSquared = dx * dx + dy * dy;
			double t = lengthSquared == 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, ((x - ax) * dx + (y - ay) * dy) / lengthSquared));
			double closestX = ax + t * dx - x;
			double closestY = ay + t * dy - y;
			return closestX * closestX + closestY * closestY;
		}
	}
}
