package nortantis.platform.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import nortantis.Stroke;
import nortantis.StrokeType;
import nortantis.geom.FloatPoint;
import nortantis.geom.Rectangle;
import nortantis.platform.AlphaComposite;
import nortantis.platform.Color;
import nortantis.platform.DrawQuality;
import nortantis.platform.Font;
import nortantis.platform.FontStyle;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.Painter;
import nortantis.platform.Transform;

/**
 * Draws onto a {@link WebImage} by writing its pixel array directly, following the rules of the AWT painter it stands in for:
 * <ul>
 * <li>With {@link DrawQuality#High}, shapes, strokes, and text are antialiased; with {@link DrawQuality#Normal} a pixel is either fully
 * drawn or not drawn at all.</li>
 * <li>Strokes have round joins. Their caps are round or butt as the stroke says, and their dashes are sized the way AWT's are.</li>
 * <li>Every {@link AlphaComposite} rule is supported, with the Porter-Duff definitions AWT uses.</li>
 * <li>Drawing onto a grayscale image draws the color's luminance, and drawing onto a binary image rounds each result to black or white.</li>
 * </ul>
 * Antialiasing works from each pixel's coverage, never by drawing at a higher resolution. A stroke's coverage at a pixel comes from the
 * pixel's distance to the stroke's center line, and the coverage of a whole stroke is gathered before any of it is drawn, so the pieces of
 * a translucent stroke don't darken where they overlap.
 */
public class WebPainter extends Painter
{
	/** Rows sampled per pixel when computing the coverage of a filled shape. Coverage is exact across each row. */
	private static final int fillSubsamples = 4;

	private static TextRasterizer textRasterizer;

	private final WebImage target;
	private final int width;
	private final int height;
	private final boolean antialias;
	private final ImageType targetType;

	private double m00 = 1, m01 = 0, m02 = 0, m10 = 0, m11 = 1, m12 = 0;

	private int colorArgb = 0xFF000000;
	private boolean hasGradient;
	private double gradientX1, gradientY1, gradientX2, gradientY2;
	private int gradientColor1, gradientColor2;

	private AlphaComposite composite = AlphaComposite.SrcOver;
	private float extraAlpha = 1f;

	private float strokeWidth = 1f;
	private boolean roundCaps = false;
	/** AWT's default stroke, which applies until a stroke is set, has square caps. */
	private boolean squareCaps = true;
	/** The lengths of a dash and of the gap after it, in device pixels, or null for a solid stroke. */
	private float[] dashPattern;

	private WebFont font;

	private int clipMinX, clipMinY, clipMaxX, clipMaxY;

	WebPainter(WebImage target, DrawQuality quality)
	{
		this.target = target;
		this.width = target.width;
		this.height = target.height;
		this.antialias = quality == DrawQuality.High;
		this.targetType = target.getType();
		clipMinX = 0;
		clipMinY = 0;
		clipMaxX = width;
		clipMaxY = height;
	}

	public static void setTextRasterizer(TextRasterizer rasterizer)
	{
		textRasterizer = rasterizer;
	}

	// ==================== State ====================

	@Override
	public void setColor(Color color)
	{
		colorArgb = color.getRGB();
		hasGradient = false;
	}

	@Override
	public Color getColor()
	{
		return new WebColor(colorArgb);
	}

	@Override
	public void setGradient(float x1, float y1, Color color1, float x2, float y2, Color color2)
	{
		hasGradient = true;
		gradientX1 = m00 * x1 + m01 * y1 + m02;
		gradientY1 = m10 * x1 + m11 * y1 + m12;
		gradientX2 = m00 * x2 + m01 * y2 + m02;
		gradientY2 = m10 * x2 + m11 * y2 + m12;
		gradientColor1 = color1.getRGB();
		gradientColor2 = color2.getRGB();
		colorArgb = gradientColor1;
	}

	@Override
	public void setFont(Font font)
	{
		this.font = (WebFont) font;
	}

	@Override
	public Font getFont()
	{
		if (font == null)
		{
			font = new WebFont("Dialog", FontStyle.Plain, 12);
		}
		return font;
	}

	@Override
	public void setAlphaComposite(AlphaComposite composite)
	{
		this.composite = composite;
		this.extraAlpha = 1f;
	}

	@Override
	public void setAlphaComposite(AlphaComposite composite, float alpha)
	{
		this.composite = composite;
		this.extraAlpha = alpha;
	}

	@Override
	public void setBasicStroke(float width)
	{
		strokeWidth = width;
		roundCaps = true;
		squareCaps = false;
		dashPattern = null;
	}

	@Override
	public void setStrokeToSolidLineWithNoEndDecorations(float width)
	{
		strokeWidth = width;
		roundCaps = false;
		squareCaps = false;
		dashPattern = null;
	}

	@Override
	public void setStroke(Stroke stroke, double resolutionScale)
	{
		// The same widths and dash lengths as AwtBridge.toAwtStroke.
		squareCaps = false;
		float width = stroke.width * (float) resolutionScale;
		float dashScale = (float) resolutionScale * stroke.width;
		if (stroke.type == StrokeType.Solid)
		{
			setBasicStroke(width);
		}
		else if (stroke.type == StrokeType.Dashes)
		{
			strokeWidth = width;
			roundCaps = false;
			dashPattern = new float[] { 6f * dashScale, 3f * dashScale };
		}
		else if (stroke.type == StrokeType.Rounded_Dashes)
		{
			strokeWidth = width;
			roundCaps = true;
			dashPattern = new float[] { 6f * dashScale, 4f * dashScale };
		}
		else if (stroke.type == StrokeType.Dots)
		{
			final float scaleBecauseDotsLookSmallerThanDashes = 3.9f / 2.7f;
			strokeWidth = width * scaleBecauseDotsLookSmallerThanDashes;
			roundCaps = true;
			dashPattern = new float[] { 0f, 2.0f * dashScale * scaleBecauseDotsLookSmallerThanDashes };
		}
		else
		{
			throw new IllegalArgumentException("Unrecognized stroke type: " + stroke.type);
		}
	}

