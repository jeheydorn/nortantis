package nortantis;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.ArrayList;
import java.util.List;

import nortantis.util.Coordinate;
import nortantis.util.ImageHelper;
import nortantis.util.Range;

public class ImageAndMasks
{
	private static final int opaqueThreshold = 50;
	public BufferedImage image;
	/**
	 * Used to linearly combine pixel values pulled from the background image without other icons vs the the map being drawn so far.
	 */
	public BufferedImage contentMask;
	private int contentHeight;

	/**
	 * Used to linearly combine pixel values pulled from the land background texture vs the background image without other icons. Used to
	 * blend coastline shading into icons when icons are allowed to draw over coastlines.
	 */
	private BufferedImage shadingMask;


	public ImageAndMasks(BufferedImage image)
	{
		this.image = image;
	}

	public ImageAndMasks(BufferedImage image, BufferedImage contentMask, BufferedImage shadingMask)
	{
		this(image);
		this.contentMask = contentMask;
		this.shadingMask = shadingMask;
	}

	public BufferedImage getOrCreateContentMask()
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
		// Top
		BufferedImage topSilhouette = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
		{
			List<Coordinate> points = new ArrayList<>();
			for (int x = 0; x < image.getWidth(); x++)
			{
				Coordinate point = findUppermostOpaquePixel(image, x);
				if (point != null)
				{
					if (points.isEmpty())
					{
						points.add(new Coordinate(x, image.getHeight()));
					}
					points.add(point);
				}
			}

			if (points.size() < 3)
			{
				contentMask = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
				return;
			}
			points.add(new Coordinate(points.get(points.size() - 1).x, image.getHeight()));
			drawWhitePolygonFromPoints(topSilhouette, points);
		}

		// Left side
		BufferedImage leftSilhouette = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
		{
			List<Coordinate> points = new ArrayList<>();
			for (int y = 0; y < image.getHeight(); y++)
			{
				Coordinate point = findLeftmostOpaquePixel(image, y);
				if (point != null)
				{
					if (points.isEmpty())
					{
						points.add(new Coordinate(image.getWidth(), y));
					}
					points.add(point);
				}
			}

			if (points.size() > 1)
			{
				contentHeight = points.get(points.size() - 1).y - points.get(0).y + 1;
			}

			points.add(new Coordinate(image.getWidth(), points.get(points.size() - 1).y));
			drawWhitePolygonFromPoints(leftSilhouette, points);

		}

		// Right side
		BufferedImage rightSilhouette = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
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

		contentMask = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
		WritableRaster maskRaster = contentMask.getRaster();
		Raster topRaster = topSilhouette.getRaster();
		Raster leftRaster = leftSilhouette.getRaster();
		Raster rightRaster = rightSilhouette.getRaster();
		for (int x = 0; x < contentMask.getWidth(); x++)
		{
			for (int y = 0; y < contentMask.getHeight(); y++)
			{
				if (topRaster.getSample(x, y, 0) > 0 && leftRaster.getSample(x, y, 0) > 0 && rightRaster.getSample(x, y, 0) > 0)
				{
					maskRaster.setSample(x, y, 0, 255);
				}
			}
		}
	}

	public BufferedImage getOrCreateShadingMask()
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
		BufferedImage bottomSilhouette = new BufferedImage(contentMask.getWidth(), contentMask.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
		Graphics2D g = bottomSilhouette.createGraphics();
		g.setColor(Color.white);
		g.setStroke(new BasicStroke(Math.max(1f, contentHeight * 0.01f)));
		int[] xPoints = new int[points.size()];
		int[] yPoints = new int[points.size()];
		for (int i : new Range(points.size()))
		{
			xPoints[i] = points.get(i).x;
			yPoints[i] = points.get(i).y;
		}
		g.drawPolyline(xPoints, yPoints, points.size());

		// Blur the line up to 25% of the height of the content
		float[][] kernel = ImageHelper.createGaussianKernel(contentHeight / 4);
		BufferedImage blurredLine = ImageHelper.convolveGrayscale(bottomSilhouette, kernel, true, true);

		// Use the content mask to set non-content pixels to zero.
		shadingMask = ImageHelper.maskWithColor(blurredLine, Color.black, contentMask, false);
	}

	private static void drawWhitePolygonFromPoints(BufferedImage image, List<Coordinate> points)
	{
		int[] xPoints = new int[points.size()];
		int[] yPoints = new int[points.size()];
		for (int i : new Range(points.size()))
		{
			Coordinate point = points.get(i);
			xPoints[i] = point.x;
			yPoints[i] = point.y;
		}

		Graphics2D g = image.createGraphics();
		g.setColor(Color.white);
		g.fillPolygon(xPoints, yPoints, xPoints.length);
	}

	private static Coordinate findLowermostOpaquePixelOnMask(BufferedImage contentMask, int x)
	{
		assert ImageHelper.isSupportedGrayscaleType(contentMask);

		Raster maskRaster = contentMask.getRaster();
		for (int y = contentMask.getHeight() - 1; y >= 0; y--)
		{
			if (maskRaster.getSample(x, y, 0) > 0)
			{
				return new Coordinate(x, y);
			}
		}

		return null;
	}

	private static Coordinate findUppermostOpaquePixel(BufferedImage icon, int x)
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

	private static Coordinate findLeftmostOpaquePixel(BufferedImage icon, int y)
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

	private static Coordinate findRightmostOpaquePixel(BufferedImage icon, int y)
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
}
