package nortantis;

import nortantis.geom.FloatPoint;
import nortantis.geom.Point;
import nortantis.geom.Rectangle;
import nortantis.geom.RotatedRectangle;
import nortantis.platform.*;
import nortantis.util.Helper;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays out the letters of a line of text, and draws the Glow, Outline, Box, Scroll, and Banner backgrounds behind text. Background fade and
 * bold background are drawn by {@link TextDrawer}, the latter together with the letters.
 *
 * <p>
 * Everything here works in a text's unrotated frame: map pixel coordinates as they are before the text is rotated about its pivot.
 */
class TextBackgroundDrawer
{
	/**
	 * The maximum angle that text can be curved. Note that changing this would require a conversion to existing maps because the editor
	 * only stores a number between -1 and 1 for text curvature, so changing this would change the angle of curved text on existing maps.
	 */
	static final double maxTextCurveAngleRange = Math.PI;
	static final double spacingScale = 1.0 / 20.0;

	/*
	 * Sizes of the shape backgrounds, as fractions of the font's height.
	 */
	private static final double shapeHorizontalPadding = 0.45;
	private static final double shapeVerticalPadding = 0.2;
	private static final double shapeLineWidthPerLevel = 0.015;
	private static final double shapeMaxJitter = 0.1;
	private static final double shapeMaxCornerOvershoot = 0.3;
	private static final double shapeJitterControlPointSpacing = 0.9;
	/*
	 * Sizes of scroll curls and banner tails, as fractions of the height of the band holding the text.
	 */
	private static final double scrollCurlWidth = 0.42;
	private static final double scrollCurlOverhang = 0.16;
	private static final double scrollCapHeight = 0.22;
	private static final double bannerTailDrop = 0.2;
	private static final double bannerTailLength = 0.5;
	private static final double bannerFoldWidth = 0.2;
	private static final double bannerNotchDepth = 0.22;
	private static final float shadeScale = 0.82f;
	private static final float darkShadeScale = 0.66f;
	/*
	 * Outline's width and Glow's reach, as fractions of the font's height per level of TextBackground.outlineWidth and
	 * TextBackground.glowSize.
	 */
	private static final double outlineWidthPerLevel = 0.0125;
	private static final double glowReachPerLevel = 0.03;
	private static final double glowExpansionFraction = 0.25;

	/**
	 * One letter of a line of text, placed where it is drawn: at (x, baselineY), then rotated by rotation about pivot, if pivot is not null.
	 */
	static final class PlacedLetter
	{
		final String letter;
		final double x;
		final double baselineY;
		final double width;
		final double rotation;
		final Point pivot;

		PlacedLetter(String letter, double x, double baselineY, double width, double rotation, Point pivot)
		{
			this.letter = letter;
			this.x = x;
			this.baselineY = baselineY;
			this.width = width;
			this.rotation = rotation;
			this.pivot = pivot;
		}
	}

	/**
	 * A line of text laid out for drawing.
	 */
	static final class LineLayout
	{
		final String text;
		final Point textStart;
		final List<PlacedLetter> letters;
		/**
		 * The center of the circle a curved line follows, or null for a straight line.
		 */
		final Point circleCenter;
		/**
		 * 1 when a curved line's circle is below it (the line is concave down), -1 when the circle is above it.
		 */
		final double curveSign;
		/**
		 * True when the line can be drawn as one string, which keeps the font's kerning.
		 */
		final boolean canDrawAsOneString;
		final double ascent;
		final double descent;

		LineLayout(String text, Point textStart, List<PlacedLetter> letters, Point circleCenter, double curveSign, boolean canDrawAsOneString, double ascent,
				double descent)
		{
			this.text = text;
			this.textStart = textStart;
			this.letters = letters;
			this.circleCenter = circleCenter;
			this.curveSign = curveSign;
			this.canDrawAsOneString = canDrawAsOneString;
			this.ascent = ascent;
			this.descent = descent;
		}

		double getTotalWidth()
		{
			if (letters.isEmpty())
			{
				return 0;
			}
			PlacedLetter first = letters.get(0);
			PlacedLetter last = letters.get(letters.size() - 1);
			return last.x + last.width - first.x;
		}
	}