	@Override
	public void setClip(int x, int y, int width, int height)
	{
		double[] xs = { x, x + width, x + width, x };
		double[] ys = { y, y, y + height, y + height };
		transformInPlace(xs, ys);
		double minX = Math.min(Math.min(xs[0], xs[1]), Math.min(xs[2], xs[3]));
		double maxX = Math.max(Math.max(xs[0], xs[1]), Math.max(xs[2], xs[3]));
		double minY = Math.min(Math.min(ys[0], ys[1]), Math.min(ys[2], ys[3]));
		double maxY = Math.max(Math.max(ys[0], ys[1]), Math.max(ys[2], ys[3]));
		clipMinX = Math.max(0, (int) Math.round(minX));
		clipMinY = Math.max(0, (int) Math.round(minY));
		clipMaxX = Math.min(this.width, (int) Math.round(maxX));
		clipMaxY = Math.min(this.height, (int) Math.round(maxY));
	}

	@Override
	public void translate(double x, double y)
	{
		m02 += m00 * x + m01 * y;
		m12 += m10 * x + m11 * y;
	}

	@Override
	public void rotate(double angle, double pivotX, double pivotY)
	{
		translate(pivotX, pivotY);
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		double n00 = m00 * cos + m01 * sin;
		double n01 = -m00 * sin + m01 * cos;
		double n10 = m10 * cos + m11 * sin;
		double n11 = -m10 * sin + m11 * cos;
		m00 = n00;
		m01 = n01;
		m10 = n10;
		m11 = n11;
		translate(-pivotX, -pivotY);
	}

	@Override
	public void setTransform(Transform transform)
	{
		WebTransform t = (WebTransform) transform;
		m00 = t.m00;
		m01 = t.m01;
		m02 = t.m02;
		m10 = t.m10;
		m11 = t.m11;
		m12 = t.m12;
	}

	@Override
	public Transform getTransform()
	{
		return new WebTransform(m00, m01, m02, m10, m11, m12);
	}

	@Override
	public void dispose()
	{
	}

	private boolean isTranslationOnly()
	{
		return m00 == 1 && m01 == 0 && m10 == 0 && m11 == 1;
	}

	private void transformInPlace(double[] xs, double[] ys)
	{
		for (int i = 0; i < xs.length; i++)
		{
			double x = xs[i];
			double y = ys[i];
			xs[i] = m00 * x + m01 * y + m02;
			ys[i] = m10 * x + m11 * y + m12;
		}
	}

	// ==================== Fills ====================

	@Override
	public void fillRect(int x, int y, int width, int height)
	{
		if (width <= 0 || height <= 0)
		{
			return;
		}
		if (isTranslationOnly() && m02 == Math.rint(m02) && m12 == Math.rint(m12))
		{
			int minX = Math.max(clipMinX, x + (int) m02);
			int minY = Math.max(clipMinY, y + (int) m12);
			int maxX = Math.min(clipMaxX, x + (int) m02 + width);
			int maxY = Math.min(clipMaxY, y + (int) m12 + height);
			for (int py = minY; py < maxY; py++)
			{
				for (int px = minX; px < maxX; px++)
				{
					plot(px, py, 1f);
				}
			}
			return;
		}
		double[] xs = { x, x + width, x + width, x };
		double[] ys = { y, y, y + height, y + height };
		transformInPlace(xs, ys);
		fillDevicePolygon(xs, ys);
	}

	@Override
	public void fillPolygon(int[] xPoints, int[] yPoints)
	{
		int n = Math.min(xPoints.length, yPoints.length);
		double[] xs = new double[n];
		double[] ys = new double[n];
		for (int i = 0; i < n; i++)
		{
			xs[i] = xPoints[i];
			ys[i] = yPoints[i];
		}
		transformInPlace(xs, ys);
		fillDevicePolygon(xs, ys);
	}

	@Override
	public void fillPolygonFloat(List<FloatPoint> points)
	{
		int n = points.size();
		double[] xs = new double[n];
		double[] ys = new double[n];
		for (int i = 0; i < n; i++)
		{
			xs[i] = points.get(i).x;
			ys[i] = points.get(i).y;
		}
		transformInPlace(xs, ys);
		fillDevicePolygon(xs, ys);
	}

	@Override
	public void fillOval(int x, int y, int width, int height)
	{
		double[][] points = ovalPoints(x, y, width, height);
		transformInPlace(points[0], points[1]);
		fillDevicePolygon(points[0], points[1]);
	}

	private static double[][] ovalPoints(int x, int y, int width, int height)
	{
		int segments = Math.max(24, width + height);
		double centerX = x + width / 2.0;
		double centerY = y + height / 2.0;
		double[] xs = new double[segments];
		double[] ys = new double[segments];
		for (int i = 0; i < segments; i++)
		{
			double angle = (2.0 * Math.PI * i) / segments;
			xs[i] = centerX + width / 2.0 * Math.cos(angle);
			ys[i] = centerY + height / 2.0 * Math.sin(angle);
		}
		return new double[][] { xs, ys };
	}

	/**
	 * Fills a polygon given in device coordinates with the even-odd rule. Without antialiasing, a pixel is drawn when its center is inside.
	 * With it, each pixel is drawn with the fraction of it the polygon covers.
	 */
	private void fillDevicePolygon(double[] xs, double[] ys)
	{
		int n = xs.length;
		if (n < 3)
		{
			return;
		}
		double minimumX = Double.MAX_VALUE, maximumX = -Double.MAX_VALUE, minimumY = Double.MAX_VALUE, maximumY = -Double.MAX_VALUE;
		for (int i = 0; i < n; i++)
		{
			minimumX = Math.min(minimumX, xs[i]);
			maximumX = Math.max(maximumX, xs[i]);
			minimumY = Math.min(minimumY, ys[i]);
			maximumY = Math.max(maximumY, ys[i]);
		}
		int minY = Math.max(clipMinY, (int) Math.floor(minimumY));
		int maxY = Math.min(clipMaxY - 1, (int) Math.ceil(maximumY));
		int minX = Math.max(clipMinX, (int) Math.floor(minimumX));
		int maxX = Math.min(clipMaxX - 1, (int) Math.ceil(maximumX));
		if (minX > maxX || minY > maxY)
		{
			return;
		}

		double[] crossings = new double[n];
		if (!antialias)
		{
			for (int y = minY; y <= maxY; y++)
			{
				int count = findCrossings(xs, ys, y + 0.5, crossings);
				for (int k = 0; k + 1 < count; k += 2)
				{
					// A pixel is drawn when its center is at or right of the left crossing and left of the right one.
					int start = Math.max(minX, (int) Math.ceil(crossings[k] - 0.5));
					int end = Math.min(maxX, (int) Math.ceil(crossings[k + 1] - 0.5) - 1);
					for (int x = start; x <= end; x++)
					{
						plot(x, y, 1f);
					}
				}
			}
			return;
		}

		float[] rowCoverage = new float[maxX - minX + 1];
		float sampleWeight = 1f / fillSubsamples;
		for (int y = minY; y <= maxY; y++)
		{
			Arrays.fill(rowCoverage, 0f);
			boolean any = false;
			for (int s = 0; s < fillSubsamples; s++)
			{
				int count = findCrossings(xs, ys, y + (s + 0.5) / fillSubsamples, crossings);
				for (int k = 0; k + 1 < count; k += 2)
				{
					addSpanCoverage(rowCoverage, minX, maxX, crossings[k], crossings[k + 1], sampleWeight);
					any = true;
				}
			}
			if (!any)
			{
				continue;
			}
			for (int i = 0; i < rowCoverage.length; i++)
			{
				float coverage = rowCoverage[i];
				if (coverage > 0f)
				{
					plot(minX + i, y, Math.min(1f, coverage));
				}
			}
		}
	}

