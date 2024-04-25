package nortantis.editor;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.NotImplementedException;

import nortantis.IconDrawTask;
import nortantis.IconDrawer;
import nortantis.IconType;
import nortantis.ImageAndMasks;
import nortantis.ImageCache;
import nortantis.geom.IntDimension;
import nortantis.geom.Point;
import nortantis.util.Tuple2;

public class FreeIcon
{
	public final IconType type;
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
	
	/**
	 * For icons that add multiple per center (currently only trees), this is the density of the icons.
	 */
	public final double density;

	public FreeIcon(double resolutionScale, Point loc, double scale, IconType type, String groupId, int iconIndex)
	{
		this(loc.mult((1.0 / resolutionScale)), scale, type, groupId, iconIndex, null, null, 0.0);
	}

	public FreeIcon(double resolutionScale, Point loc, double scale, IconType type, String groupId, int iconIndex, Integer centerIndex)
	{
		this(loc.mult((1.0 / resolutionScale)), scale, type, groupId, iconIndex, null, centerIndex, 0.0);
	}
	
	/**
	 * @param scale
	 *            Scale before applying resolutionScale or icon-type level scaling.
	 */
	public FreeIcon(double resolutionScale, Point loc, double scale, IconType type, String groupId, int iconIndex, Integer centerIndex, double density)
	{
		this(loc.mult((1.0 / resolutionScale)), scale, type, groupId, iconIndex, null, centerIndex, density);
	}
	
	/**
	 * @param scale
	 *            Scale before applying resolutionScale or icon-type level scaling.
	 */
	public FreeIcon(double resolutionScale, Point loc, double scale, IconType type, String groupId, String iconName, Integer centerIndex)
	{
		this(loc.mult((1.0 / resolutionScale)), scale, type, groupId, -1, iconName, centerIndex, 0.0);
	}
	
	public FreeIcon(Point locationResolutionInvariant, double scale, IconType type, String groupId, int iconIndex, String iconName, Integer centerIndex, double density)
	{
		this.type = type;
		this.locationResolutionInvariant = locationResolutionInvariant;
		this.scale = scale;
		this.groupId = groupId;
		this.iconIndex = iconIndex;
		this.iconName = iconName;
		this.density = density;
		this.centerIndex = centerIndex;
	}

	public FreeIcon copyWith(String groupId)
	{
		return new FreeIcon(locationResolutionInvariant, scale, type, groupId, iconIndex, iconName, centerIndex, density);
	}

	public FreeIcon copyWith(String groupId, String iconName)
	{
		return new FreeIcon(locationResolutionInvariant, scale, type, groupId, iconIndex, iconName, centerIndex, density);
	}

	public FreeIcon copyWith(String groupId, int iconIndex)
	{
		return new FreeIcon(locationResolutionInvariant, scale, type, groupId, iconIndex, iconName, centerIndex, density);
	}

	public FreeIcon copyWithScale(double scale)
	{
		return new FreeIcon(locationResolutionInvariant, scale, type, groupId, iconIndex, iconName, centerIndex, density);
	}

	public FreeIcon copyWithLocation(double resolutionScale, Point loc)
	{
		return new FreeIcon(loc.mult((1.0 / resolutionScale)), scale, type, groupId, iconIndex, iconName, centerIndex, density);
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
	 * @param baseWidthOrHeight
	 *            The width or height (which is used depends on the type of icon) of the icon before type-level scaling. Should already be
	 *            adjusted for resolution..
	 * @return a new IconDrawTask.
	 */
	public IconDrawTask toIconDrawTask(String imagesPath, double resolutionScale, double typeLevelScale, double baseWidthOrHeight)
	{
		if (isScaledByWidthRatherThanHeight())
		{
			return toIconDrawTaskUsingWidth(imagesPath, resolutionScale, typeLevelScale, baseWidthOrHeight);
		}
		else
		{
			return toIconDrawTaskUsingHeight(imagesPath, resolutionScale, typeLevelScale, baseWidthOrHeight);
		}
	}

	private boolean isScaledByWidthRatherThanHeight()
	{
		return type != IconType.trees;
	}

	private IconDrawTask toIconDrawTaskUsingHeight(String imagesPath, double resolutionScale, double typeLevelScale, double baseHeight)
	{
		if (iconName != null && !iconName.isEmpty())
		{
			throw new NotImplementedException("Named icon drawing by height is not implemented since it's only used for trees.");
		}
		else
		{
			List<ImageAndMasks> groupImages = ImageCache.getInstance(imagesPath).getAllIconGroupsAndMasksForType(type).get(groupId);
			ImageAndMasks imageAndMasks = groupImages.get(iconIndex % groupImages.size());
			IntDimension drawSize = IconDrawer
					.getDimensionsWhenScaledByHeight(imageAndMasks.image.size(), typeLevelScale * scale * baseHeight)
					.toIntDimension();
			  return new IconDrawTask(imageAndMasks, type, getScaledLocation(resolutionScale), drawSize);
		}
	}

	private IconDrawTask toIconDrawTaskUsingWidth(String imagesPath, double resolutionScale, double typeLevelScale, double baseWidth)
	{
		if (iconName != null && !iconName.isEmpty())
		{
			Map<String, Tuple2<ImageAndMasks, Integer>> iconsWithWidths = ImageCache.getInstance(imagesPath)
					.getIconsWithWidths(IconType.cities, groupId);
			ImageAndMasks imageAndMasks = iconsWithWidths.get(iconName).getFirst();
			IntDimension drawSize = IconDrawer
					.getDimensionsWhenScaledByWidth(imageAndMasks.image.size(), typeLevelScale * scale * baseWidth).toIntDimension();
			return new IconDrawTask(imageAndMasks, type, getScaledLocation(resolutionScale), drawSize, iconName);
		}
		else
		{
			List<ImageAndMasks> groupImages = ImageCache.getInstance(imagesPath).getAllIconGroupsAndMasksForType(type).get(groupId);
			ImageAndMasks imageAndMasks = groupImages.get(iconIndex % groupImages.size());
			IntDimension drawSize = IconDrawer
					.getDimensionsWhenScaledByWidth(imageAndMasks.image.size(), typeLevelScale * scale * baseWidth).toIntDimension();
			return new IconDrawTask(imageAndMasks, type, getScaledLocation(resolutionScale), drawSize);
		}
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(centerIndex, density, groupId, iconIndex, iconName, locationResolutionInvariant, scale, type);
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
		return Objects.equals(centerIndex, other.centerIndex) && Double.doubleToLongBits(density) == Double.doubleToLongBits(other.density)
				&& Objects.equals(groupId, other.groupId) && iconIndex == other.iconIndex && Objects.equals(iconName, other.iconName)
				&& Objects.equals(locationResolutionInvariant, other.locationResolutionInvariant)
				&& Double.doubleToLongBits(scale) == Double.doubleToLongBits(other.scale) && type == other.type;
	}

	@Override
	public String toString()
	{
		return "FreeIcon [type=" + type + ", groupId=" + groupId + ", iconIndex=" + iconIndex + ", iconName=" + iconName
				+ ", locationResolutionInvariant=" + locationResolutionInvariant + ", scale=" + scale + ", centerIndex=" + centerIndex
				+ ", density=" + density + "]";
	}

}