	/**
	 * Lays out a line of text, using the painter's current font for measurements.
	 *
	 * @param textStart
	 *            Where the line starts, before curvature, with y at the baseline.
	 */
	static LineLayout layoutLine(Painter p, String line, Point textStart, double curvature, int spacing)
	{
		List<PlacedLetter> letters = new ArrayList<>(line.length());
		double ascent = p.getFontAscent();
		double descent = p.getFontDescent();
		double adjustedSpacing = line.length() < 2 ? 0.0 : spacing * ascent * spacingScale;
		double startXDiffFromSpacing = (adjustedSpacing * (line.length() - 1)) / 2.0;

		if (Math.abs(curvature) <= 0.001)
		{
			double x = textStart.x - startXDiffFromSpacing;
			for (int i = 0; i < line.length(); i++)
			{
				char c = line.charAt(i);
				int charWidth = p.charWidth(c);
				letters.add(new PlacedLetter("" + c, x, textStart.y, charWidth, 0.0, null));
				x += charWidth + adjustedSpacing;
			}
			return new LineLayout(line, textStart, letters, null, 0, spacing == 0, ascent, descent);
		}

		double totalWidth = p.stringWidth(line) + (line.length() > 0 ? (line.length() - 1) * adjustedSpacing : 0.0);
		Point textCenter = textStart.add(new Point((totalWidth / 2.0) - startXDiffFromSpacing, 0));
		double angleRange = Math.abs(curvature * maxTextCurveAngleRange);
		double radius = (totalWidth / 2.0) / angleRange;
		// Concave down text curves along its baseline, around a circle below it. Concave up text curves along its ascender line, around a
		// circle above it.
		Point circleCenter = curvature > 0 ? textCenter.add(new Point(0.0, radius)) : textCenter.add(new Point(0.0, -radius - ascent));
		double widthSoFar = 0.0;
		for (int i = 0; i < line.length(); i++)
		{
			char c = line.charAt(i);
			double charWidth = p.charWidth(c);
			double theta = -angleRange + ((widthSoFar + charWidth / 2.0) / totalWidth) * (angleRange * 2.0);
			letters.add(new PlacedLetter("" + c, textCenter.x - (charWidth / 2.0), textCenter.y, charWidth, curvature > 0 ? theta : -theta, circleCenter));
			widthSoFar += charWidth + adjustedSpacing;
		}
		return new LineLayout(line, textStart, letters, circleCenter, curvature > 0 ? 1 : -1, false, ascent, descent);
	}

	/**
	 * Draws the letters of a line with the painter's current font and color. When boldFont is not null, each letter is drawn first in it, in
	 * boldColor, behind itself.
	 */
	static void drawLetters(Painter p, LineLayout layout, Font boldFont, Color boldColor)
	{
		if (layout.canDrawAsOneString && boldFont == null)
		{
			p.drawString(layout.text, layout.textStart.x, layout.textStart.y);
			return;
		}

		Font font = p.getFont();
		Color color = p.getColor();
		try
		{
			for (PlacedLetter letter : layout.letters)
			{
				if (boldFont != null)
				{
					p.setFont(boldFont);
					p.setColor(boldColor);
					drawLetter(p, letter);
					p.setFont(font);
					p.setColor(color);
				}
				drawLetter(p, letter);
			}
		}
		finally
		{
			p.setFont(font);
			p.setColor(color);
		}
	}

	private static void drawLetter(Painter p, PlacedLetter letter)
	{
		if (letter.pivot == null)
		{
			p.drawString(letter.letter, letter.x, letter.baselineY);
			return;
		}

		Transform original = p.getTransform();
		try
		{
			p.rotate(letter.rotation, letter.pivot.x, letter.pivot.y);
			p.drawString(letter.letter, letter.x, letter.baselineY);
		}
		finally
		{
			p.setTransform(original);
		}
	}

	/**
	 * How far Glow or Outline reaches beyond the letters, in pixels.
	 */
	static double getHaloReach(TextBackground background, int fontHeight)
	{
		if (background.effect == TextBackgroundEffect.Outline)
		{
			return getOutlineWidth(background, fontHeight);
		}
		double glowReach = getGlowReach(background, fontHeight);
		return glowReach * (1.0 + glowExpansionFraction);
	}

	private static double getOutlineWidth(TextBackground background, int fontHeight)
	{
		return background.outlineWidth * outlineWidthPerLevel * fontHeight;
	}

	private static double getGlowReach(TextBackground background, int fontHeight)
	{
		return background.glowSize * glowReachPerLevel * fontHeight;
	}