	/**
	 * Finds where a horizontal line crosses the polygon's edges, sorted from left to right, and returns how many there are.
	 */
	private static int findCrossings(double[] xs, double[] ys, double y, double[] crossings)
	{
		int n = xs.length;
		int count = 0;
		int j = n - 1;
		for (int i = 0; i < n; i++)
		{
			double yi = ys[i];
			double yj = ys[j];
			if ((yi <= y && yj > y) || (yj <= y && yi > y))
			{
				crossings[count++] = xs[i] + (y - yi) / (yj - yi) * (xs[j] - xs[i]);
			}
			j = i;
		}
		if (count > 1)
		{
			Arrays.sort(crossings, 0, count);
		}
		return count;
	}

	/**
	 * Adds the given weight times the fraction of each pixel in [minX, maxX] that the span [x0, x1) covers.
	 */
	private static void addSpanCoverage(float[] rowCoverage, int minX, int maxX, double x0, double x1, float weight)
	{
		if (x1 <= x0)
		{
			return;
		}
		int first = (int) Math.floor(x0);
		int last = (int) Math.ceil(x1) - 1;
		if (last < minX || first > maxX)
		{
			return;
		}
		if (first == last)
		{
			if (first >= minX && first <= maxX)
			{
				rowCoverage[first - minX] += (float) (weight * (x1 - x0));
			}
			return;
		}
		if (first >= minX)
		{
			rowCoverage[first - minX] += (float) (weight * (first + 1 - x0));
		}
		int interiorStart = Math.max(first + 1, minX);
		int interiorEnd = Math.min(last - 1, maxX);
		for (int x = interiorStart; x <= interiorEnd; x++)
		{
			rowCoverage[x - minX] += weight;
		}
		if (last <= maxX)
		{
			rowCoverage[last - minX] += (float) (weight * (x1 - last));
		}
	}

	// ==================== Strokes ====================

	@Override
	public void drawLine(int x1, int y1, int x2, int y2)
	{
		drawLine((float) x1, (float) y1, (float) x2, (float) y2);
	}

	@Override
	public void drawLine(float x1, float y1, float x2, float y2)
	{
		double[] xs = { x1, x2 };
		double[] ys = { y1, y2 };
		transformInPlace(xs, ys);
		strokeDevicePath(xs, ys, false, true);
	}

	@Override
	public void drawRect(int x, int y, int width, int height)
	{
		double[] xs = { x, x + width, x + width, x };
		double[] ys = { y, y, y + height, y + height };
		transformInPlace(xs, ys);
		strokeDevicePath(xs, ys, true, true);
	}

	@Override
	public void drawOval(int x, int y, int width, int height)
	{
		double[][] points = ovalPoints(x, y, width, height);
		transformInPlace(points[0], points[1]);
		strokeDevicePath(points[0], points[1], true, true);
	}

	@Override
	public void drawPolygon(int[] xPoints, int[] yPoints)
	{
		strokeIntPoints(xPoints, yPoints, true);
	}

	@Override
	public void drawPolyline(int[] xPoints, int[] yPoints)
	{
		strokeIntPoints(xPoints, yPoints, false);
	}

	private void strokeIntPoints(int[] xPoints, int[] yPoints, boolean closed)
	{
		int n = Math.min(xPoints.length, yPoints.length);
		double[] xs = new double[n];
		double[] ys = new double[n];
		for (int i = 0; i < n; i++)
		{
			xs[i] = xPoints[i];
			ys[i] = yPoints[i];
		}
		transformInPlace(xs, ys);
		strokeDevicePath(xs, ys, closed, true);
	}

	@Override
	public void drawPolygonFloat(List<FloatPoint> points)
	{
		strokeFloatPoints(points, true, true);
	}

	@Override
	public void drawPolylineFloat(List<FloatPoint> points)
	{
		// Without moving points onto the pixel grid, as AwtPainter.drawPolylineFloat does, since that makes a line through closely spaced
		// points wobble.
		strokeFloatPoints(points, false, false);
	}

	private void strokeFloatPoints(List<FloatPoint> points, boolean closed, boolean normalize)
	{
		int n = points.size();
		double[] xs = new double[n];
		double[] ys = new double[n];
		for (int i = 0; i < n; i++)
		{
			xs[i] = points.get(i).x;
			ys[i] = points.get(i).y;
		}
		transformInPlace(xs, ys);
		strokeDevicePath(xs, ys, closed, normalize);
	}

