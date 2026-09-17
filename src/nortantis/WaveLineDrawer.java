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
import nortantis.platform.PixelReaderWriter;
import nortantis.util.Helper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
	/**
	 * The value in a row's fade levels that leaves a stroke as opaque as it was drawn.
	 */
	private static final int noFadeLevel = 255;
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
	private final double jitterFraction;
	private final double minRowSeparation;
	private final double maxRowShift;
	private final ReachDistribution reachDistribution;
	/**
	 * Reused by {@link #calcDistanceBeyondReach}, which is called for every pixel of every row's band.
	 */
	private final SegmentGrid.Nearest nearestOnCurve = new SegmentGrid.Nearest();

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
		jitterFraction = calcJitterFraction(settings);
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
		// Jitter moves both neighboring rows, so each gets half of the space between them.
		return calcJitterFraction(settings)
				* Math.min(settings.waveLineRowSpacing * maxJitterAmplitudeAsFractionOfRowSpacing, calcSpaceBetweenRowsWithoutJitter(settings) / 2.0);
	}

	/**
	 * How much of the jitter the settings ask for, from 0 when jitter is off to 1 at the highest jitter level.
	 */
	private static double calcJitterFraction(MapSettings settings)
	{
		if (!settings.jitterToWaveLines)
		{
			return 0.0;
		}
		return Math.max(0, Math.min(MapSettings.maxJitterLevel, settings.waveLineJitterLevel)) / (double) MapSettings.maxJitterLevel;
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
		double concentricLinePadding = MapCreator.calcWaveLinesConcentricLineOuterWidth(resolutionScale) + calcConcentricLineJitter(settings, resolutionScale);
		double bandPadding = calcBandRadius(settings, resolutionScale) + calcConcentricLineJitter(settings, resolutionScale);
		// How far a stroke's waves, jitter, row shift and width reach off its row.
		double offRowPadding = (calcAmplitude(settings) + calcJitterAmplitude(settings) + calcMaxRowShift(settings)) * sizeMultiplier + calcStrokeWidth(resolutionScale);
		return Math.max(concentricLinePadding, bandPadding + offRowPadding);
	}

	/**
	 * The shortest a break may be, in units. Round caps make each stroke reach half its width past the end of its piece, so the space a break
	 * leaves is its length less the stroke width.
	 */
	private static double calcMinBreakLength()
	{
		return calcStrokeWidthInUnits() * (1.0 + minBreakGapAsMultipleOfStrokeWidth);
	}

	/**
	 * How far a stroke's inner end reaches under the concentric line, in pixels.
	 */
	private static double calcOverhang(double strokeWidth)
	{
		return strokeWidth + 2.0;
	}

	/**
	 * Draws wave lines in white into target. Whether a point is part of a wave line depends only on the coastlines within a band radius of it,
	 * so the strokes drawn depend only on the map, not on what area is drawn, except within {@link #calcEffectsPadding} of the drawn area's
	 * edges.
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
		double concentricLineOuterRadius = MapCreator.calcWaveLinesConcentricLineOuterWidth(resolutionScale) / 2.0;
		double bandRadius = calcBandRadius(settings, resolutionScale);

		try (Image guide = Image.create(width, height, ImageType.Grayscale8Bit))
		{
			drawGuide(guide, graph, curves, concentricLineOuterRadius, bandRadius, centersToDraw, drawBounds);

			SegmentGrid segmentGrid = new SegmentGrid(curves, drawBounds, bandRadius + 1.0);
			List<Integer> fadedRows = settings.fadeWaveLines ? new ArrayList<>() : null;
			List<byte[]> fadeLevelsByRow = settings.fadeWaveLines ? new ArrayList<>() : null;

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
					int pixelRow = (int) Math.floor(yOnMap - drawBounds.y);
					if (pixelRow < 0 || pixelRow >= height)
					{
						continue;
					}

					for (int x = 0; x < width; x++)
					{
						double xInGraph = x + drawBounds.x;
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

					byte[] fadeLevels = null;
					if (fadedRows != null)
					{
						fadeLevels = new byte[width];
						Arrays.fill(fadeLevels, (byte) noFadeLevel);
						fadedRows.add(row);
						fadeLevelsByRow.add(fadeLevels);
					}

					drawRow(p, row, yInGraph, classes, segmentGrid, concentricLineOuterRadius, drawBounds, fadeLevels);
				}
			}

			if (fadedRows != null)
			{
				fadeRows(target, fadedRows, fadeLevelsByRow, drawBounds);
			}
		}
	}

	/**
	 * Lightens each row's strokes by the fade factors found along it, which lightens the parts of wave lines that are farther from the
	 * concentric line.
	 */
	private void fadeRows(Image target, List<Integer> rows, List<byte[]> fadeLevelsByRow, Rectangle drawBounds)
	{
		int height = target.getHeight();
		try (PixelReaderWriter pixels = target.createPixelReaderWriter())
		{
			for (int i = 0; i < rows.size(); i++)
			{
				int row = rows.get(i);
				byte[] fadeLevels = fadeLevelsByRow.get(i);
				int firstFaded = 0;
				while (firstFaded < fadeLevels.length && fadeLevels[firstFaded] == (byte) noFadeLevel)
				{
					firstFaded++;
				}
				int lastFaded = fadeLevels.length - 1;
				while (lastFaded >= firstFaded && fadeLevels[lastFaded] == (byte) noFadeLevel)
				{
					lastFaded--;
				}

				// A row is faded only where its own strokes can be, and never past halfway to where its neighbors' strokes can be, so that no
				// pixel is faded twice.
				int start = (int) Math.max(Math.round(getRowStripStart(row) - drawBounds.y), Math.floor(getRowInkTop(row) - drawBounds.y - 1.0));
				int end = (int) Math.min(Math.round(getRowStripStart(row + 1) - drawBounds.y), Math.ceil(getRowInkBottom(row) - drawBounds.y + 1.0));
				for (int y = Math.max(0, start); y < Math.min(height, end); y++)
				{
					for (int x = firstFaded; x <= lastFaded; x++)
					{
						int fadeLevel = fadeLevels[x] & 0xFF;
						if (fadeLevel < noFadeLevel)
						{
							int level = pixels.getGrayLevel(x, y);
							if (level > 0)
							{
								pixels.setGrayLevel(x, y, (level * fadeLevel + noFadeLevel / 2) / noFadeLevel);
							}
						}
					}
				}
			}
		}
	}

	/**
	 * The highest a row's strokes can reach, in pixels in the map.
	 */
	private double getRowInkTop(int row)
	{
		return (getRowY(row) - amplitude - getRowJitterAmplitude(row)) * sizeMultiplier - strokeWidth / 2.0;
	}

	/**
	 * The lowest a row's strokes can reach, in pixels in the map.
	 */
	private double getRowInkBottom(int row)
	{
		return (getRowY(row) + getRowJitterAmplitude(row)) * sizeMultiplier + strokeWidth / 2.0;
	}

	/**
	 * Where, in pixels in the map, the band of pixels that only the given row draws in begins, which is halfway between the highest its
	 * strokes reach and the lowest the row above it reaches.
	 */
	private double getRowStripStart(int row)
	{
		return (getRowInkBottom(row - 1) + getRowInkTop(row)) / 2.0;
	}

	/**
	 * How opaque a wave line is at a point, out of noFadeLevel, when fading is on: fully opaque where it leaves the concentric line, and
	 * fading to nothing at the far end of the stroke, however far that stroke reaches.
	 *
	 * @param distancePastLine
	 *            How far the point is outside the concentric line, in pixels.
	 * @param distanceBeyondReach
	 *            How much farther the point is from the concentric line than the wave line there reaches, in pixels.
	 */
	private byte calcFadeLevel(double distancePastLine, double distanceBeyondReach)
	{
		double reach = distancePastLine - distanceBeyondReach;
		double fraction = reach <= 0.0 ? 1.0 : Math.max(0.0, Math.min(1.0, distancePastLine / reach));
		return (byte) Math.round(noFadeLevel * (1.0 - fraction));
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
	 * @param fadeLevels
	 *            If not null, is filled in with how opaque the row's strokes are at each pixel along it, out of noFadeLevel.
	 */
	private void drawRow(Painter p, int row, double yInGraph, byte[] classes, SegmentGrid segmentGrid, double concentricLineOuterRadius, Rectangle drawBounds,
			byte[] fadeLevels)
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

			List<double[]> stretches = new ArrayList<>();
			double stretchStart = 0.0;
			boolean isInStretch = false;
			double previousX = Double.NaN;
			double previousDistanceBeyondReach = 0.0;
			for (int pixel = runStart; pixel < runEnd; pixel++)
			{
				double xInGraph = pixel + drawBounds.x;
				double distanceBeyondReach = calcDistanceBeyondReach(row, xInGraph, yInGraph, segmentGrid, concentricLineOuterRadius, lengthNoise);
				if (fadeLevels != null)
				{
					fadeLevels[pixel] = calcFadeLevel(nearestOnCurve.distance - concentricLineOuterRadius, distanceBeyondReach);
				}
				boolean isTouchingLine = (pixel == runStart && reachesLineAtStart) || (pixel == runEnd - 1 && reachesLineAtEnd);
				boolean isWithinReach = distanceBeyondReach <= 0.0 || isTouchingLine;

				if (isWithinReach && !isInStretch)
				{
					stretchStart = pixel == runStart && reachesLineAtStart ? xInGraph - overhang
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
				double stretchEnd = reachesLineAtEnd ? runEnd + drawBounds.x + overhang : previousX;
				stretches.add(new double[] { stretchStart, stretchEnd });
			}

			breakPattern = drawStretches(p, row, yInGraph, rowJitterAmplitude, stretches, breakPattern, drawBounds);
		}
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
			double start = stretches.get(i)[0];
			double end = stretches.get(i)[1];
			while (i + 1 < stretches.size() && stretches.get(i + 1)[0] - end < minGap)
			{
				i++;
				end = stretches.get(i)[1];
			}
			breakPattern = drawStretch(p, row, yInGraph, rowJitterAmplitude, start, end, breakPattern, drawBounds);
		}
		return breakPattern;
	}

	/**
	 * Draws one stroke, in the pieces the row's breaks leave of it.
	 *
	 * @return The row's break pattern, created if it did not exist yet.
	 */
	private BreakPattern drawStretch(Painter p, int row, double yInGraph, double rowJitterAmplitude, double start, double end, BreakPattern breakPattern, Rectangle drawBounds)
	{
		if (end <= start)
		{
			return breakPattern;
		}

		double startInUnits = start / sizeMultiplier;
		double endInUnits = end / sizeMultiplier;
		if (DebugFlags.disableWaveLineBreaks())
		{
			drawPiece(p, row, yInGraph, rowJitterAmplitude, startInUnits, endInUnits, drawBounds);
			return breakPattern;
		}

		BreakPattern pattern = breakPattern == null ? new BreakPattern(row) : breakPattern;
		pattern.extendTo(endInUnits);
		for (double[] drawInterval : pattern.drawIntervals)
		{
			drawPiece(p, row, yInGraph, rowJitterAmplitude, Math.max(startInUnits, drawInterval[0]), Math.min(endInUnits, drawInterval[1]), drawBounds);
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
					position += Math.max(calcMinBreakLength(), wavelength * rand.nextDouble(minBreakSkipLengthInWavelengths, maxBreakSkipLengthInWavelengths));
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