	/**
	 * The most a background can reach past the letters in any direction, in pixels, not counting fade. Used where a text's extent is needed
	 * before its lines are laid out.
	 */
	static double getMaxReach(TextBackground background, int fontHeight)
	{
		if (background.effect.isHalo())
		{
			return getHaloReach(background, fontHeight);
		}
		if (background.effect.isShape())
		{
			// The tallest band holds two lines. Banner tails reach farthest from it.
			double bandHeight = 2 * fontHeight + 2 * shapeVerticalPadding * fontHeight;
			double lineWidth = getShapeLineWidth(background, fontHeight);
			double reach = shapeHorizontalPadding * fontHeight + Math.max(bannerTailLength, scrollCurlWidth) * bandHeight
					+ (shapeMaxJitter + shapeMaxCornerOvershoot) * fontHeight + lineWidth;
			// Curved text bends the band, so its corners can reach a little past its padding.
			return reach * 1.25;
		}
		if (background.effect == TextBackgroundEffect.BoldBackground)
		{
			// Bold letters are a little wider than the letters they are drawn behind.
			return 0.1 * fontHeight;
		}
		return 0;
	}

	/**
	 * The smallest rectangle holding the drawn shapes of a line's letters. These can reach past the line's bounds, such as a script font's
	 * swashes and descenders.
	 */
	static Rectangle getLetterShapeBounds(Painter p, LineLayout layout)
	{
		if (layout.canDrawAsOneString)
		{
			return p.getStringVisualBounds(layout.text).translate(layout.textStart.x, layout.textStart.y);
		}

		Rectangle result = null;
		for (PlacedLetter letter : layout.letters)
		{
			Rectangle letterBounds = p.getStringVisualBounds(letter.letter).translate(letter.x, letter.baselineY);
			if (letter.pivot != null)
			{
				letterBounds = new RotatedRectangle(letterBounds, letter.rotation, letter.pivot).getBounds();
			}
			result = letterBounds.add(result);
		}
		return result;
	}

	/**
	 * The area a halo covers around the given letter bounds.
	 */
	static Rectangle padForHalo(Rectangle bounds, TextBackground background, int fontHeight)
	{
		if (bounds == null)
		{
			return null;
		}
		double reach = getHaloReach(background, fontHeight);
		return new Rectangle(bounds.x - reach, bounds.y - reach, bounds.width + reach * 2, bounds.height + reach * 2);
	}

	private static double getShapeLineWidth(TextBackground background, int fontHeight)
	{
		return background.shapeLineWidth * shapeLineWidthPerLevel * fontHeight;
	}

	/**
	 * Draws Glow or Outline behind the given lines.
	 *
	 * @param p
	 *            The painter of the map, with the transform the text is drawn with.
	 * @param unrotatedTransform
	 *            The painter's transform before the text's rotation and the draw offset were applied.
	 * @param extent
	 *            The area the halo covers, in the text's unrotated frame.
	 * @param drawOffset
	 *            The location in the map of the image being drawn on.
	 */
	static void drawHalo(Painter p, Transform unrotatedTransform, TextBackground background, List<LineLayout> layouts, Rectangle extent, double angle, Point pivot, Point drawOffset,
			int fontHeight)
	{
		Rectangle boundsInMap = new RotatedRectangle(extent, angle, pivot).getBounds();
		final int margin = 2;
		int originX = (int) Math.floor(boundsInMap.x - drawOffset.x) - margin;
		int originY = (int) Math.floor(boundsInMap.y - drawOffset.y) - margin;
		int width = (int) Math.ceil(boundsInMap.width) + margin * 2 + 1;
		int height = (int) Math.ceil(boundsInMap.height) + margin * 2 + 1;
		if (width <= 0 || height <= 0)
		{
			return;
		}

		boolean isGlow = background.effect == TextBackgroundEffect.Glow;
		double expansion = isGlow ? getGlowReach(background, fontHeight) * glowExpansionFraction : getOutlineWidth(background, fontHeight);

		try (Image mask = Image.create(width, height, ImageType.Grayscale8Bit))
		{
			try (Painter maskPainter = mask.createPainter(DrawQuality.High))
			{
				maskPainter.setFont(p.getFont());
				maskPainter.setColor(Color.white);
				maskPainter.translate(-originX - drawOffset.x, -originY - drawOffset.y);
				maskPainter.rotate(angle, pivot.x, pivot.y);
				for (Point offset : createDiskOffsets(expansion))
				{
					maskPainter.translate(offset.x, offset.y);
					for (LineLayout layout : layouts)
					{
						drawLetters(maskPainter, layout, null, null);
					}
					maskPainter.translate(-offset.x, -offset.y);
				}
			}

			Image alpha = mask;
			if (isGlow)
			{
				// The blur's kernel reaches three standard deviations, which is half of the blur level, so this level fades the glow out
				// over the glow's reach.
				int blurLevel = Math.max(1, (int) Math.round(2 * getGlowReach(background, fontHeight)));
				final float glowStrength = 2f;
				alpha = ImageHelper.getInstance().blurAndScale(mask, blurLevel, glowStrength, true);
			}

			try (Image overlay = createColoredImage(background.color, alpha))
			{
				Transform transform = p.getTransform();
				try
				{
					p.setTransform(unrotatedTransform);
					p.setAlphaComposite(AlphaComposite.SrcOver, background.color.getAlpha() / 255f);
					p.drawImage(overlay, originX, originY);
				}
				finally
				{
					p.setAlphaComposite(AlphaComposite.SrcOver);
					p.setTransform(transform);
				}
			}
			finally
			{
				if (alpha != mask)
				{
					alpha.close();
				}
			}
		}
	}