	/**
	 * Strokes a path given in device coordinates with the current stroke.
	 *
	 * @param normalize
	 *            Whether to first move the points onto the pixel grid the way AWT's default stroke control does, so that a line on
	 *            whole-number coordinates covers whole pixels: to pixel centers when antialiasing, and to a quarter of a pixel past each
	 *            whole number otherwise.
	 */
	private void strokeDevicePath(double[] xs, double[] ys, boolean closed, boolean normalize)
	{
		int n = xs.length;
		if (n == 0)
		{
			return;
		}
		if (normalize)
		{
			for (int i = 0; i < n; i++)
			{
				xs[i] = antialias ? Math.floor(xs[i]) + 0.5 : Math.floor(xs[i] + 0.25) + 0.25;
				ys[i] = antialias ? Math.floor(ys[i]) + 0.5 : Math.floor(ys[i] + 0.25) + 0.25;
			}
		}
		double halfWidth = strokeWidth / 2.0;
		if (dashPattern == null)
		{
			strokeRun(xs, ys, n, closed, halfWidth);
			return;
		}

		double dashLength = dashPattern[0];
		double gapLength = dashPattern[1];
		if (dashLength <= 0)
		{
			// A zero-length dash with round caps draws a dot at the start of each dash.
			if (roundCaps)
			{
				for (double[] dot : pointsAlongPath(xs, ys, closed, gapLength))
				{
					CoverageBuffer buffer = CoverageBuffer.begin(this, dot[0] - halfWidth - 1, dot[1] - halfWidth - 1, dot[0] + halfWidth + 1, dot[1] + halfWidth + 1);
					if (buffer != null)
					{
						buffer.addDisc(dot[0], dot[1], halfWidth);
						buffer.draw(this);
					}
				}
			}
			return;
		}
		for (double[] run : splitIntoDashes(xs, ys, closed, dashLength, gapLength))
		{
			int m = run.length / 2;
			double[] runXs = new double[m];
			double[] runYs = new double[m];
			for (int i = 0; i < m; i++)
			{
				runXs[i] = run[i * 2];
				runYs[i] = run[i * 2 + 1];
			}
			strokeRun(runXs, runYs, m, false, halfWidth);
		}
	}

	/**
	 * Strokes one continuous run of points, with round joins between its segments and the current caps at its ends.
	 */
	private void strokeRun(double[] xs, double[] ys, int n, boolean closed, double halfWidth)
	{
		double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
		for (int i = 0; i < n; i++)
		{
			minX = Math.min(minX, xs[i]);
			maxX = Math.max(maxX, xs[i]);
			minY = Math.min(minY, ys[i]);
			maxY = Math.max(maxY, ys[i]);
		}
		double margin = halfWidth + 1.5;
		CoverageBuffer buffer = CoverageBuffer.begin(this, minX - margin, minY - margin, maxX + margin, maxY + margin);
		if (buffer == null)
		{
			return;
		}

		if (n == 1)
		{
			if (roundCaps)
			{
				buffer.addDisc(xs[0], ys[0], halfWidth);
			}
			buffer.draw(this);
			return;
		}

		if (squareCaps && !closed)
		{
			// A square cap is a butt cap moved out by half the width.
			extendEnd(xs, ys, 0, 1, halfWidth);
			extendEnd(xs, ys, n - 1, n - 2, halfWidth);
		}

		int segments = closed ? n : n - 1;
		for (int i = 0; i < segments; i++)
		{
			int j = (i + 1) % n;
			boolean isFirst = !closed && i == 0;
			boolean isLast = !closed && i == segments - 1;
			// Butt caps cut each end of a segment flat. The open ends of the run get no more than that, and every joint between segments
			// is filled in by the round disc each segment adds at its end, which makes the join round.
			buffer.addSegment(xs[i], ys[i], xs[j], ys[j], halfWidth, roundCaps || !isFirst, roundCaps || !isLast);
		}
		buffer.draw(this);
	}

	/**
	 * Moves the point at index end away from the point at index neighbor by the given distance.
	 */
	private static void extendEnd(double[] xs, double[] ys, int end, int neighbor, double distance)
	{
		double dx = xs[end] - xs[neighbor];
		double dy = ys[end] - ys[neighbor];
		double length = Math.hypot(dx, dy);
		if (length > 0)
		{
			xs[end] += dx / length * distance;
			ys[end] += dy / length * distance;
		}
	}

	/**
	 * Points spaced the given distance apart along the path, starting at its first point.
	 */
	private static List<double[]> pointsAlongPath(double[] xs, double[] ys, boolean closed, double spacing)
	{
		List<double[]> result = new ArrayList<>();
		int n = xs.length;
		if (spacing <= 0)
		{
			result.add(new double[] { xs[0], ys[0] });
			return result;
		}
		int segments = closed ? n : n - 1;
		double travelled = 0;
		double next = 0;
		for (int i = 0; i < Math.max(1, segments); i++)
		{
			int j = segments == 0 ? i : (i + 1) % n;
			double dx = xs[j] - xs[i];
			double dy = ys[j] - ys[i];
			double length = Math.hypot(dx, dy);
			while (next <= travelled + length + 1e-9)
			{
				double t = length == 0 ? 0 : (next - travelled) / length;
				result.add(new double[] { xs[i] + t * dx, ys[i] + t * dy });
				next += spacing;
				if (length == 0)
				{
					break;
				}
			}
			travelled += length;
		}
		return result;
	}

	/**
	 * Splits a path into the runs of points its dashes cover, measuring along the whole path so the pattern continues around corners. Each
	 * run is an array of alternating x and y coordinates.
	 */
	private static List<double[]> splitIntoDashes(double[] xs, double[] ys, boolean closed, double dashLength, double gapLength)
	{
		List<double[]> result = new ArrayList<>();
		int n = xs.length;
		int segments = closed ? n : n - 1;
		boolean drawing = true;
		double remaining = dashLength;
		List<Double> current = new ArrayList<>();
		current.add(xs[0]);
		current.add(ys[0]);
		for (int i = 0; i < segments; i++)
		{
			int j = (i + 1) % n;
			double ax = xs[i], ay = ys[i];
			double dx = xs[j] - ax, dy = ys[j] - ay;
			double length = Math.hypot(dx, dy);
			if (length < 1e-9)
			{
				continue;
			}
			double position = 0;
			while (position < length - 1e-9)
			{
				double step = Math.min(remaining, length - position);
				position += step;
				remaining -= step;
				double t = position / length;
				double px = ax + t * dx;
				double py = ay + t * dy;
				if (drawing)
				{
					current.add(px);
					current.add(py);
				}
				if (remaining < 1e-9)
				{
					if (drawing)
					{
						addRun(result, current);
						current.clear();
						remaining = gapLength;
					}
					else
					{
						current.add(px);
						current.add(py);
						remaining = dashLength;
					}
					drawing = !drawing;
				}
			}
		}
		if (drawing)
		{
			addRun(result, current);
		}
		return result;
	}

	private static void addRun(List<double[]> result, List<Double> run)
	{
		if (run.size() < 4)
		{
			return;
		}
		double[] values = new double[run.size()];
		for (int i = 0; i < values.length; i++)
		{
			values[i] = run.get(i);
		}
		result.add(values);
	}

