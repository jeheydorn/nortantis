package nortantis;

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
import java.util.List;
import java.util.Random;

/**
 * Draws the rows of short horizontal wave lines that the "Wave lines" ocean wave style stacks outside the concentric line around coastlines.
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
	private static final double amplitudeAsFractionOfRowSpacing = 0.22;
	private static final double wavelengthAsMultipleOfRowSpacing = 1.6;
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
	private static final double minBreakDrawLengthInWavelengths = 0.5;
	private static final double maxBreakDrawLengthInWavelengths = 3.0;
	private static final double minBreakSkipLengthInWavelengths = 0.15;
	private static final double maxBreakSkipLengthInWavelengths = 0.65;
	private static final double lengthNoiseControlPointSpacingAsMultipleOfRowSpacing = 6.0;
	private static final int bisectionIterations = 8;

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

	private final MapSettings settings;
	private final double resolutionScale;
	private final double sizeMultiplier;
	private final double strokeWidth;
	private final double rowSpacing;
	private final double amplitude;
	private final double wavelength;
	private final double jitterAmplitude;
	private final double minRowSeparation;
	private final double maxRowShift;
	private final ReachDistribution reachDistribution;

	public WaveLineDrawer(MapSettings settings, double resolutionScale)
	{
		this.settings = settings;
		this.resolutionScale = resolutionScale;
		sizeMultiplier = MapCreator.calcSizeMultiplierFromResolutionScale(resolutionScale);
		strokeWidth = calcStrokeWidth(resolutionScale);
		rowSpacing = settings.waveLineRowSpacing;
		amplitude = calcAmplitude(settings);
		wavelength = rowSpacing * wavelengthAsMultipleOfRowSpacing;
		jitterAmplitude = calcJitterAmplitude(settings);
		minRowSeparation = calcMinRowSeparation(settings);
		maxRowShift = calcMaxRowShift(settings);
		reachDistribution = ReachDistribution.create(settings);
	}

	private static double calcStrokeWidth(double resolutionScale)
	{
		return MapCreator.calcConcentricWaveVisibleLineWidth(resolutionScale);
	}

	private static double calcStrokeWidthInUnits()
	{
		return calcStrokeWidth(1.0) / MapCreator.calcSizeMultiplierFromResolutionScale(1.0);
	}

	private static double calcAmplitude(MapSettings settings)
	{
		return settings.waveLineRowSpacing * amplitudeAsFractionOfRowSpacing;
	}

	/**
	 * The space between neighboring rows, in units, that neither their waves, stroke width nor the gap kept between them use.
	 */
	private static double calcSpaceBetweenRowsWithoutJitter(MapSettings settings)
	{
		return Math.max(0.0, settings.waveLineRowSpacing - calcAmplitude(settings) - calcStrokeWidthInUnits() - minGapBetweenRows);
	}

	/**
	 * The most a row's jitter moves it up or down, in units, which is what a row gets when it is evenly spaced from both neighbors. See
	 * {@link #getRowJitterAmplitude}.
	 */
	private static double calcJitterAmplitude(MapSettings settings)
	{
		if (!settings.jitterToConcentricWaves)
		{
			return 0.0;
		}
		// Jitter moves both neighboring rows, so each gets half of the space between them.
		return Math.min(settings.waveLineRowSpacing * maxJitterAmplitudeAsFractionOfRowSpacing, calcSpaceBetweenRowsWithoutJitter(settings) / 2.0);
	}

	/**
	 * How close, in units, the baselines of neighboring rows can be without the rows touching, before jitter.
	 */
	private static double calcMinRowSeparation(MapSettings settings)
	{
		// The upper row reaches down by half its stroke width. The lower row reaches up by its waves and half its stroke width.
		return calcAmplitude(settings) + calcStrokeWidthInUnits() + minGapBetweenRows;
	}

	/**
	 * The largest distance, in units, that a row can be shifted up or down from its evenly spaced position. See {@link #getRowY}.
	 */
	private static double calcMaxRowShift(MapSettings settings)
	{
		double largestShift = Math.max(0.0, settings.waveLineRowSpacing - calcMinRowSeparation(settings));
		return largestShift * Math.max(0, Math.min(10, settings.waveLineRowSpacingVariation)) / 10.0;
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
	 * The distance, in pixels, from a coastline curve to the farthest a wave line can reach.
	 */
	private static double calcBandRadius(MapSettings settings, double resolutionScale)
	{
		return MapCreator.calcWaveLinesConcentricLineOuterWidth(resolutionScale) / 2.0 + calcMaxReach(settings) * MapCreator.calcSizeMultiplierFromResolutionScale(resolutionScale);
	}

	private static double calcConcentricLineJitter(MapSettings settings, double resolutionScale)
	{
		return settings.jitterToConcentricWaves ? MapCreator.calcWaveLinesConcentricLineJitter(resolutionScale) : 0.0;
	}

	/**
	 * How far, in pixels, a change on the map directly moves what wave lines and their concentric line draw: the band around the changed part
	 * of the coastline, and how far strokes in it reach off their rows. Incremental draws must pad each side of the area they draw by at least
	 * this much.
	 *
	 * A change to the coastline can also move strokes farther away, by up to {@link #calcStrokeEndWalkDistance} beyond this, which is left
	 * out here so that incremental draws stay small. See {@link #drawWaveLines}.
	 */
	public static double calcEffectsPadding(MapSettings settings, double resolutionScale)
	{
		double sizeMultiplier = MapCreator.calcSizeMultiplierFromResolutionScale(resolutionScale);
		double concentricLinePadding = MapCreator.calcWaveLinesConcentricLineOuterWidth(resolutionScale) + calcConcentricLineJitter(settings, resolutionScale);
		double bandPadding = calcBandRadius(settings, resolutionScale) + calcConcentricLineJitter(settings, resolutionScale);
		// How far a stroke's waves, jitter, row shift and width reach off its row.
		double offRowPadding = (calcAmplitude(settings) + calcJitterAmplitude(settings) + calcMaxRowShift(settings)) * sizeMultiplier + calcStrokeWidth(resolutionScale);
		return Math.max(concentricLinePadding, bandPadding + offRowPadding);
	}

	/**
	 * How far, in pixels, a stroke's end can move along its row when the band it was found in changes. A stroke's outer end is found by
	 * walking from the band's edge by up to maxReach, and a piece shorter than minPieceLength next to that end can be dropped or kept. When a
	 * change to the coastline joins or separates two bands, a row's stroke can appear, disappear or change length this far past the band
	 * around the changed coastline.
	 */
	public static double calcStrokeEndWalkDistance(MapSettings settings, double resolutionScale)
	{
		return (calcMaxReach(settings) + minPieceLength) * MapCreator.calcSizeMultiplierFromResolutionScale(resolutionScale) + calcOverhang(calcStrokeWidth(resolutionScale));
	}

	/**
	 * How far a stroke's inner end reaches under the concentric line, in pixels.
	 */
	private static double calcOverhang(double strokeWidth)
	{
		return strokeWidth + 2.0;
	}

	/**
	 * Draws wave lines in white into target.
	 *
	 * Where strokes go is worked out from the placement area, which must extend at least {@link #calcStrokeEndWalkDistance} past each side of
	 * the drawn area. Coastlines and land just outside the placement area can affect strokes within {@link #calcEffectsPadding} plus that
	 * distance of its edges, so with that margin, the strokes drawn depend only on the map, not on what area is drawn, except within
	 * {@link #calcEffectsPadding} of the drawn area's edges.
	 *
	 * @param target
	 *            A grayscale image covering drawBounds.
	 * @param drawBounds
	 *            The area of the map the target covers, in graph coordinates.
	 * @param curves
	 *            The lines the concentric line is drawn along, including every one that passes through placementBounds.
	 * @param landMask
	 *            A binary image covering placementBounds that is white on land.
	 * @param placementCenters
	 *            The centers overlapping placementBounds, or null to use all centers.
	 * @param placementBounds
	 *            The area of the map, in graph coordinates, that strokes are placed from.
	 */
	public void drawWaveLines(Image target, Rectangle drawBounds, WorldGraph graph, List<CoastlineCurve> curves, Image landMask, Collection<Center> placementCenters,
			Rectangle placementBounds)
	{
		if (reachDistribution == null)
		{
			return;
		}

		int width = landMask.getWidth();
		int height = landMask.getHeight();
		double concentricLineOuterRadius = MapCreator.calcWaveLinesConcentricLineOuterWidth(resolutionScale) / 2.0;
		double bandRadius = calcBandRadius(settings, resolutionScale);

		try (Image guide = Image.create(width, height, ImageType.Grayscale8Bit))
		{
			drawGuide(guide, graph, curves, concentricLineOuterRadius, bandRadius, placementCenters, placementBounds);

			SegmentGrid segmentGrid = new SegmentGrid(curves, placementBounds, bandRadius + 1.0);

			try (PixelReader guidePixels = guide.createPixelReader(); PixelReader landPixels = landMask.createPixelReader(); Painter p = target.createPainter(DrawQuality.High))
			{
				p.setColor(Color.white);
				p.setBasicStroke((float) strokeWidth);

				// Include rows whose waves, jitter and stroke width reach into the target from just outside it.
				double rowReach = (amplitude + jitterAmplitude) * sizeMultiplier + strokeWidth;
				byte[] classes = new byte[width];
				int firstRow = (int) Math.floor(((drawBounds.y - rowReach) / sizeMultiplier - maxRowShift) / rowSpacing);
				int lastRow = (int) Math.ceil(((drawBounds.y + drawBounds.height + rowReach) / sizeMultiplier + maxRowShift) / rowSpacing);
				for (int row = firstRow; row <= lastRow; row++)
				{
					double yInGraph = getRowY(row) * sizeMultiplier;
					// A row just off the top or bottom of the map can still reach onto it, so it is placed using the nearest row of pixels on the
					// map. That way a full draw, which has no pixels off the map, and an incremental draw that reaches past the map's edge place it
					// the same way. What it draws off the map is clipped.
					double yOnMap = Math.max(graph.bounds.y, Math.min(graph.bounds.y + graph.bounds.height - 1.0, yInGraph));
					int pixelRow = (int) Math.floor(yOnMap - placementBounds.y);
					if (pixelRow < 0 || pixelRow >= height)
					{
						continue;
					}

					for (int x = 0; x < width; x++)
					{
						double xInGraph = x + placementBounds.x;
						if (xInGraph < graph.bounds.x || xInGraph >= graph.bounds.x + graph.bounds.width)
						{
							// Strokes that reach the map's left or right edge continue past it, the same as at the edge of a full draw.
							classes[x] = keepOutClass;
						}
						else if (landPixels.getNormalizedPixelLevel(x, pixelRow) > 0.5f)
						{
							classes[x] = keepOutClass;
						}
						else
						{
							int level = guidePixels.getGrayLevel(x, pixelRow);
							classes[x] = level > (bandLevel + keepOutLevel) / 2 ? keepOutClass : level > (outsideLevel + bandLevel) / 2 ? bandClass : outsideClass;
						}
					}

					drawRow(p, row, yInGraph, classes, segmentGrid, concentricLineOuterRadius, placementBounds, drawBounds);
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
	 *            For each pixel along the row in placementBounds, whether it is outside the band, in it, or kept clear of wave lines.
	 */
	private void drawRow(Painter p, int row, double yInGraph, byte[] classes, SegmentGrid segmentGrid, double concentricLineOuterRadius, Rectangle placementBounds,
			Rectangle drawBounds)
	{
		int width = classes.length;
		double overhang = calcOverhang(strokeWidth);
		double rowJitterAmplitude = getRowJitterAmplitude(row);
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

			double start;
			if (runStart > 0 && classes[runStart - 1] == outsideClass)
			{
				start = findOuterEnd(row, runStart + placementBounds.x, 1, yInGraph, segmentGrid, concentricLineOuterRadius);
			}
			else
			{
				// Either the run starts at the concentric line, where the end is hidden under it, or at the edge of the placement area,
				// where the stroke continues past it.
				start = runStart + placementBounds.x - overhang;
			}

			double end;
			if (runEnd < width && classes[runEnd] == outsideClass)
			{
				end = findOuterEnd(row, runEnd + placementBounds.x, -1, yInGraph, segmentGrid, concentricLineOuterRadius);
			}
			else
			{
				end = runEnd + placementBounds.x + overhang;
			}

			if (end <= start)
			{
				continue;
			}

			double startInUnits = start / sizeMultiplier;
			double endInUnits = end / sizeMultiplier;
			if (DebugFlags.disableWaveLineBreaks())
			{
				drawPiece(p, row, yInGraph, rowJitterAmplitude, startInUnits, endInUnits, drawBounds);
				continue;
			}
			if (breakPattern == null)
			{
				breakPattern = new BreakPattern(row);
			}
			breakPattern.extendTo(endInUnits);
			for (double[] drawInterval : breakPattern.drawIntervals)
			{
				drawPiece(p, row, yInGraph, rowJitterAmplitude, Math.max(startInUnits, drawInterval[0]), Math.min(endInUnits, drawInterval[1]), drawBounds);
			}
		}
	}

	/**
	 * Finds where a stroke's outer end goes by walking from the edge of the band toward the concentric line until the row is within this
	 * end's randomly chosen reach of the line.
	 *
	 * @param bandEdge
	 *            The x coordinate of the band's edge, in graph coordinates.
	 * @param direction
	 *            1 to walk right, -1 to walk left.
	 * @return The x coordinate of the end, in graph coordinates.
	 */
	private double findOuterEnd(int row, double bandEdge, int direction, double yInGraph, SegmentGrid segmentGrid, double concentricLineOuterRadius)
	{
		// Walking right means this is a stroke's left end.
		boolean isLeftEnd = direction > 0;
		double reach = reachDistribution.getReach(sampleLengthNoise(row, bandEdge / sizeMultiplier, isLeftEnd)) * sizeMultiplier;
		double maxWalk = reachDistribution.getMaxReach() * sizeMultiplier;

		double previous = bandEdge;
		double walked = 0.0;
		while (true)
		{
			double x = bandEdge + direction * walked;
			double distanceBeyondReach = segmentGrid.findDistanceLowerBound(x, yInGraph) - concentricLineOuterRadius - reach;
			if (distanceBeyondReach <= 0.0)
			{
				if (walked == 0.0)
				{
					return x;
				}
				// The row's distance from the concentric line changes by at most one pixel per pixel walked, so the steps below never pass
				// the first point within reach, and it lies between the last two points checked.
				double outside = previous;
				double inside = x;
				for (int i = 0; i < bisectionIterations; i++)
				{
					double middle = (outside + inside) / 2.0;
					if (segmentGrid.findDistanceLowerBound(middle, yInGraph) - concentricLineOuterRadius - reach <= 0.0)
					{
						inside = middle;
					}
					else
					{
						outside = middle;
					}
				}
				return inside;
			}

			if (walked >= maxWalk)
			{
				return x;
			}
			previous = x;
			walked = Math.min(maxWalk, walked + Math.max(0.5, distanceBeyondReach));
		}
	}

	/**
	 * A standard normal value that varies smoothly along a row, and is independent between rows. Left and right ends of strokes use separate
	 * values, so that two strokes whose ends face each other across a narrow gap, such as a bay or a strait, still get independent lengths.
	 */
	private double sampleLengthNoise(int row, double xInUnits, boolean isLeftEnd)
	{
		long salt = isLeftEnd ? leftEndLengthSalt : rightEndLengthSalt;
		double controlPointSpacing = rowSpacing * lengthNoiseControlPointSpacingAsMultipleOfRowSpacing;
		double position = xInUnits / controlPointSpacing;
		long index = (long) Math.floor(position);
		double t = position - index;
		// The squares of these weights sum to 1, so the blend of two independent standard normal values is itself standard normal.
		double weight0 = Math.cos(t * Math.PI / 2.0);
		double weight1 = Math.sin(t * Math.PI / 2.0);
		return weight0 * random(salt, row, index).nextGaussian() + weight1 * random(salt, row, index + 1).nextGaussian();
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
			if (settings.waveLineLength <= 0)
			{
				return null;
			}
			double variation = Math.max(0, Math.min(10, settings.waveLineLengthVariation)) / 10.0;
			double maxDeviation = settings.waveLineLength * maxLengthVariationAsFractionOfLength * variation;
			return new ReachDistribution(settings.waveLineLength, maxDeviation / maxReachStandardDeviations);
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
					position += wavelength * rand.nextDouble(minBreakSkipLengthInWavelengths, maxBreakSkipLengthInWavelengths);
				}
				isDrawing = !isDrawing;
			}
		}
	}

	/**
	 * Draws the part of a row's stroke from startInUnits to endInUnits, if it is long enough to draw.
	 */
	private void drawPiece(Painter p, int row, double yInGraph, double rowJitterAmplitude, double startInUnits, double endInUnits, Rectangle drawBounds)
	{
		if (endInUnits - startInUnits < minPieceLength)
		{
			return;
		}

		WaveLineShape shape = settings.waveLineShape;
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
		for (double xInUnits : samples)
		{
			double phase = getPhase(row, xInUnits);
			double y = yInGraph - drawBounds.y - amplitudeInPixels * getWaveHeight(shape, row, phase) + sampleJitter(row, rowJitterAmplitude, xInUnits) * sizeMultiplier;
			points.add(new FloatPoint((float) (xInUnits * sizeMultiplier - drawBounds.x), (float) y));
		}
		p.drawPolylineFloat(points);
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
		if (settings.jitterToConcentricWaves)
		{
			phase += maxCrestShiftInWavelengths * sampleSmoothNoise(crestShiftSalt, row, xInUnits, wavelength * jitterControlPointSpacingAsMultipleOfWavelength);
		}
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
		if (settings.jitterToConcentricWaves)
		{
			double startScale = getCrestHeightScale(row, period);
			double endScale = getCrestHeightScale(row, period + 1);
			height *= startScale + (endScale - startScale) * fraction;
		}
		return height;
	}

	private double getCrestHeightScale(int row, long period)
	{
		return minCrestHeightFraction + (1.0 - minCrestHeightFraction) * uniform(crestHeightSalt, row, period);
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
	 * Finds the distance from a point to the nearest of a set of line segments, for points within a fixed distance of some segment.
	 */
	private static class SegmentGrid
	{
		private final double cellSize;
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
			cellSize = maxDistance;
			originX = bounds.x - cellSize;
			originY = bounds.y - cellSize;
			columns = (int) Math.ceil((bounds.width + 2.0 * cellSize) / cellSize) + 1;
			rows = (int) Math.ceil((bounds.height + 2.0 * cellSize) / cellSize) + 1;
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
		 * The distance from the point to the nearest segment if that is at most the grid's cell size, and otherwise the cell size.
		 */
		double findDistanceLowerBound(double x, double y)
		{
			int column = (int) Math.floor((x - originX) / cellSize);
			int row = (int) Math.floor((y - originY) / cellSize);
			double minDistanceSquared = cellSize * cellSize;
			for (int r = Math.max(0, row - 1); r <= Math.min(rows - 1, row + 1); r++)
			{
				for (int c = Math.max(0, column - 1); c <= Math.min(columns - 1, column + 1); c++)
				{
					int cell = r * columns + c;
					float[] segments = segmentsByCell[cell];
					if (segments == null)
					{
						continue;
					}
					int count = segmentCountsByCell[cell];
					for (int i = 0; i < count; i++)
					{
						double distanceSquared = distanceSquaredToSegment(x, y, segments[i * 4], segments[i * 4 + 1], segments[i * 4 + 2], segments[i * 4 + 3]);
						if (distanceSquared < minDistanceSquared)
						{
							minDistanceSquared = distanceSquared;
						}
					}
				}
			}
			return Math.sqrt(minDistanceSquared);
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