	/**
	 * Offsets that fill a disk of the given radius closely enough that drawing a shape at each one draws the shape grown by that radius.
	 */
	private static List<Point> createDiskOffsets(double radius)
	{
		List<Point> result = new ArrayList<>();
		result.add(new Point(0, 0));
		if (radius <= 0)
		{
			return result;
		}

		final double maxGapBetweenOffsets = 0.8;
		final int maxRings = 4;
		final int maxOffsetsPerRing = 64;
		int ringCount = Math.max(1, Math.min(maxRings, (int) Math.ceil(radius / maxGapBetweenOffsets)));
		for (int ring = 1; ring <= ringCount; ring++)
		{
			double ringRadius = radius * ring / ringCount;
			int count = Math.max(6, Math.min(maxOffsetsPerRing, (int) Math.ceil(2 * Math.PI * ringRadius / maxGapBetweenOffsets)));
			for (int i = 0; i < count; i++)
			{
				double angle = 2 * Math.PI * i / count;
				result.add(new Point(ringRadius * Math.cos(angle), ringRadius * Math.sin(angle)));
			}
		}
		return result;
	}

	private static Image createColoredImage(Color color, Image alphaMask)
	{
		Image result = Image.create(alphaMask.getWidth(), alphaMask.getHeight(), ImageType.ARGB);
		int maxLevel = alphaMask.getMaxPixelLevel();
		try (PixelReader maskPixels = alphaMask.createPixelReader(); PixelWriter resultPixels = result.createPixelWriter())
		{
			for (int y = 0; y < alphaMask.getHeight(); y++)
			{
				for (int x = 0; x < alphaMask.getWidth(); x++)
				{
					int level = maskPixels.getGrayLevel(x, y);
					int alpha = Math.min(255, (int) Math.round(255.0 * level / maxLevel));
					resultPixels.setRGB(x, y, color.getRed(), color.getGreen(), color.getBlue(), alpha);
				}
			}
		}
		return result;
	}

	/**
	 * One part of a shape background: a polygon or polyline in the text's unrotated frame, filled, stroked, or both.
	 */
	static final class ShapePiece
	{
		final List<Point> points;
		final boolean isClosed;
		final Color fillColor;
		final boolean isStroked;

		ShapePiece(List<Point> points, boolean isClosed, Color fillColor, boolean isStroked)
		{
			this.points = points;
			this.isClosed = isClosed;
			this.fillColor = fillColor;
			this.isStroked = isStroked;
		}
	}

	/**
	 * The parts of a Box, Scroll, or Banner background, drawn in order.
	 */
	static final class Shape
	{
		final List<ShapePiece> pieces;
		final Color lineColor;
		final double lineWidth;
		/**
		 * Everything the shape draws on, in the text's unrotated frame.
		 */
		final Rectangle bounds;

		Shape(List<ShapePiece> pieces, Color lineColor, double lineWidth)
		{
			this.pieces = pieces;
			this.lineColor = lineColor;
			this.lineWidth = lineWidth;
			Rectangle bounds = null;
			for (ShapePiece piece : pieces)
			{
				for (Point point : piece.points)
				{
					bounds = bounds == null ? new Rectangle(point.x, point.y, 0, 0) : bounds.add(point);
				}
			}
			double padding = lineWidth / 2.0 + 1.0;
			this.bounds = bounds == null ? null : new Rectangle(bounds.x - padding, bounds.y - padding, bounds.width + padding * 2, bounds.height + padding * 2);
		}

		void draw(Painter p)
		{
			for (ShapePiece piece : pieces)
			{
				List<FloatPoint> points = toFloatPoints(piece.points);
				if (piece.fillColor != null && piece.isClosed)
				{
					p.setColor(piece.fillColor);
					p.fillPolygonFloat(points);
				}
				if (piece.isStroked && lineWidth > 0)
				{
					p.setColor(lineColor);
					p.setBasicStroke((float) lineWidth);
					if (piece.isClosed)
					{
						p.drawPolygonFloat(points);
					}
					else
					{
						p.drawPolylineFloat(points);
					}
				}
			}
		}

