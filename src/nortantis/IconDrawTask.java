package nortantis;

import nortantis.geom.*;
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
	/**
	 * The icon's scaled draw size before it was rounded to whole pixels (scaledSize is the rounded version actually used to draw). Because
	 * the rounding amount changes with resolution, the water-touch check uses this un-rounded size so its sample positions vary smoothly
	 * with resolution instead of wobbling by up to half a pixel - which could otherwise flip the check near a coast and make an icon (e.g. a
	 * city) appear or disappear when the display quality changes.
	 */
	Dimension unroundedScaledSize;

	final IconType type;
	final String fileName;
	final Color fillColor;
	final HSBColor filterColor;
	boolean maximizeOpacity;
	final boolean fillWithColor;
	final boolean fillShowsTexture;
	final double resolutionScale;
	/**
	 * Where the scaled icon is filled, from 0 to 255, when {@link #drawsFillFromTexture()}. The fill is then left out of the scaled image
	 * and drawn from the background texture under the icon instead.
	 */
	Image scaledFillMask;

	public IconDrawTask(ImageAndMasks unScaledImageAndMasks, IconType type, Point centerLoc, IntDimension scaledSize, Dimension unroundedScaledSize, Color fillColor, HSBColor filterColor,
			boolean maximizeOpacity, boolean fillWithColor, boolean fillShowsTexture, double resolutionScale)
	{
		this(unScaledImageAndMasks, null, type, centerLoc, scaledSize, unroundedScaledSize, null, fillColor, filterColor, maximizeOpacity, fillWithColor, fillShowsTexture,
				resolutionScale);
	}

	public IconDrawTask(ImageAndMasks unScaledImageAndMasks, IconType type, Point centerLoc, IntDimension scaledSize, Dimension unroundedScaledSize, String fileName, Color fillColor,
			HSBColor filterColor, boolean maximizeOpacity, boolean fillWithColor, boolean fillShowsTexture, double resolutionScale)
	{
		this(unScaledImageAndMasks, null, type, centerLoc, scaledSize, unroundedScaledSize, fileName, fillColor, filterColor, maximizeOpacity, fillWithColor, fillShowsTexture,
				resolutionScale);
	}

	private IconDrawTask(ImageAndMasks unScaledImageAndMasks, ImageAndMasks scaledImageAndMasks, IconType type, Point centerLoc, IntDimension scaledSize, Dimension unroundedScaledSize, String fileName,
			Color fillColor, HSBColor filterColor, boolean maximizeOpacity, boolean fillWithColor, boolean fillShowsTexture, double resolutionScale)
	{
		this.unScaledImageAndMasks = unScaledImageAndMasks;
		this.scaledImageAndMasks = scaledImageAndMasks;
		this.centerLoc = centerLoc;
		this.scaledSize = scaledSize;
		this.unroundedScaledSize = unroundedScaledSize;
		this.type = type;

		this.fileName = fileName;
		this.fillColor = fillColor;
		this.filterColor = filterColor;
		this.maximizeOpacity = maximizeOpacity;
		this.fillWithColor = fillWithColor;
		this.fillShowsTexture = fillShowsTexture;
		this.resolutionScale = resolutionScale;
	}

	/**
	 * Whether the icon's fill is drawn from the background texture under it, rather than being part of its colored image.
	 */
	boolean drawsFillFromTexture()
	{
		return fillWithColor && fillShowsTexture && fillColor.getAlpha() > 0;
	}

	public void colorAndScaleIcon()
	{
		if (scaledImageAndMasks == null)
		{
			Image coloredIcon;
			boolean isFillInImage = fillWithColor && !fillShowsTexture;
			if ((!isFillInImage || Color.transparentBlack.equals(fillColor)) && filterColor.equals(MapSettings.defaultIconFilterColor) && !maximizeOpacity)
			{
				// Do nothing since the color is transparent.
				coloredIcon = unScaledImageAndMasks.image;
			}
			else
			{
				coloredIcon = ImageCache.getInstance(Assets.installedArtPack, null).getColoredIcon(unScaledImageAndMasks, fillColor, filterColor, maximizeOpacity, isFillInImage);
			}

			// The path passed to ImageCache.getInstance isn't important so long as other calls to getScaledImageByWidth
			// use the same path, since getScaledImage doesn't load images from disk.
			Image scaledImage = ImageCache.getInstance(Assets.installedArtPack, null).getScaledImage(coloredIcon, scaledSize);

			Image scaledContentMask = ImageCache.getInstance(Assets.installedArtPack, null).getScaledImage(unScaledImageAndMasks.getOrCreateContentMask(), scaledSize);

			Image scaledShadingMask = null;
			scaledShadingMask = ImageCache.getInstance(Assets.installedArtPack, null).getScaledImage(unScaledImageAndMasks.getOrCreateShadingMask(), scaledSize);

			if (drawsFillFromTexture())
			{
				scaledFillMask = ImageCache.getInstance(Assets.installedArtPack, null).getScaledImage(unScaledImageAndMasks.getOrCreateColorMask(), scaledSize);
			}

			IntRectangle scaledContentBounds = ImageAndMasks.calcScaledContentBounds(unScaledImageAndMasks.getOrCreateContentMask(), unScaledImageAndMasks.getOrCreateContentBounds(), scaledSize.width,
					scaledSize.height);

			scaledImageAndMasks = new ImageAndMasks(scaledImage, scaledContentMask, scaledContentBounds, scaledShadingMask, type, unScaledImageAndMasks.widthFromFileName,
					unScaledImageAndMasks.artPack, unScaledImageAndMasks.groupId, unScaledImageAndMasks.fileNameWithoutParametersOrExtension);
		}
	}

	public IconType getType()
	{
		return type;
	}

	public double getCenterLocX()
	{
		return centerLoc.x;
	}

	public double getCenterLocY()
	{
		return centerLoc.y;
	}

	public RotatedRectangle createArea()
	{
		return new RotatedRectangle(new Rectangle(centerLoc.x - scaledSize.width / 2.0, centerLoc.y - scaledSize.height / 2.0, scaledSize.width, scaledSize.height));
	}

	/**
	 * @return The bounds of the image on the map (I think without respect to the map border).
	 */
	public Rectangle createBounds()
	{
		return new nortantis.geom.Rectangle(centerLoc.x - scaledSize.width / 2.0, centerLoc.y - scaledSize.height / 2.0, scaledSize.width, scaledSize.height);
	}

	private Rectangle contentBoundsPadded;

	public Rectangle getOrCreateContentBoundsPadded()
	{
		if (contentBoundsPadded == null)
		{
			Rectangle bounds = createBounds();
			final double padding = 2 * resolutionScale;
			Rectangle scaledContentBounds = calcScaledContentBounds();
			contentBoundsPadded = new nortantis.geom.Rectangle(bounds.x + scaledContentBounds.x, bounds.y + scaledContentBounds.y, scaledContentBounds.width, scaledContentBounds.height).pad(padding,
					padding);
		}
		return contentBoundsPadded;
	}

	private Rectangle calcScaledContentBounds()
	{
		Image originalContentMask = unScaledImageAndMasks.getOrCreateContentMask();
		IntRectangle originalContentBounds = unScaledImageAndMasks.getOrCreateContentBounds();
		final double xScale = (((double) scaledSize.width / originalContentMask.getWidth()));
		final double yScale = (((double) scaledSize.height / originalContentMask.getHeight()));

		Rectangle scaledContentBounds = new Rectangle(originalContentBounds.x * xScale, originalContentBounds.y * yScale, originalContentBounds.width * xScale, originalContentBounds.height * yScale);
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