	/**
	 * Gathers the coverage of a stroke over a rectangle of device pixels, keeping the largest coverage any part of the stroke gives each
	 * pixel, so that the whole stroke can then be drawn in one pass. Each thread reuses one buffer.
	 */
	private static class CoverageBuffer
	{
		private static final ThreadLocal<CoverageBuffer> buffers = ThreadLocal.withInitial(CoverageBuffer::new);

		private float[] coverage = new float[0];
		/** For each row of the buffer, the first and last device x the stroke may have covered, or an empty range if it covered none. */
		private int[] rowFirstX = new int[0];
		private int[] rowLastX = new int[0];
		private int minX, minY, bufferWidth, bufferHeight;
		private boolean antialias;

		/**
		 * Starts gathering coverage for the device pixels in the given bounds, clipped to the painter's clip. Returns null if none remain.
		 */
		static CoverageBuffer begin(WebPainter painter, double minimumX, double minimumY, double maximumX, double maximumY)
		{
			int minX = Math.max(painter.clipMinX, (int) Math.floor(minimumX));
			int minY = Math.max(painter.clipMinY, (int) Math.floor(minimumY));
			int maxX = Math.min(painter.clipMaxX - 1, (int) Math.ceil(maximumX));
			int maxY = Math.min(painter.clipMaxY - 1, (int) Math.ceil(maximumY));
			if (minX > maxX || minY > maxY)
			{
				return null;
			}
			CoverageBuffer buffer = buffers.get();
			buffer.minX = minX;
			buffer.minY = minY;
			buffer.bufferWidth = maxX - minX + 1;
			buffer.bufferHeight = maxY - minY + 1;
			buffer.antialias = painter.antialias;
			int size = buffer.bufferWidth * buffer.bufferHeight;
			if (buffer.coverage.length < size)
			{
				buffer.coverage = new float[size];
			}
			if (buffer.rowFirstX.length < buffer.bufferHeight)
			{
				buffer.rowFirstX = new int[buffer.bufferHeight];
				buffer.rowLastX = new int[buffer.bufferHeight];
			}
			Arrays.fill(buffer.rowFirstX, 0, buffer.bufferHeight, Integer.MAX_VALUE);
			Arrays.fill(buffer.rowLastX, 0, buffer.bufferHeight, Integer.MIN_VALUE);
			return buffer;
		}

		/**
		 * Adds a straight segment of the given half width. Each end that has a cap gets a round one; an end without a cap is cut off
		 * square at the end point.
		 */
		void addSegment(double x1, double y1, double x2, double y2, double halfWidth, boolean roundStart, boolean roundEnd)
		{
			double dx = x2 - x1;
			double dy = y2 - y1;
			double lengthSquared = dx * dx + dy * dy;
			if (lengthSquared == 0)
			{
				if (roundStart || roundEnd)
				{
					addDisc(x1, y1, halfWidth);
				}
				return;
			}
			double length = Math.sqrt(lengthSquared);
			double reach = halfWidth + 1;
			int fromX = Math.max(minX, (int) Math.floor(Math.min(x1, x2) - reach));
			int toX = Math.min(minX + bufferWidth - 1, (int) Math.ceil(Math.max(x1, x2) + reach));
			int fromY = Math.max(minY, (int) Math.floor(Math.min(y1, y2) - reach));
			int toY = Math.min(minY + bufferHeight - 1, (int) Math.ceil(Math.max(y1, y2) + reach));
			double unitX = dx / length;
			double unitY = dy / length;
			for (int py = fromY; py <= toY; py++)
			{
				double cy = py + 0.5 - y1;
				int row = (py - minY) * bufferWidth - minX;
				// On this row, only the pixels whose centers are within reach of the segment's line can be covered.
				int rowFromX = fromX;
				int rowToX = toX;
				if (Math.abs(unitY) > 1e-9)
				{
					double centerX = x1 + cy * unitX / unitY - 0.5;
					double halfSpan = reach / Math.abs(unitY);
					rowFromX = Math.max(fromX, (int) Math.floor(centerX - halfSpan));
					rowToX = Math.min(toX, (int) Math.ceil(centerX + halfSpan));
				}
				markRow(py, rowFromX, rowToX);
				for (int px = rowFromX; px <= rowToX; px++)
				{
					double cx = px + 0.5 - x1;
					double along = cx * unitX + cy * unitY;
					double across = Math.abs(cx * unitY - cy * unitX);
					double value;
					if (along < 0)
					{
						value = roundStart ? edgeCoverage(halfWidth - Math.hypot(cx, cy)) : Math.min(edgeCoverage(halfWidth - across), edgeCoverage(along));
					}
					else if (along > length)
					{
						value = roundEnd ? edgeCoverage(halfWidth - Math.hypot(px + 0.5 - x2, py + 0.5 - y2))
								: Math.min(edgeCoverage(halfWidth - across), edgeCoverage(length - along));
					}
					else
					{
						value = edgeCoverage(halfWidth - across);
					}
					if (value > 0)
					{
						int index = row + px;
						if (value > coverage[index])
						{
							coverage[index] = (float) value;
						}
					}
				}
			}
		}

		void addDisc(double centerX, double centerY, double radius)
		{
			double reach = radius + 1;
			int fromX = Math.max(minX, (int) Math.floor(centerX - reach));
			int toX = Math.min(minX + bufferWidth - 1, (int) Math.ceil(centerX + reach));
			int fromY = Math.max(minY, (int) Math.floor(centerY - reach));
			int toY = Math.min(minY + bufferHeight - 1, (int) Math.ceil(centerY + reach));
			for (int py = fromY; py <= toY; py++)
			{
				int row = (py - minY) * bufferWidth - minX;
				markRow(py, fromX, toX);
				for (int px = fromX; px <= toX; px++)
				{
					double value = edgeCoverage(radius - Math.hypot(px + 0.5 - centerX, py + 0.5 - centerY));
					if (value > 0)
					{
						int index = row + px;
						if (value > coverage[index])
						{
							coverage[index] = (float) value;
						}
					}
				}
			}
		}

		private void markRow(int y, int firstX, int lastX)
		{
			int row = y - minY;
			if (firstX < rowFirstX[row])
			{
				rowFirstX[row] = firstX;
			}
			if (lastX > rowLastX[row])
			{
				rowLastX[row] = lastX;
			}
		}