		private static List<FloatPoint> toFloatPoints(List<Point> points)
		{
			List<FloatPoint> result = new ArrayList<>(points.size());
			for (Point point : points)
			{
				result.add(new FloatPoint((float) point.x, (float) point.y));
			}
			return result;
		}
	}

	/**
	 * Maps between the text's unrotated frame and a frame that follows the text's curve: "along" runs left to right along the text and
	 * "height" runs up away from it. For straight text this is just the unrotated frame with y flipped. For curved text, along is an angle
	 * around the circle the text follows, scaled to a distance at the middle of the band, and height is a distance from the circle's center.
	 * Shapes are built in this frame so that they bend with the text.
	 */
	private static final class BandFrame
	{
		final Point origin;
		final Point center;
		final double sign;
		double referenceRadius = 1.0;

		BandFrame(Point origin, Point center, double sign)
		{
			this.origin = origin;
			this.center = center;
			this.sign = sign;
		}

		Point toPoint(double along, double height)
		{
			if (center == null)
			{
				return new Point(origin.x + along, origin.y - height);
			}
			double theta = along / referenceRadius;
			double radius = sign * height;
			return new Point(center.x + radius * Math.sin(theta), center.y - sign * radius * Math.cos(theta));
		}

		/**
		 * @return The angle about the center (or the x offset from the origin for straight text) and the height.
		 */
		double[] toAngleAndHeight(Point point)
		{
			if (center == null)
			{
				return new double[] { point.x - origin.x, origin.y - point.y };
			}
			double dx = point.x - center.x;
			double dy = point.y - center.y;
			return new double[] { Math.atan2(dx, -sign * dy), sign * Math.sqrt(dx * dx + dy * dy) };
		}

		double angleToAlong(double angle)
		{
			return center == null ? angle : angle * referenceRadius;
		}
	}

	/**
	 * Creates the Box, Scroll, or Banner background for the given lines, in the text's unrotated frame.
	 *
	 * @param letterBounds
	 *            The bounds of each line's letters, including curvature and spacing.
	 * @param pivot
	 *            The point the text rotates about, which the shape's wobble is measured from so it stays the same when the text moves.
	 */
	static Shape createShape(TextBackground background, List<LineLayout> layouts, List<Rectangle> letterBounds, int fontHeight, Point pivot, long seed)
	{
		// Curved text follows the circle of its widest line.
		LineLayout widest = null;
		for (LineLayout layout : layouts)
		{
			if (widest == null || layout.getTotalWidth() > widest.getTotalWidth())
			{
				widest = layout;
			}
		}
		BandFrame frame = new BandFrame(pivot, widest == null ? null : widest.circleCenter, widest == null ? 1 : widest.curveSign);

		List<Point> corners = new ArrayList<>();
		if (frame.center == null)
		{
			for (Rectangle bounds : letterBounds)
			{
				if (bounds != null)
				{
					corners.add(bounds.upperLeftCorner());
					corners.add(new Point(bounds.x + bounds.width, bounds.y + bounds.height));
				}
			}
		}
		else
		{
			for (LineLayout layout : layouts)
			{
				for (PlacedLetter letter : layout.letters)
				{
					addLetterCorners(letter, layout, corners);
				}
			}
		}
		if (corners.isEmpty())
		{
			return null;
		}

		double minAngle = Double.POSITIVE_INFINITY, maxAngle = Double.NEGATIVE_INFINITY, minHeight = Double.POSITIVE_INFINITY, maxHeight = Double.NEGATIVE_INFINITY;
		for (Point corner : corners)
		{
			double[] angleAndHeight = frame.toAngleAndHeight(corner);
			minAngle = Math.min(minAngle, angleAndHeight[0]);
			maxAngle = Math.max(maxAngle, angleAndHeight[0]);
			minHeight = Math.min(minHeight, angleAndHeight[1]);
			maxHeight = Math.max(maxHeight, angleAndHeight[1]);
		}
		if (frame.center != null)
		{
			frame.referenceRadius = Math.abs((minHeight + maxHeight) / 2.0);
		}

		double paddingX = shapeHorizontalPadding * fontHeight;
		double paddingY = shapeVerticalPadding * fontHeight;
		Band band = new Band(frame.angleToAlong(minAngle) - paddingX, frame.angleToAlong(maxAngle) + paddingX, minHeight - paddingY, maxHeight + paddingY);

		double jitterFraction = Math.max(0, Math.min(TextBackground.maxShapeJitter, background.shapeJitter)) / (double) TextBackground.maxShapeJitter;
		ShapeBuilder builder = new ShapeBuilder(frame, fontHeight, jitterFraction * shapeMaxJitter * fontHeight, seed);
		Color fill = background.shapeFillColor;
		Color shade = scaleColor(fill, shadeScale);
		Color darkShade = scaleColor(fill, darkShadeScale);
		List<ShapePiece> pieces = new ArrayList<>();
		switch (background.effect)
		{
		case Box -> addBoxPieces(builder, band, fill, jitterFraction * shapeMaxCornerOvershoot * fontHeight, pieces);
		case Scroll -> addScrollPieces(builder, band, fill, shade, darkShade, pieces);
		case Banner -> addBannerPieces(builder, band, fill, shade, darkShade, pieces);
		default -> throw new IllegalArgumentException("Not a shape background: " + background.effect);
		}
		return new Shape(pieces, background.shapeLineColor, getShapeLineWidth(background, fontHeight));
	}

