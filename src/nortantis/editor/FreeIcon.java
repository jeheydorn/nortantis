package nortantis.editor;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import nortantis.IconDrawTask;
import nortantis.IconDrawer;
import nortantis.IconType;
import nortantis.ImageAndMasks;
import nortantis.ImageCache;
import nortantis.geom.IntDimension;
import nortantis.geom.Point;
import nortantis.platform.Color;

public class FreeIcon
{
	public final IconType type;
	public final String artPack;
	public final String groupId;
	/**
	 * When moduloed by the number of icons in a group, this gives an index into the set of icons.
	 */
	public final int iconIndex;
	/**
	 * An alternative to using iconIndex.
	 */
	public final String iconName;
	/**
	 * Where the center of the icon will be drawn on the map. This is resolution invariant, meaning it is the location at 100% resolution,
	 * before integer truncation.
	 */
	public final Point locationResolutionInvariant;
	/**
	 * A factor in determining icon size
	 */
	public final double scale;
	/**
	 * If this icon is attached to a Center, then this is the Center's index.
	 */
	public final Integer centerIndex;

	public final Color color;

	/**
	 * For icons that add multiple per center (currently only trees), this is the density of the icons.
	 */
	public final double density;

	public final double originalScale;

	/**
	 * For creating a new FreeIcon. 
	 * <p>
	 * Note - this constructor must never be used to re-create a FreeIcon with a new scale (to essentially
	 * change the scale) because that would throw off originalScale.
	 * </p>
	 */
	public FreeIcon(double resolutionScale, Point loc, double scale, IconType type, String artPack, String groupId, int iconIndex,
			Integer centerIndex, Color color)
	{
		this(loc.mult((1.0 / resolutionScale)), scale, type, artPack, groupId, iconIndex, null, centerIndex, 0.0, color, scale);
	}

	/**
	 * For creating a new FreeIcon. 
	 * <p>
	 * Note - this constructor must never be used to re-create a FreeIcon with a new scale (to essentially
	 * change the scale) because that would throw off originalScale.
	 * </p>
	 * @param scale
	 *            Scale before applying resolutionScale or icon-type level scaling.
	 * @param artPack
	 *            The art pack the image is from.
	 */
	public FreeIcon(double resolutionScale, Point loc, double scale, IconType type, String artPack, String groupId, int iconIndex,
			Integer centerIndex, double density, Color color)
	{
		this(loc.mult((1.0 / resolutionScale)), scale, type, artPack, groupId, iconIndex, null, centerIndex, density, color, scale);
	}

	/**
	 * For creating a new FreeIcon. 
	 * <p>
	 * Note - this constructor must never be used to re-create a FreeIcon with a new scale (to essentially
	 * change the scale) because that would throw off originalScale.
	 * </p>
	 * @param scale
	 *            Scale before applying resolutionScale or icon-type level scaling.
	 */
	public FreeIcon(double resolutionScale, Point loc, double scale, IconType type, String artPack, String groupId, String iconName,
			Integer centerIndex, Color color)
	{
		this(loc.mult((1.0 / resolutionScale)), scale, type, artPack, groupId, -1, iconName, centerIndex, 0.0, color, scale);
	}

	/**
	 * For creating a new FreeIcon. 
	 * <p>
	 * Note - this constructor must never be used to re-create a FreeIcon with a new scale (to essentially
	 * change the scale) because that would throw off originalScale.
	 * </p>
	 */
	public FreeIcon(Point locationResolutionInvariant, double scale, IconType type, String artPack, String groupId, int iconIndex,
			String iconName, Integer centerIndex, double density, Color color, double originalScale)
	{
		this.type = type;
		this.locationResolutionInvariant = locationResolutionInvariant;
		this.scale = scale;
		this.artPack = artPack;
		assert !StringUtils.isEmpty(artPack);
		this.groupId = groupId;
		this.iconIndex = iconIndex;
		this.iconName = iconName;
		this.density = density;
		this.centerIndex = centerIndex;
		this.color = color;
		this.originalScale = originalScale;
	}

	public FreeIcon copyWithGroupId(String groupId)
	{
		return new FreeIcon(locationResolutionInvariant, scale, type, artPack, groupId, iconIndex, iconName, centerIndex, density, color,
				originalScale);
	}

	public FreeIcon copyWithName(String iconName)
	{
		return new FreeIcon(locationResolutionInvariant, scale, type, artPack, groupId, iconIndex, iconName, centerIndex, density, color,
				originalScale);
	}

	public FreeIcon copyWith(String artPack, String groupId, String iconName, Color color)
	{
		return new FreeIcon(locationResolutionInvariant, scale, type, artPack, groupId, iconIndex, iconName, centerIndex, density, color,
				originalScale);
	}

	public FreeIcon copyWith(String artPack, String groupId, int iconIndex, Color color)
	{
		return new FreeIcon(locationResolutionInvariant, scale, type, artPack, groupId, iconIndex, iconName, centerIndex, density, color,
				originalScale);
	}

