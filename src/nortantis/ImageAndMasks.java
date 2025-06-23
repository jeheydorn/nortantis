package nortantis;

import java.util.ArrayList;
import java.util.List;

import nortantis.geom.IntRectangle;
import nortantis.geom.Rectangle;
import nortantis.platform.Color;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.Painter;
import nortantis.util.Coordinate;
import nortantis.util.ImageHelper;
import nortantis.util.Range;

public class ImageAndMasks
{
	private static final int opaqueThreshold = 10;
	public Image image;
	/**
	 * Used to linearly combine pixel values pulled from the background image without other icons vs the the map being drawn so far.
	 */
	private Image contentMask;
	private IntRectangle contentBounds;
	private Integer[] contentYStarts;

	/**
	 * Used to linearly combine pixel values pulled from the land background texture vs the background image without other icons. Used to
	 * blend coastline shading into icons when icons are allowed to draw over coastlines.
	 */
	private Image shadingMask;
	private IconType iconType;
	/**
	 * Either the width encoded in the icons' file name, or a width calculated based on the size of other icons in the group and the default
	 * size of icons in the group.
	 */
	public final double widthFromFileName;

	public ImageAndMasks(Image image, IconType iconType, double widthFromFileName)
	{
		this.image = image;
		this.iconType = iconType;
		this.widthFromFileName = widthFromFileName;
	}

	public ImageAndMasks(Image image, Image contentMask, IntRectangle contentBounds, Image shadingMask, IconType iconType,
			double widthFromFileName)
	{
		this(image, iconType, widthFromFileName);
		this.contentMask = contentMask;
		this.shadingMask = shadingMask;
		this.contentBounds = contentBounds;
	}

	public Image getOrCreateContentMask()
	{
		if (image == null)
		{
			return null;
		}

		if (contentMask == null)
		{
			createContentMask();
		}

		return contentMask;
	}

	/**
	 * Generates a mask image for an icon that approximately covers the content of the icon. A mask is used when drawing an icon to
	 * determine which pixels that are transparent in the icon should draw the map background vs draw the icons already drawn behind that
	 * icon. If a pixel is transparent in the icon, and the corresponding pixel is white in the mask, then the map background is drawn for
	 * that pixel. But if the map pixel is black, then there is no special handling when drawing that pixel, so whatever was drawn in that
	 * place on the map before it will be visible.
	 */
	private void createContentMask()
	{
		if (iconType == IconType.decorations)
		{
			// Decorations are special because they don't tend to have an interior in which the mask should be filled in.
			contentMask = Image.create(image.getWidth(), image.getHeight(), ImageType.Binary);
			for (int x = 0; x < contentMask.getWidth(); x++)
			{
				for (int y = 0; y < contentMask.getHeight(); y++)
				{
					int alpha = ImageHelper.getAlphaLevel(image, x, y);
					if (alpha >= opaqueThreshold)
					{
						contentMask.setGrayLevel(x, y, 255);
						addToContentBounds(new Coordinate(x, y));
					}
					else
					{
						contentMask.setGrayLevel(x, y, 0);
					}
				}
			}
		}
		else
		{
			// Top
			Image topSilhouette = Image.create(image.getWidth(), image.getHeight(), ImageType.Binary);
			{
				List<Coordinate> points = new ArrayList<>();
				for (int x = 0; x < image.getWidth(); x++)
				{
					Coordinate point = findUppermostOpaquePixel(image, x);
					if (point != null)
					{
						addToContentBounds(point);
						if (points.isEmpty())
						{
							points.add(new Coordinate(x, image.getHeight()));
						}
						points.add(point);
					}
				}

				if (points.size() < 3)
				{
					contentMask = Image.create(image.getWidth(), image.getHeight(), ImageType.Binary);
					return;
				}
				points.add(new Coordinate(points.get(points.size() - 1).x, image.getHeight()));
				drawWhitePolygonFromPoints(topSilhouette, points);
			}

			// Left side
			Image leftSilhouette = Image.create(image.getWidth(), image.getHeight(), ImageType.Binary);
			{
				List<Coordinate> points = new ArrayList<>();
				for (int y = 0; y < image.getHeight(); y++)
				{
					Coordinate point = findLeftmostOpaquePixel(image, y);
					addToContentBounds(point);
					if (point != null)
					{
						if (points.isEmpty())
						{
							points.add(new Coordinate(image.getWidth(), y));
						}
						points.add(point);
					}
				}

				points.add(new Coordinate(image.getWidth(), points.get(points.size() - 1).y));
				drawWhitePolygonFromPoints(leftSilhouette, points);

			}

			// Right side
			Image rightSilhouette = Image.create(image.getWidth(), image.getHeight(), ImageType.Binary);
			{
				List<Coordinate> points = new ArrayList<>();
				for (int y = 0; y < image.getHeight(); y++)
				{
					Coordinate point = findRightmostOpaquePixel(image, y);
					if (point != null)
					{
						if (points.isEmpty())
						{
							points.add(new Coordinate(0, y));
						}
						points.add(point);
					}
				}
				points.add(new Coordinate(0, points.get(points.size() - 1).y));
				drawWhitePolygonFromPoints(rightSilhouette, points);
			}

			// The mask image is the intersection of the 3 silhouettes.

			contentMask = Image.create(image.getWidth(), image.getHeight(), ImageType.Binary);
			for (int x = 0; x < contentMask.getWidth(); x++)
			{
				for (int y = 0; y < contentMask.getHeight(); y++)
				{
					if (topSilhouette.getGrayLevel(x, y) > 0 && leftSilhouette.getGrayLevel(x, y) > 0
							&& rightSilhouette.getGrayLevel(x, y) > 0)
					{
						contentMask.setGrayLevel(x, y, 255);
					}
				}
			}
		}
	}