	private static void addLetterCorners(PlacedLetter letter, LineLayout layout, List<Point> corners)
	{
		double top = letter.baselineY - layout.ascent;
		double bottom = letter.baselineY + layout.descent;
		Point[] letterCorners = { new Point(letter.x, top), new Point(letter.x + letter.width, top), new Point(letter.x, bottom), new Point(letter.x + letter.width, bottom) };
		for (Point corner : letterCorners)
		{
			corners.add(letter.pivot == null ? corner : corner.rotate(letter.pivot, letter.rotation));
		}
	}

	/**
	 * The rectangle in the band frame that holds the text and its padding.
	 */
	private record Band(double left, double right, double bottom, double top)
	{
		double height()
		{
			return top - bottom;
		}
	}

	/**
	 * Builds the outlines of a shape in the band frame, wobbling them, and converts them to the text's unrotated frame.
	 */
	private static final class ShapeBuilder
	{
		final BandFrame frame;
		final double fontHeight;
		final double jitter;
		final long seed;
		final double step;
		int nextLineIndex;

		ShapeBuilder(BandFrame frame, double fontHeight, double jitter, long seed)
		{
			this.frame = frame;
			this.fontHeight = fontHeight;
			this.jitter = jitter;
			this.seed = seed;
			this.step = Math.max(1.0, 0.06 * fontHeight);
		}

		/**
		 * Points along a straight line in the band frame, from start to end inclusive.
		 */
		List<double[]> line(double along1, double height1, double along2, double height2)
		{
			double length = Math.hypot(along2 - along1, height2 - height1);
			int count = Math.max(1, (int) Math.ceil(length / step));
			List<double[]> result = new ArrayList<>(count + 1);
			for (int i = 0; i <= count; i++)
			{
				double t = i / (double) count;
				result.add(new double[] { along1 + (along2 - along1) * t, height1 + (height2 - height1) * t });
			}
			return result;
		}

		/**
		 * Points along part of an ellipse in the band frame. Angles are in radians, counterclockwise from the right, with height up.
		 */
		List<double[]> arc(double centerAlong, double centerHeight, double radiusAlong, double radiusHeight, double startAngle, double endAngle)
		{
			double approximateLength = Math.abs(endAngle - startAngle) * Math.max(radiusAlong, radiusHeight);
			int count = Math.max(6, (int) Math.ceil(approximateLength / step));
			List<double[]> result = new ArrayList<>(count + 1);
			for (int i = 0; i <= count; i++)
			{
				double angle = startAngle + (endAngle - startAngle) * i / count;
				result.add(new double[] { centerAlong + radiusAlong * Math.cos(angle), centerHeight + radiusHeight * Math.sin(angle) });
			}
			return result;
		}

		/**
		 * Moves each point perpendicular to the line by a smoothly wandering amount, and converts the result to the text's unrotated frame.
		 * Each call wobbles independently, so two lines that cross at a corner don't wobble together.
		 */
		List<Point> finish(List<double[]> bandPoints, boolean isClosed)
		{
			long lineSeed = Helper.mixSeed(seed + nextLineIndex++);
			List<Point> result = new ArrayList<>(bandPoints.size());
			int count = bandPoints.size();
			for (int i = 0; i < count; i++)
			{
				double[] point = bandPoints.get(i);
				double along = point[0];
				double height = point[1];
				if (jitter > 0 && count > 1)
				{
					double[] before = bandPoints.get(isClosed ? Math.floorMod(i - 1, count) : Math.max(0, i - 1));
					double[] after = bandPoints.get(isClosed ? (i + 1) % count : Math.min(count - 1, i + 1));
					double directionAlong = after[0] - before[0];
					double directionHeight = after[1] - before[1];
					double length = Math.hypot(directionAlong, directionHeight);
					if (length > 0)
					{
						// Sampled relative to the font's height so the wobble is the same at every resolution and font size.
						double amount = jitter * Helper.sampleSmoothNoise(lineSeed, along / fontHeight, height / fontHeight, shapeJitterControlPointSpacing);
						along += -amount * directionHeight / length;
						height += amount * directionAlong / length;
					}
				}
				result.add(frame.toPoint(along, height));
			}
			return result;
		}

