package nortantis;

import java.awt.geom.Area;
import java.awt.image.BufferedImage;

import nortantis.geom.Point;
import nortantis.geom.Rectangle;
import nortantis.geom.RotatedRectangle;
import nortantis.util.AssetsPath;

/**
 * Stores things needed to draw an icon onto the map.
 * 
 * @author joseph
 *
 */
public class IconDrawTask implements Comparable<IconDrawTask>
{
	public ImageAndMasks unScaledImageAndMasks;
	public ImageAndMasks scaledImageAndMasks;
	Point centerLoc;
	int scaledWidth;
	int scaledHeight;
	int yBottom;
	boolean ignoreMaxSize;
	/**
	 * A flag to tell which icons could not be drawn because they don't fit in the space they are supposed to be drawn.
	 */
	boolean failedToDraw;
	IconType type;
	String fileName;


	public IconDrawTask(ImageAndMasks unScaledImageAndMasks, IconType type, Point centerLoc, int scaledWidth,
			boolean ignoreMaxSize)
	{
		this(unScaledImageAndMasks, null, type, centerLoc, scaledWidth, ignoreMaxSize, null);
	}
	
	public IconDrawTask(ImageAndMasks unScaledImageAndMasks, IconType type, Point centerLoc, int scaledWidth,
			boolean ignoreMaxSize, String fileName)
	{
		this(unScaledImageAndMasks, null, type, centerLoc, scaledWidth, ignoreMaxSize, fileName);
	}

	public IconDrawTask(ImageAndMasks unScaledImageAndMasks,  ImageAndMasks scaledImageAndMasks, IconType type, Point centerLoc, int scaledWidth,
			boolean ignoreMaxSize, String fileName)
	{
		this.unScaledImageAndMasks = unScaledImageAndMasks;
		this.scaledImageAndMasks = scaledImageAndMasks;
		this.centerLoc = centerLoc;
		this.scaledWidth = scaledWidth;
		this.type = type;

		double aspectRatio = ((double) unScaledImageAndMasks.image.getWidth()) / unScaledImageAndMasks.image.getHeight();
		if (scaledImageAndMasks == null)
		{
			scaledHeight = (int) (scaledWidth / aspectRatio);
		}
		else
		{
			// When the icon doesn't need to be scaled, getting the height directly is more accurate.
			scaledHeight = scaledImageAndMasks.image.getHeight();
		}

		yBottom = (int) (centerLoc.y + (scaledHeight / 2.0));

		this.ignoreMaxSize = ignoreMaxSize;
		this.fileName = fileName;
	}

	public void scaleIcon()
	{
		if (scaledImageAndMasks == null)
		{
			BufferedImage scaledImage = ImageCache.getInstance(AssetsPath.getInstallPath()).getScaledImageByWidth(unScaledImageAndMasks.image,
					scaledWidth);
			// The path passed to ImageCache.getInstance insn't important so long as other calls to getScaledImageByWidth
			// use the same path, since getScaledImageByWidth doesn't load images from disk.

			BufferedImage scaledContentMask = ImageCache.getInstance(AssetsPath.getInstallPath())
					.getScaledImageByWidth(unScaledImageAndMasks.getOrCreateContentMask(), scaledWidth);

			BufferedImage scaledShadingMask = null;
			scaledShadingMask = ImageCache.getInstance(AssetsPath.getInstallPath())
					.getScaledImageByWidth(unScaledImageAndMasks.getOrCreateShadingMask(), scaledWidth);

			java.awt.Rectangle scaledContentBounds = ImageAndMasks.calcScaledContentBounds(unScaledImageAndMasks.getOrCreateContentMask(),
					unScaledImageAndMasks.getOrCreateContentBounds(), scaledWidth, scaledHeight);

			scaledImageAndMasks = new ImageAndMasks(scaledImage, scaledContentMask, scaledContentBounds, scaledShadingMask, type);
		}
	}

	public RotatedRectangle createArea()
	{
		return new RotatedRectangle(new Rectangle(centerLoc.x - scaledWidth / 2.0, centerLoc.y - scaledHeight / 2.0,
				scaledWidth, scaledHeight));
	}

	public nortantis.geom.Rectangle createBounds()
	{
		return new nortantis.geom.Rectangle(centerLoc.x - scaledWidth / 2.0, centerLoc.y - scaledHeight / 2.0, scaledWidth,
				scaledHeight);
	}

	public boolean overlaps(nortantis.geom.Rectangle bounds)
	{
		return createBounds().overlaps(bounds);
	}

	@Override
	public int compareTo(IconDrawTask other)
	{
		return Integer.compare(yBottom, other.yBottom);
	}
}