	public Image cropToContent()
	{
		getOrCreateContentBounds();
		return image.getSubImage(contentBounds);
	}

	public IntRectangle getOrCreateContentBounds()
	{
		getOrCreateContentMask();
		return contentBounds;
	}

	/**
	 * Finds the lower-most y pixel value for the given x for which the content mask is not black.
	 * 
	 * @param x
	 * @return A y value, or null if the content mask is black for all y values of the given x.
	 */
	public Integer getContentYStart(int x)
	{
		if (contentYStarts == null)
		{
			createContentYStarts();
		}

		return contentYStarts[x];
	}

	private void createContentYStarts()
	{
		getOrCreateContentMask();
		contentYStarts = new Integer[image.getWidth()];
		for (int x : new Range(image.getWidth()))
		{
			if (x < contentBounds.x)
			{
				contentYStarts[x] = null;
			}
			else if (x > contentBounds.x + contentBounds.width)
			{
				contentYStarts[x] = null;
			}
			else
			{
				Coordinate point = findLowermostOpaquePixelOnMask(contentMask, x);
				if (point != null)
				{
					assert point.x == x;
					contentYStarts[x] = point.y;
				}
				else
				{
					contentYStarts[x] = null;
				}
			}
		}
	}

	private void addToContentBounds(Coordinate point)
	{
		if (point == null)
		{
			return;
		}

		if (contentBounds == null)
		{
			contentBounds = new IntRectangle(point.x, point.y, 0, 0);
		}
		else
		{
			contentBounds = contentBounds.add(point.x, point.y);
		}
	}

	public Image getOrCreateShadingMask()
	{
		if (image == null)
		{
			return null;
		}

		if (shadingMask == null)
		{
			createShadingMask();
		}

		return shadingMask;
	}