		/**
		 * A closed outline made of several lines joined end to end, wobbled with one seed per line so it matches outlines drawn one line at a
		 * time.
		 */
		List<Point> finishJoined(List<List<double[]>> lines)
		{
			List<Point> result = new ArrayList<>();
			for (List<double[]> line : lines)
			{
				List<Point> finished = finish(line, false);
				result.addAll(finished.subList(0, finished.size() - 1));
			}
			return result;
		}

		List<double[]> polygon(double[]... corners)
		{
			List<double[]> result = new ArrayList<>();
			for (int i = 0; i < corners.length; i++)
			{
				double[] from = corners[i];
				double[] to = corners[(i + 1) % corners.length];
				List<double[]> edge = line(from[0], from[1], to[0], to[1]);
				result.addAll(edge.subList(0, edge.size() - 1));
			}
			return result;
		}
	}

	private static void addBoxPieces(ShapeBuilder builder, Band band, Color fill, double cornerOvershoot, List<ShapePiece> pieces)
	{
		List<List<double[]>> edges = new ArrayList<>();
		edges.add(builder.line(band.left, band.top, band.right, band.top));
		edges.add(builder.line(band.right, band.top, band.right, band.bottom));
		edges.add(builder.line(band.right, band.bottom, band.left, band.bottom));
		edges.add(builder.line(band.left, band.bottom, band.left, band.top));

		if (cornerOvershoot <= 0)
		{
			pieces.add(new ShapePiece(builder.finishJoined(edges), true, fill, true));
			return;
		}

		// The edges are drawn as separate strokes that run a little past the corners, as a box drawn by hand does. The fill follows the same
		// wobble so the strokes stay on its edges.
		int firstLineIndex = builder.nextLineIndex;
		pieces.add(new ShapePiece(builder.finishJoined(edges), true, fill, false));
		builder.nextLineIndex = firstLineIndex;
		double o = cornerOvershoot;
		pieces.add(new ShapePiece(builder.finish(builder.line(band.left - o, band.top, band.right + o, band.top), false), false, null, true));
		pieces.add(new ShapePiece(builder.finish(builder.line(band.right, band.top + o, band.right, band.bottom - o), false), false, null, true));
		pieces.add(new ShapePiece(builder.finish(builder.line(band.right + o, band.bottom, band.left - o, band.bottom), false), false, null, true));
		pieces.add(new ShapePiece(builder.finish(builder.line(band.left, band.bottom - o, band.left, band.top + o), false), false, null, true));
	}

	private static void addScrollPieces(ShapeBuilder builder, Band band, Color fill, Color shade, Color darkShade, List<ShapePiece> pieces)
	{
		double height = band.height();
		double curlWidth = scrollCurlWidth * height;
		double overhang = scrollCurlOverhang * height;
		double capHeight = scrollCapHeight * curlWidth;

		pieces.add(new ShapePiece(builder.finish(builder.polygon(new double[] { band.left, band.top }, new double[] { band.right, band.top }, new double[] { band.right, band.bottom },
				new double[] { band.left, band.bottom }), true), true, fill, true));

		for (boolean isLeft : new boolean[] { true, false })
		{
			// Each end of the paper is rolled into a curl that sits over the end of the band, a little taller than it.
			double inner = isLeft ? band.left + curlWidth * 0.4 : band.right - curlWidth * 0.4;
			double outer = isLeft ? band.left - curlWidth * 0.6 : band.right + curlWidth * 0.6;
			double left = Math.min(inner, outer);
			double right = Math.max(inner, outer);
			double middle = (left + right) / 2.0;
			double radius = (right - left) / 2.0;
			double top = band.top + overhang;
			double bottom = band.bottom - overhang;

			List<double[]> silhouette = new ArrayList<>();
			silhouette.addAll(builder.line(left, top, left, bottom));
			silhouette.addAll(builder.arc(middle, bottom, radius, capHeight, Math.PI, 2 * Math.PI));
			silhouette.addAll(builder.line(right, bottom, right, top));
			silhouette.addAll(builder.arc(middle, top, radius, capHeight, 0, Math.PI));
			int silhouetteLineIndex = builder.nextLineIndex;
			List<Point> silhouettePoints = builder.finish(silhouette, true);
			pieces.add(new ShapePiece(silhouettePoints, true, fill, false));

			// The side of the roll facing away from the text is in shadow. The shadow is cut from the roll's own wobbled outline, so it reaches
			// the curve of the roll's bottom and stays inside the outline however much the outline wanders.
			double shadeEdge = isLeft ? left + (right - left) * 0.35 : right - (right - left) * 0.35;
			pieces.add(new ShapePiece(selectRun(silhouette, silhouettePoints, bandPoint -> isLeft ? bandPoint[0] <= shadeEdge : bandPoint[0] >= shadeEdge), true, shade,
					false));

			builder.nextLineIndex = silhouetteLineIndex;
			pieces.add(new ShapePiece(builder.finish(silhouette, true), true, null, true));

			// The top of the roll shows the spiral of rolled paper.
			pieces.add(new ShapePiece(builder.finish(builder.arc(middle, top, radius, capHeight, 0, 2 * Math.PI), true), true, darkShade, true));
			double spiralOffset = (isLeft ? 1 : -1) * radius * 0.2;
			pieces.add(new ShapePiece(builder.finish(builder.arc(middle + spiralOffset, top, radius * 0.45, capHeight * 0.45, 0, 2 * Math.PI), true), true, null, true));
		}
	}