		/**
		 * The coverage of a pixel whose center is the given distance inside a shape's edge (negative when outside). This treats the edge
		 * as straight across the pixel, which makes a stroke narrower than a pixel draw as a faint line whose total darkness matches its
		 * width.
		 */
		private double edgeCoverage(double distanceInside)
		{
			double value = distanceInside + 0.5;
			if (value <= 0)
			{
				return 0;
			}
			if (!antialias)
			{
				return value >= 0.5 ? 1 : 0;
			}
			return value >= 1 ? 1 : value;
		}

		/**
		 * Draws every pixel the stroke covered, and clears the buffer for the next stroke.
		 */
		void draw(WebPainter painter)
		{
			for (int y = 0; y < bufferHeight; y++)
			{
				int row = y * bufferWidth;
				int lastX = rowLastX[y] - minX;
				for (int x = Math.max(0, rowFirstX[y] - minX); x <= lastX; x++)
				{
					float value = coverage[row + x];
					if (value > 0f)
					{
						coverage[row + x] = 0f;
						painter.plot(minX + x, minY + y, value);
					}
				}
			}
		}
	}

	// ==================== Images ====================

	@Override
	public void drawImage(Image image, int x, int y)
	{
		WebImage source = (WebImage) image;
		if (isTranslationOnly() && m02 == Math.rint(m02) && m12 == Math.rint(m12))
		{
			blit(source, x + (int) m02, y + (int) m12);
			return;
		}
		drawImageMapped(source, x, y, source.width, source.height);
	}

	@Override
	public void drawImage(Image image, int x, int y, int width, int height)
	{
		WebImage source = (WebImage) image;
		if (width == source.width && height == source.height)
		{
			drawImage(image, x, y);
			return;
		}
		drawImageMapped(source, x, y, width, height);
	}

	/**
	 * Draws an image at an integer device position without scaling it.
	 */
	private void blit(WebImage source, int x, int y)
	{
		int minX = Math.max(clipMinX, x);
		int minY = Math.max(clipMinY, y);
		int maxX = Math.min(clipMaxX, x + source.width);
		int maxY = Math.min(clipMaxY, y + source.height);
		if (minX >= maxX || minY >= maxY)
		{
			return;
		}

		if (composite == AlphaComposite.SrcOver && extraAlpha >= 1f && source.ints != null && target.ints != null)
		{
			blitSourceOverInts(source, x, y, minX, minY, maxX, maxY);
			return;
		}
		if (composite == AlphaComposite.Src && extraAlpha >= 1f && source.getType() == targetType)
		{
			WebImage.copyPixels(source, minX - x, minY - y, target, minX, minY, maxX - minX, maxY - minY);
			return;
		}

		for (int py = minY; py < maxY; py++)
		{
			for (int px = minX; px < maxX; px++)
			{
				int argb = source.getARGB(px - x, py - y);
				composite(px, py, argb, 1f);
			}
		}
	}

	/**
	 * The common case of drawing one color image over another, written out for speed.
	 */
	private void blitSourceOverInts(WebImage source, int x, int y, int minX, int minY, int maxX, int maxY)
	{
		int[] sourcePixels = source.ints;
		int[] targetPixels = target.ints;
		boolean sourceOpaque = source.getType() == ImageType.RGB;
		boolean targetHasAlpha = targetType == ImageType.ARGB;
		for (int py = minY; py < maxY; py++)
		{
			int sourceIndex = source.offset + (py - y) * source.stride + (minX - x);
			int targetIndex = target.offset + py * target.stride + minX;
			for (int px = minX; px < maxX; px++, sourceIndex++, targetIndex++)
			{
				int sourcePixel = sourcePixels[sourceIndex];
				int sourceAlpha = sourceOpaque ? 255 : sourcePixel >>> 24;
				if (sourceAlpha == 0)
				{
					continue;
				}
				if (sourceAlpha == 255)
				{
					targetPixels[targetIndex] = sourcePixel | 0xFF000000;
					continue;
				}
				int destinationPixel = targetPixels[targetIndex];
				float alphaFraction = sourceAlpha / 255f;
				float destinationAlpha = targetHasAlpha ? (destinationPixel >>> 24) / 255f : 1f;
				float resultAlpha = alphaFraction + destinationAlpha * (1 - alphaFraction);
				if (resultAlpha <= 0f)
				{
					targetPixels[targetIndex] = 0;
					continue;
				}
				float destinationWeight = destinationAlpha * (1 - alphaFraction);
				int r = Math.round((((sourcePixel >> 16) & 0xFF) * alphaFraction + ((destinationPixel >> 16) & 0xFF) * destinationWeight) / resultAlpha);
				int g = Math.round((((sourcePixel >> 8) & 0xFF) * alphaFraction + ((destinationPixel >> 8) & 0xFF) * destinationWeight) / resultAlpha);
				int b = Math.round(((sourcePixel & 0xFF) * alphaFraction + (destinationPixel & 0xFF) * destinationWeight) / resultAlpha);
				int alpha = targetHasAlpha ? Math.round(resultAlpha * 255) : 255;
				targetPixels[targetIndex] = (alpha << 24) | (r << 16) | (g << 8) | b;
			}
		}
	}