	private void createShadingMask()
	{
		getOrCreateContentMask();

		if (iconType == IconType.trees)
		{
			// Trees, in my opinion, look better without they feathered shading along the bottom that the shading mask gives.
			// Use a solid black image.
			shadingMask = Image.create(contentMask.getWidth(), contentMask.getHeight(), ImageType.Grayscale8Bit);
			return;
		}

		// Draw a line along the bottom of the content of the image.
		List<Coordinate> points = new ArrayList<>();
		for (int x = 0; x < contentMask.getWidth(); x++)
		{
			Coordinate point = findLowermostOpaquePixelOnMask(contentMask, x);
			if (point != null)
			{
				if (points.isEmpty())
				{
					points.add(point);
				}
				points.add(point);
			}
		}
		Image bottomSilhouette = Image.create(contentMask.getWidth(), contentMask.getHeight(), ImageType.Binary);
		Painter p = bottomSilhouette.createPainter();
		p.setColor(Color.white);
		int contentHeight = contentBounds.height;
		assert contentHeight >= 0;
		p.setBasicStroke(Math.max(1f, contentHeight * 0.01f));
		int[] xPoints = new int[points.size()];
		int[] yPoints = new int[points.size()];
		for (int i : new Range(points.size()))
		{
			xPoints[i] = points.get(i).x;
			yPoints[i] = points.get(i).y;
		}
		p.drawPolyline(xPoints, yPoints);

		// Blur the line up to a fraction of the height of the content
		float[][] kernel = ImageHelper.createGaussianKernel(contentHeight / 3);
		Image blurredLine = ImageHelper.convolveGrayscale(bottomSilhouette, kernel, true, true);

		// Use the content mask to set non-content pixels to zero.
		shadingMask = ImageHelper.maskWithColor(blurredLine, Color.black, contentMask, false);
	}

	private static void drawWhitePolygonFromPoints(Image image, List<Coordinate> points)
	{
		int[] xPoints = new int[points.size()];
		int[] yPoints = new int[points.size()];
		for (int i : new Range(points.size()))
		{
			Coordinate point = points.get(i);
			xPoints[i] = point.x;
			yPoints[i] = point.y;
		}

		Painter p = image.createPainter();
		p.setColor(Color.white);
		p.fillPolygon(xPoints, yPoints);
	}

	private static Coordinate findLowermostOpaquePixelOnMask(Image contentMask, int x)
	{
		assert contentMask.isGrayscaleOrBinary();

		for (int y = contentMask.getHeight() - 1; y >= 0; y--)
		{
			if (contentMask.getGrayLevel(x, y) > 0)
			{
				return new Coordinate(x, y);
			}
		}

		return null;
	}

	private static Coordinate findUppermostOpaquePixel(Image icon, int x)
	{
		for (int y = 0; y < icon.getHeight(); y++)
		{
			int alpha = ImageHelper.getAlphaLevel(icon, x, y);
			if (alpha >= opaqueThreshold)
			{
				return new Coordinate(x, y);
			}
		}

		return null;
	}

	private static Coordinate findLeftmostOpaquePixel(Image icon, int y)
	{
		for (int x = 0; x < icon.getWidth(); x++)
		{
			int alpha = ImageHelper.getAlphaLevel(icon, x, y);
			if (alpha >= opaqueThreshold)
			{
				return new Coordinate(x, y);
			}
		}

		return null;
	}

	private static Coordinate findRightmostOpaquePixel(Image icon, int y)
	{
		for (int x = icon.getWidth() - 1; x >= 0; x--)
		{
			int alpha = ImageHelper.getAlphaLevel(icon, x, y);
			if (alpha >= opaqueThreshold)
			{
				return new Coordinate(x, y);
			}
		}

		return null;
	}

	public static IntRectangle calcScaledContentBounds(Image originalContentMask, IntRectangle originalContentBounds, int scaledWidth,
			int scaledHeight)
	{
		final double xScale = (((double) scaledWidth / originalContentMask.getWidth()));
		final double yScale = (((double) scaledHeight / originalContentMask.getHeight()));

		IntRectangle scaledContentBounds = new Rectangle(originalContentBounds.x * (xScale), originalContentBounds.y * yScale,
				originalContentBounds.width * xScale, originalContentBounds.height * yScale).toIntRectangle();
		return scaledContentBounds;
	}
}