	/**
	 * The finished points of a closed outline whose band points pass the given test, which must select one unbroken run of the outline,
	 * possibly wrapping past its first point. The result is closed by a straight line from the run's end back to its start.
	 */
	private static List<Point> selectRun(List<double[]> bandPoints, List<Point> finishedPoints, java.util.function.Predicate<double[]> isSelected)
	{
		int count = bandPoints.size();
		int start = -1;
		for (int i = 0; i < count; i++)
		{
			int previous = (i - 1 + count) % count;
			if (isSelected.test(bandPoints.get(i)) && !isSelected.test(bandPoints.get(previous)))
			{
				start = i;
				break;
			}
		}
		List<Point> result = new ArrayList<>();
		if (start < 0)
		{
			// Every point or none is selected.
			if (count > 0 && isSelected.test(bandPoints.get(0)))
			{
				result.addAll(finishedPoints);
			}
			return result;
		}
		for (int i = 0; i < count; i++)
		{
			int index = (start + i) % count;
			if (!isSelected.test(bandPoints.get(index)))
			{
				break;
			}
			result.add(finishedPoints.get(index));
		}
		return result;
	}

	private static void addBannerPieces(ShapeBuilder builder, Band band, Color fill, Color shade, Color darkShade, List<ShapePiece> pieces)
	{
		double height = band.height();
		double drop = bannerTailDrop * height;
		double tailLength = bannerTailLength * height;
		double foldWidth = bannerFoldWidth * height;
		double notchDepth = bannerNotchDepth * height;
		double tailTop = band.top - drop;
		double tailBottom = band.bottom - drop;

		for (boolean isLeft : new boolean[] { true, false })
		{
			double direction = isLeft ? -1 : 1;
			double panelEnd = isLeft ? band.left : band.right;
			double tailInner = panelEnd - direction * foldWidth;
			double tailOuter = panelEnd + direction * tailLength;

			// The tail sits behind the panel and lower than it, and ends in a forked notch.
			pieces.add(new ShapePiece(builder.finish(builder.polygon(new double[] { tailInner, tailTop }, new double[] { tailOuter, tailTop },
					new double[] { tailOuter - direction * notchDepth, (tailTop + tailBottom) / 2.0 }, new double[] { tailOuter, tailBottom }, new double[] { tailInner, tailBottom }),
					true), true, shade, true));

			// Where the ribbon folds back from the panel to the tail.
			pieces.add(new ShapePiece(builder.finish(builder.polygon(new double[] { panelEnd, band.bottom }, new double[] { tailInner, tailBottom }, new double[] { tailInner, band.bottom }),
					true), true, darkShade, true));
		}

		pieces.add(new ShapePiece(builder.finish(builder.polygon(new double[] { band.left, band.top }, new double[] { band.right, band.top }, new double[] { band.right, band.bottom },
				new double[] { band.left, band.bottom }), true), true, fill, true));
	}

	private static Color scaleColor(Color color, float scale)
	{
		return Color.create((int) (color.getRed() * scale), (int) (color.getGreen() * scale), (int) (color.getBlue() * scale), color.getAlpha());
	}
}
