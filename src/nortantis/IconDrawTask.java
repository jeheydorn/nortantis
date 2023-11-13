package nortantis;

import java.awt.geom.Area;
import java.awt.image.BufferedImage;

import nortantis.graph.geom.Point;
import nortantis.util.AssetsPath;

/**
 * Stores things needed to draw an icon onto the map.
 * 
 * @author joseph
 *
 */
public class IconDrawTask implements Comparable<IconDrawTask>
{
	public ImageAndMasks imageAndMasks;
	Point centerLoc;
	int scaledWidth;
	int scaledHeight;
	int yBottom;
	boolean needsScale;
	boolean ignoreMaxSize;
	String fileName;
	/**
	 * A flag to tell which icons could not be drawn because they don't fit in the space they are supposed to be drawn.
	 */
	boolean failedToDraw;
	IconType type;

	public IconDrawTask(ImageAndMasks imageAndMasks, IconType type, Point centerLoc, int scaledWidth, boolean needsScale,
			boolean ignoreMaxSize)
	{
		this(imageAndMasks, type, centerLoc, scaledWidth, needsScale, ignoreMaxSize, "");
	}

	public IconDrawTask(ImageAndMasks imageAndMasks, IconType type, Point centerLoc, int scaledWidth, boolean needsScale,
			boolean ignoreMaxSize, String fileName)
	{
		this.imageAndMasks = imageAndMasks;
		this.centerLoc = centerLoc;
		this.scaledWidth = scaledWidth;
		this.needsScale = needsScale;
		this.type = type;

		double aspectRatio = ((double) imageAndMasks.image.getWidth()) / imageAndMasks.image.getHeight();
		if (needsScale)
		{
			scaledHeight = (int) (scaledWidth / aspectRatio);
		}
		else
		{
			// When the icon doesn't need to be scaled, getting the height directly is more accurate.
			scaledHeight = imageAndMasks.image.getHeight();
		}

		yBottom = (int) (centerLoc.y + (scaledHeight / 2.0));

		this.ignoreMaxSize = ignoreMaxSize;
		this.fileName = fileName;
	}

	public void scaleIcon(boolean needsShadingMask)
	{
		if (needsScale)
		{
			BufferedImage scaledImage = ImageCache.getInstance(AssetsPath.getInstallPath()).getScaledImageByWidth(imageAndMasks.image,
					scaledWidth);
			// The path passed to ImageCache.getInstance insn't important so long as other calls to getScaledImageByWidth
			// use the same path, since getScaledImageByWidth doesn't load images from disk.

			BufferedImage scaledContentMask = ImageCache.getInstance(AssetsPath.getInstallPath())
					.getScaledImageByWidth(imageAndMasks.getOrCreateContentMask(), scaledWidth);

			BufferedImage scaledShadingMask = null;
			if (needsShadingMask)
			{
				scaledShadingMask = ImageCache.getInstance(AssetsPath.getInstallPath())
						.getScaledImageByWidth(imageAndMasks.getOrCreateShadingMask(), scaledWidth);
			}
			imageAndMasks = new ImageAndMasks(scaledImage, scaledContentMask, scaledShadingMask);
		}
	}

	public Area createArea()
	{
		return new Area(new java.awt.Rectangle((int) (centerLoc.x - scaledWidth / 2.0), (int) (centerLoc.y - scaledHeight / 2.0),
				scaledWidth, scaledHeight));
	}

	public nortantis.graph.geom.Rectangle createBounds()
	{
		return new nortantis.graph.geom.Rectangle(centerLoc.x - scaledWidth / 2.0, centerLoc.y - scaledHeight / 2.0, scaledWidth,
				scaledHeight);
	}

	public boolean overlaps(nortantis.graph.geom.Rectangle bounds)
	{
		return createBounds().overlaps(bounds);
	}

	@Override
	public int compareTo(IconDrawTask other)
	{
		return Integer.compare(yBottom, other.yBottom);
	}
}