	/**
	 * Draws an image scaled to the given size and transformed by the current transform, finding each device pixel's source point by
	 * inverting the transform. Antialiased painters sample between source pixels; others take the nearest source pixel.
	 */
	private void drawImageMapped(WebImage source, int x, int y, int width, int height)
	{
		if (width <= 0 || height <= 0)
		{
			return;
		}
		double[] xs = { x, x + width, x + width, x };
		double[] ys = { y, y, y + height, y + height };
		transformInPlace(xs, ys);
		int minX = clipMinX, maxX = clipMaxX - 1, minY = clipMinY, maxY = clipMaxY - 1;
		minX = Math.max(minX, (int) Math.floor(Math.min(Math.min(xs[0], xs[1]), Math.min(xs[2], xs[3]))));
		maxX = Math.min(maxX, (int) Math.ceil(Math.max(Math.max(xs[0], xs[1]), Math.max(xs[2], xs[3]))));
		minY = Math.max(minY, (int) Math.floor(Math.min(Math.min(ys[0], ys[1]), Math.min(ys[2], ys[3]))));
		maxY = Math.min(maxY, (int) Math.ceil(Math.max(Math.max(ys[0], ys[1]), Math.max(ys[2], ys[3]))));
		if (minX > maxX || minY > maxY)
		{
			return;
		}

		double determinant = m00 * m11 - m01 * m10;
		if (determinant == 0)
		{
			return;
		}
		double i00 = m11 / determinant, i01 = -m01 / determinant, i10 = -m10 / determinant, i11 = m00 / determinant;
		double scaleX = (double) source.width / width;
		double scaleY = (double) source.height / height;
		for (int py = minY; py <= maxY; py++)
		{
			for (int px = minX; px <= maxX; px++)
			{
				double dx = px + 0.5 - m02;
				double dy = py + 0.5 - m12;
				double u = (i00 * dx + i01 * dy - x) * scaleX;
				double v = (i10 * dx + i11 * dy - y) * scaleY;
				if (u < 0 || v < 0 || u >= source.width || v >= source.height)
				{
					continue;
				}
				int argb = antialias ? sampleBilinear(source, u - 0.5, v - 0.5) : source.getARGB((int) u, (int) v);
				composite(px, py, argb, 1f);
			}
		}
	}

	private static int sampleBilinear(WebImage source, double u, double v)
	{
		int x0 = (int) Math.floor(u);
		int y0 = (int) Math.floor(v);
		double fx = u - x0;
		double fy = v - y0;
		int x1 = Math.min(source.width - 1, x0 + 1);
		int y1 = Math.min(source.height - 1, y0 + 1);
		x0 = Math.max(0, x0);
		y0 = Math.max(0, y0);
		int c00 = source.getARGB(x0, y0), c10 = source.getARGB(x1, y0), c01 = source.getARGB(x0, y1), c11 = source.getARGB(x1, y1);
		double w00 = (1 - fx) * (1 - fy), w10 = fx * (1 - fy), w01 = (1 - fx) * fy, w11 = fx * fy;
		double a00 = (c00 >>> 24) * w00, a10 = (c10 >>> 24) * w10, a01 = (c01 >>> 24) * w01, a11 = (c11 >>> 24) * w11;
		double alpha = a00 + a10 + a01 + a11;
		if (alpha <= 0)
		{
			return 0;
		}
		int r = (int) Math.round((((c00 >> 16) & 0xFF) * a00 + ((c10 >> 16) & 0xFF) * a10 + ((c01 >> 16) & 0xFF) * a01 + ((c11 >> 16) & 0xFF) * a11) / alpha);
		int g = (int) Math.round((((c00 >> 8) & 0xFF) * a00 + ((c10 >> 8) & 0xFF) * a10 + ((c01 >> 8) & 0xFF) * a01 + ((c11 >> 8) & 0xFF) * a11) / alpha);
		int b = (int) Math.round(((c00 & 0xFF) * a00 + (c10 & 0xFF) * a10 + (c01 & 0xFF) * a01 + (c11 & 0xFF) * a11) / alpha);
		int a = (int) Math.round(alpha);
		return (Math.min(255, a) << 24) | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
	}

	// ==================== Text ====================

	@Override
	public void drawString(String string, double x, double y)
	{
		if (string == null || string.isEmpty())
		{
			return;
		}
		TextRasterizer.TextCoverage text = textRasterizer.rasterize((WebFont) getFont(), string, x, y, new double[] { m00, m10, m01, m11, m02, m12 }, width,
				height);
		if (text == null)
		{
			return;
		}
		int minX = Math.max(clipMinX, text.x);
		int minY = Math.max(clipMinY, text.y);
		int maxX = Math.min(clipMaxX, text.x + text.width);
		int maxY = Math.min(clipMaxY, text.y + text.height);
		for (int py = minY; py < maxY; py++)
		{
			int row = (py - text.y) * text.width - text.x;
			for (int px = minX; px < maxX; px++)
			{
				int value = text.coverage[row + px] & 0xFF;
				if (value == 0)
				{
					continue;
				}
				if (!antialias)
				{
					if (value < 128)
					{
						continue;
					}
					plot(px, py, 1f);
				}
				else
				{
					plot(px, py, value / 255f);
				}
			}
		}
	}

	@Override
	public int stringWidth(String string)
	{
		return (int) (0.5 + textRasterizer.getAdvance((WebFont) getFont(), string));
	}

	@Override
	public Rectangle getStringVisualBounds(String string)
	{
		return textRasterizer.getVisualBounds((WebFont) getFont(), string);
	}

	@Override
	public int charWidth(char c)
	{
		return stringWidth(String.valueOf(c));
	}

	@Override
	public int getFontAscent()
	{
		return textRasterizer.getAscent((WebFont) getFont());
	}

	@Override
	public int getFontDescent()
	{
		return textRasterizer.getDescent((WebFont) getFont());
	}

	// ==================== Compositing ====================

	/**
	 * Draws the current paint at a device pixel, covering the given fraction of it.
	 */
	private void plot(int x, int y, float coverage)
	{
		int argb = hasGradient ? gradientColorAt(x + 0.5, y + 0.5) : colorArgb;
		composite(x, y, argb, coverage);
	}

	private int gradientColorAt(double x, double y)
	{
		double dx = gradientX2 - gradientX1;
		double dy = gradientY2 - gradientY1;
		double lengthSquared = dx * dx + dy * dy;
		double t = lengthSquared == 0 ? 0 : ((x - gradientX1) * dx + (y - gradientY1) * dy) / lengthSquared;
		t = Math.max(0, Math.min(1, t));
		int result = 0;
		for (int shift = 0; shift <= 24; shift += 8)
		{
			int c1 = (gradientColor1 >>> shift) & 0xFF;
			int c2 = (gradientColor2 >>> shift) & 0xFF;
			result |= ((int) Math.round(c1 + (c2 - c1) * t) & 0xFF) << shift;
		}
		return result;
	}

