package nortantis;

import nortantis.geom.IntDimension;
import nortantis.geom.IntRectangle;
import nortantis.geom.Point;
import nortantis.geom.Rectangle;
import nortantis.geom.RotatedRectangle;
import nortantis.platform.Image;
import nortantis.util.Assets;

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
	IntDimension scaledSize;
	int yBottom;
	/**
	 * A flag to tell which icons could not be drawn because they don't fit in the space they are supposed to be drawn.
	 */
	boolean failedToDraw;
	IconType type;
	String fileName;

	public IconDrawTask(ImageAndMasks unScaledImageAndMasks, IconType type, Point centerLoc, IntDimension scaledSize)
	{
		this(unScaledImageAndMasks, null, type, centerLoc, scaledSize, null);
	}

	public IconDrawTask(ImageAndMasks unScaledImageAndMasks, IconType type, Point centerLoc, IntDimension scaledSize, String fileName)
	{
		this(unScaledImageAndMasks, null, type, centerLoc, scaledSize, fileName);
	}

	private IconDrawTask(ImageAndMasks unScaledImageAndMasks, ImageAndMasks scaledImageAndMasks, IconType type, Point centerLoc,
			IntDimension scaledSize, String fileName)
	{
		this.unScaledImageAndMasks = unScaledImageAndMasks;
		this.scaledImageAndMasks = scaledImageAndMasks;
		this.centerLoc = centerLoc;
		this.scaledSize = scaledSize;
		this.type = type;

		yBottom = (int) (centerLoc.y + (scaledSize.height / 2.0));

		this.fileName = fileName;
	}

	public void scaleIcon()
	{
		if (scaledImageAndMasks == null)
		{
			// The path passed to ImageCache.getInstance isn't important so long as other calls to getScaledImageByWidth
			// use the same path, since getScaledImage doesn't load images from disk.
			Image scaledImage = ImageCache.getInstance(Assets.installedArtPack, null).getScaledImage(unScaledImageAndMasks.image,
					scaledSize);

			Image scaledContentMask = ImageCache.getInstance(Assets.installedArtPack, null)
					.getScaledImage(unScaledImageAndMasks.getOrCreateContentMask(), scaledSize);

			Image scaledShadingMask = null;
			scaledShadingMask = ImageCache.getInstance(Assets.installedArtPack, null)
					.getScaledImage(unScaledImageAndMasks.getOrCreateShadingMask(), scaledSize);

			IntRectangle scaledContentBounds = ImageAndMasks.calcScaledContentBounds(unScaledImageAndMasks.getOrCreateContentMask(),
					unScaledImageAndMasks.getOrCreateContentBounds(), scaledSize.width, scaledSize.height);

			scaledImageAndMasks = new ImageAndMasks(scaledImage, scaledContentMask, scaledContentBounds, scaledShadingMask, type,
					unScaledImageAndMasks.widthFromFileName);
		}
	}

	public RotatedRectangle createArea()
	{
		return new RotatedRectangle(new Rectangle(centerLoc.x - scaledSize.width / 2.0, centerLoc.y - scaledSize.height / 2.0,
				scaledSize.width, scaledSize.height));
	}

	public nortantis.geom.Rectangle createBounds()
	{
		return new nortantis.geom.Rectangle(centerLoc.x - scaledSize.width / 2.0, centerLoc.y - scaledSize.height / 2.0, scaledSize.width,
				scaledSize.height);
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