	public FreeIcon copyWithArtPack(String artPack)
	{
		return new FreeIcon(locationResolutionInvariant, scale, type, artPack, groupId, iconIndex, iconName, centerIndex, density, color,
				originalScale);
	}

	public FreeIcon copyWithScale(double scale)
	{
		return new FreeIcon(locationResolutionInvariant, scale, type, artPack, groupId, iconIndex, iconName, centerIndex, density, color,
				originalScale);
	}

	public FreeIcon copyWithLocation(double resolutionScale, Point loc)
	{
		return new FreeIcon(loc.mult((1.0 / resolutionScale)), scale, type, artPack, groupId, iconIndex, iconName, centerIndex, density,
				color, originalScale);
	}

	public FreeIcon copyWithLocationResolutionInvariant(Point locationResolutionInvariant)
	{
		return new FreeIcon(locationResolutionInvariant, scale, type, artPack, groupId, iconIndex, iconName, centerIndex, density, color,
				originalScale);
	}

	public FreeIcon copyWithColor(Color color)
	{
		return new FreeIcon(locationResolutionInvariant, scale, type, artPack, groupId, iconIndex, iconName, centerIndex, density, color,
				originalScale);
	}


	public FreeIcon copyUnanchored()
	{
		return new FreeIcon(locationResolutionInvariant, scale, type, artPack, groupId, iconIndex, iconName, null, density, color,
				originalScale);
	}

	/**
	 * Gets the point in the center of the icon, scaled to the resolution of the map.
	 * 
	 * @return
	 */
	public Point getScaledLocation(double resolutionScale)
	{
		return locationResolutionInvariant.mult(resolutionScale);
	}

	/**
	 * Converts a free icon to an icon draw task.
	 * 
	 * @param imagesPath
	 *            Either no or empty, or a custom images folder
	 * @param resolutionScale
	 *            MapSettings.resolution that we're currently drawing at.
	 * @param typeLevelScale
	 *            The scaling from the sliders that scale all icons of a type.
	 * @param baseWidth
	 *            The width of the icon before type-level scaling. Should already be adjusted for resolution.
	 * @return a new IconDrawTask.
	 */
	public IconDrawTask toIconDrawTask(String customImagesFolder, double resolutionScale, double typeLevelScale, double baseWidth)
	{
		if (iconName != null && !iconName.isEmpty())
		{
			Map<String, ImageAndMasks> iconsWithWidths = ImageCache.getInstance(artPack, customImagesFolder).getIconsByNameForGroup(type,
					groupId);
			if (iconsWithWidths == null || iconsWithWidths.isEmpty())
			{
				return null;
			}
			if (!iconsWithWidths.containsKey(iconName) || iconsWithWidths.get(iconName) == null)
			{
				return null;
			}
			ImageAndMasks imageAndMasks = iconsWithWidths.get(iconName);
			IntDimension drawSize = IconDrawer
					.getDimensionsWhenScaledByWidth(imageAndMasks.image.size(), Math.round(typeLevelScale * scale * baseWidth))
					.toIntDimension();
			return new IconDrawTask(imageAndMasks, type, getScaledLocation(resolutionScale), drawSize, iconName, color);
		}
		else
		{
			List<ImageAndMasks> groupImages = ImageCache.getInstance(artPack, customImagesFolder).getIconsInGroup(type, groupId);
			if (groupImages == null || groupImages.isEmpty())
			{
				return null;
			}
			ImageAndMasks imageAndMasks = groupImages.get(iconIndex % groupImages.size());
			IntDimension drawSize = IconDrawer
					.getDimensionsWhenScaledByWidth(imageAndMasks.image.size(), typeLevelScale * scale * baseWidth).roundToIntDimension();
			return new IconDrawTask(imageAndMasks, type, getScaledLocation(resolutionScale), drawSize, color);
		}
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(artPack, centerIndex, color, density, groupId, iconIndex, iconName, locationResolutionInvariant, scale, type);
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		if (obj == null)
		{
			return false;
		}
		if (getClass() != obj.getClass())
		{
			return false;
		}
		FreeIcon other = (FreeIcon) obj;
		return Objects.equals(artPack, other.artPack) && Objects.equals(centerIndex, other.centerIndex)
				&& Objects.equals(color, other.color) && Double.doubleToLongBits(density) == Double.doubleToLongBits(other.density)
				&& Objects.equals(groupId, other.groupId) && iconIndex == other.iconIndex && Objects.equals(iconName, other.iconName)
				&& Objects.equals(locationResolutionInvariant, other.locationResolutionInvariant)
				&& Double.doubleToLongBits(scale) == Double.doubleToLongBits(other.scale) && type == other.type;
	}

	@Override
	public String toString()
	{
		return "FreeIcon [type=" + type + ", artPack=" + artPack + ", groupId=" + groupId + ", iconIndex=" + iconIndex + ", iconName="
				+ iconName + ", locationResolutionInvariant=" + locationResolutionInvariant + ", scale=" + scale + ", centerIndex="
				+ centerIndex + ", color=" + color + ", density=" + density + "]";
	}
}