	/**
	 * Composites a source color (ARGB, not premultiplied) onto a device pixel with the current rule and extra alpha. The coverage blends
	 * between the pixel as it was and the composited result, as AWT does for antialiased edges.
	 */
	private void composite(int x, int y, int sourceArgb, float coverage)
	{
		if (x < clipMinX || y < clipMinY || x >= clipMaxX || y >= clipMaxY)
		{
			return;
		}
		int index = target.offset + y * target.stride + x;
		float sourceAlpha = ((sourceArgb >>> 24) / 255f) * extraAlpha;
		int sourceRed = (sourceArgb >> 16) & 0xFF;
		int sourceGreen = (sourceArgb >> 8) & 0xFF;
		int sourceBlue = sourceArgb & 0xFF;

		if (target.ints != null)
		{
			int destination = target.ints[index];
			boolean hasAlpha = targetType == ImageType.ARGB;
			if (composite == AlphaComposite.SrcOver)
			{
				float a = sourceAlpha * coverage;
				if (a <= 0f)
				{
					return;
				}
				if (a >= 1f)
				{
					target.ints[index] = 0xFF000000 | (sourceRed << 16) | (sourceGreen << 8) | sourceBlue;
					return;
				}
				float destinationAlpha = hasAlpha ? (destination >>> 24) / 255f : 1f;
				float resultAlpha = a + destinationAlpha * (1 - a);
				float destinationWeight = destinationAlpha * (1 - a);
				int r = Math.round((sourceRed * a + ((destination >> 16) & 0xFF) * destinationWeight) / resultAlpha);
				int g = Math.round((sourceGreen * a + ((destination >> 8) & 0xFF) * destinationWeight) / resultAlpha);
				int b = Math.round((sourceBlue * a + (destination & 0xFF) * destinationWeight) / resultAlpha);
				int alpha = hasAlpha ? Math.round(resultAlpha * 255) : 255;
				target.ints[index] = (alpha << 24) | (r << 16) | (g << 8) | b;
				return;
			}

			float destinationAlpha = hasAlpha ? (destination >>> 24) / 255f : 1f;
			float[] fractions = porterDuffFractions(sourceAlpha, destinationAlpha);
			float sourceFraction = fractions[0];
			float destinationFraction = fractions[1];
			float resultAlpha = sourceAlpha * sourceFraction + destinationAlpha * destinationFraction;
			float r = (sourceRed * sourceAlpha * sourceFraction + ((destination >> 16) & 0xFF) * destinationAlpha * destinationFraction);
			float g = (sourceGreen * sourceAlpha * sourceFraction + ((destination >> 8) & 0xFF) * destinationAlpha * destinationFraction);
			float b = (sourceBlue * sourceAlpha * sourceFraction + (destination & 0xFF) * destinationAlpha * destinationFraction);
			if (coverage < 1f)
			{
				resultAlpha = destinationAlpha + (resultAlpha - destinationAlpha) * coverage;
				r = ((destination >> 16) & 0xFF) * destinationAlpha + (r - ((destination >> 16) & 0xFF) * destinationAlpha) * coverage;
				g = ((destination >> 8) & 0xFF) * destinationAlpha + (g - ((destination >> 8) & 0xFF) * destinationAlpha) * coverage;
				b = (destination & 0xFF) * destinationAlpha + (b - (destination & 0xFF) * destinationAlpha) * coverage;
			}
			if (hasAlpha)
			{
				if (resultAlpha <= 0f)
				{
					target.ints[index] = 0;
					return;
				}
				target.ints[index] = (clamp(Math.round(resultAlpha * 255)) << 24) | (clamp(Math.round(r / resultAlpha)) << 16) | (clamp(Math.round(g / resultAlpha)) << 8)
						| clamp(Math.round(b / resultAlpha));
			}
			else
			{
				target.ints[index] = 0xFF000000 | (clamp(Math.round(r)) << 16) | (clamp(Math.round(g)) << 8) | clamp(Math.round(b));
			}
			return;
		}

		// Grayscale and binary targets are opaque.
		float sourceGray = ((77 * sourceRed + 150 * sourceGreen + 29 * sourceBlue + 128) >> 8);
		float maxLevel;
		float destinationGray;
		if (target.shorts != null)
		{
			maxLevel = 65535f;
			destinationGray = (target.shorts[index] & 0xFFFF);
			sourceGray *= 257f;
		}
		else if (targetType == ImageType.Binary)
		{
			maxLevel = 255f;
			destinationGray = target.bytes[index] == 0 ? 0f : 255f;
		}
		else
		{
			maxLevel = 255f;
			destinationGray = target.bytes[index] & 0xFF;
		}
		float result;
		if (composite == AlphaComposite.SrcOver)
		{
			float a = sourceAlpha * coverage;
			result = sourceGray * a + destinationGray * (1 - a);
		}
		else
		{
			float[] fractions = porterDuffFractions(sourceAlpha, 1f);
			float composited = sourceGray * sourceAlpha * fractions[0] + destinationGray * fractions[1];
			result = destinationGray + (composited - destinationGray) * coverage;
		}
		result = Math.max(0f, Math.min(maxLevel, result));
		if (target.shorts != null)
		{
			target.shorts[index] = (short) Math.round(result);
		}
		else if (targetType == ImageType.Binary)
		{
			target.bytes[index] = (byte) (result >= 127.5f ? 1 : 0);
		}
		else
		{
			target.bytes[index] = (byte) Math.round(result);
		}
	}

	private static int clamp(int value)
	{
		return value < 0 ? 0 : (value > 255 ? 255 : value);
	}

	/**
	 * The Porter-Duff fractions of the source and destination for the current rule, given the source's and destination's alphas.
	 */
	private float[] porterDuffFractions(float sourceAlpha, float destinationAlpha)
	{
		switch (composite)
		{
			case Clear:
				return new float[] { 0f, 0f };
			case Src:
				return new float[] { 1f, 0f };
			case Dst:
				return new float[] { 0f, 1f };
			case SrcOver:
				return new float[] { 1f, 1f - sourceAlpha };
			case DstOver:
				return new float[] { 1f - destinationAlpha, 1f };
			case SrcIn:
				return new float[] { destinationAlpha, 0f };
			case DstIn:
				return new float[] { 0f, sourceAlpha };
			case SrcOut:
				return new float[] { 1f - destinationAlpha, 0f };
			case DstOut:
				return new float[] { 0f, 1f - sourceAlpha };
			case SrcAtop:
				return new float[] { destinationAlpha, 1f - sourceAlpha };
			case DstAtop:
				return new float[] { 1f - destinationAlpha, sourceAlpha };
			case Xor:
				return new float[] { 1f - destinationAlpha, 1f - sourceAlpha };
			default:
				throw new IllegalStateException("Unrecognized composite rule: " + composite);
		}
	}
}
