package nortantis;

import nortantis.geom.IntDimension;
import nortantis.geom.IntRectangle;
import nortantis.geom.Point;
import nortantis.geom.Rectangle;
import nortantis.geom.RotatedRectangle;
import nortantis.platform.Color;
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
	final IconType type;
	final String groupId;
	final String fileName;
	final Color color;
	final HSBColor filterColor;
	boolean maximizeOpacity;
	final double resolutionScale;

	public IconDrawTask(ImageAndMasks unScaledImageAndMasks, IconType type, Point centerLoc, IntDimension scaledSize, Color color,
			HSBColor filterColor, boolean maximizeOpacity, String groupId, double resolutionScale)
	{
		this(unScaledImageAndMasks, null, type, centerLoc, scaledSize, null, color, filterColor, maximizeOpacity, groupId, resolutionScale);
	}

	public IconDrawTask(ImageAndMasks unScaledImageAndMasks, IconType type, Point centerLoc, IntDimension scaledSize, String fileName,
			Color color, HSBColor filterColor, boolean maximizeOpacity, String groupId, double resolutionScale)
	{
		this(unScaledImageAndMasks, null, type, centerLoc, scaledSize, fileName, color, filterColor, maximizeOpacity, groupId, resolutionScale);
	}

	private IconDrawTask(ImageAndMasks unScaledImageAndMasks, ImageAndMasks scaledImageAndMasks, IconType type, Point centerLoc,
			IntDimension scaledSize, String fileName, Color color, HSBColor filterColor, boolean maximizeOpacity, String groupId, double resolutionScale)
	{
		this.unScaledImageAndMasks = unScaledImageAndMasks;
		this.scaledImageAndMasks = scaledImageAndMasks;
		this.centerLoc = centerLoc;
		this.scaledSize = scaledSize;
		this.type = type;

		yBottom = (int) (centerLoc.y + (scaledSize.height / 2.0));

		this.fileName = fileName;
		this.color = color;
		this.filterColor = filterColor;
		this.maximizeOpacity = maximizeOpacity;
		this.groupId = groupId;
		this.resolutionScale = resolutionScale;
	}

	public void colorAndScaleIcon()
	{
		if (scaledImageAndMasks == null)
		{
			Image coloredIcon;
			if (MapSettings.defaultIconColor.equals(color) && filterColor.equals(MapSettings.defaultIconFilterColor) && !maximizeOpacity)
			{
				// Do nothing since the color is transparent.
				coloredIcon = unScaledImageAndMasks.image;
			}
			else
			{
				coloredIcon = ImageCache.getInstance(Assets.installedArtPack, null).getColoredIcon(unScaledImageAndMasks, color,
						filterColor, maximizeOpacity);
			}

			// The path passed to ImageCache.getInstance isn't important so long as other calls to getScaledImageByWidth
			// use the same path, since getScaledImage doesn't load images from disk.
			Image scaledImage = ImageCache.getInstance(Assets.installedArtPack, null).getScaledImage(coloredIcon, scaledSize);

			Image scaledContentMask = ImageCache.getInstance(Assets.installedArtPack, null)
					.getScaledImage(unScaledImageAndMasks.getOrCreateContentMask(), scaledSize);

			Image scaledShadingMask = null;
			scaledShadingMask = ImageCache.getInstance(Assets.installedArtPack, null)
					.getScaledImage(unScaledImageAndMasks.getOrCreateShadingMask(), scaledSize);

			IntRectangle scaledContentBounds = ImageAndMasks.calcScaledContentBounds(unScaledImageAndMasks.getOrCreateContentMask(),
					unScaledImageAndMasks.getOrCreateContentBounds(), scaledSize.width, scaledSize.height);

			scaledImageAndMasks = new ImageAndMasks(scaledImage, scaledContentMask, scaledContentBounds, scaledShadingMask, type,
					unScaledImageAndMasks.widthFromFileName, unScaledImageAndMasks.artPack, unScaledImageAndMasks.groupId,
					unScaledImageAndMasks.fileNameWithoutParametersOrExtension);
		}
	}

	public RotatedRectangle createArea()
	{
		return new RotatedRectangle(new Rectangle(centerLoc.x - scaledSize.width / 2.0, centerLoc.y - scaledSize.height / 2.0,
				scaledSize.width, scaledSize.height));
	}

	/**
	 * @return The bounds of the image on the map (I think without respect to the map border).
	 */
	public Rectangle createBounds()
	{
		return new nortantis.geom.Rectangle(centerLoc.x - scaledSize.width / 2.0, centerLoc.y - scaledSize.height / 2.0, scaledSize.width,
				scaledSize.height);
	}

	private Rectangle contentBoundsPadded;

	public Rectangle getOrCreateContentBoundsPadded()
	{
		if (contentBoundsPadded == null)
		{
			Rectangle bounds = createBounds();
			final double padding = 2 * resolutionScale;
			Rectangle scaledContentBounds = calcScaledContentBounds();
			contentBoundsPadded = new nortantis.geom.Rectangle(bounds.x + scaledContentBounds.x,
					bounds.y + scaledContentBounds.y, scaledContentBounds.width, scaledContentBounds.height).pad(padding, padding);
		}
		return contentBoundsPadded;
	}
	
	private Rectangle calcScaledContentBounds()
	{
		Image originalContentMask = unScaledImageAndMasks.getOrCreateContentMask();
		IntRectangle originalContentBounds = unScaledImageAndMasks.getOrCreateContentBounds();
		final double xScale = (((double) scaledSize.width / originalContentMask.getWidth()));
		final double yScale = (((double) scaledSize.height / originalContentMask.getHeight()));

		Rectangle scaledContentBounds = new Rectangle(originalContentBounds.x * xScale, originalContentBounds.y * yScale,
				originalContentBounds.width * xScale, originalContentBounds.height * yScale);
		return scaledContentBounds;
	}


	public boolean overlaps(nortantis.geom.Rectangle bounds)
	{
		return createBounds().overlaps(bounds);
	}

	@Override
	public int compareTo(IconDrawTask other)
	{
		return Double.compare(getOrCreateContentBoundsPadded().getBottom(), other.getOrCreateContentBoundsPadded().getBottom());
	}
}