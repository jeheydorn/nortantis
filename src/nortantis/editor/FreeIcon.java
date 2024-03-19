package nortantis.editor;

import java.util.Map;
import java.util.Objects;

import nortantis.IconDrawTask;
import nortantis.IconType;
import nortantis.ImageAndMasks;
import nortantis.ImageCache;
import nortantis.geom.Dimension;
import nortantis.geom.IntDimension;
import nortantis.geom.Point;
import nortantis.util.Tuple2;

public class FreeIcon
{
	public IconType type;
	public String groupId;
	/**
	 * When moduloed by the number of icons in a group, this gives an index into the set of icons.
	 */
	public int iconIndex;
	/**
	 * An alternative to using iconIndex.
	 */
	public String iconName;
	/**
	 * Where the center of the icon will be drawn on the map. This is resolution invariant, meaning it is the location at 100% resolution,
	 * before integer truncation.
	 */
	public Point locationResolutionInvariant;
	/**
	 * Resolution invariant size, meaning this is the size of the icon at 100% resolution, before integer truncation.
	 */
	public Dimension sizeResolutionInvariant;
	/**
	 * If this icon is attached to a Center, then this is the Center's index.
	 */
	public Integer centerIndex;
	/**
	 * For icons that add multiple per center (currently only trees), this is the density of the icons.
	 */
	public double density;

	public FreeIcon(double resolutionScale, Point loc, Dimension size, IconType iconType, String iconGroupId, int iconIndex)
	{
		double resolutionInverse = (1.0 / resolutionScale);
		this.locationResolutionInvariant = loc.mult(resolutionInverse);
		this.sizeResolutionInvariant = size.mult(resolutionInverse);
		this.type = iconType;
		this.groupId = iconGroupId;
		this.iconIndex = iconIndex;
	}

	public FreeIcon(double resolutionScale, Point loc, Dimension size, IconType iconType, String iconGroupId, String iconName)
	{
		this(resolutionScale, loc, size, iconType, iconGroupId, -1);
		this.iconName = iconName;
	}

	private FreeIcon()
	{
	}

	/**
	 * Gets the point in the center of the icon, scaled to the resolution of the map.
	 * 
	 * @return
	 */
	public Point getLocationScaled(double resolutionScale)
	{
		return locationResolutionInvariant.mult(resolutionScale);
	}

	public IntDimension getSizeScaled(double resolutionScale, double additionalSizeScale)
	{
		return new IntDimension((int) Math.round(sizeResolutionInvariant.width * resolutionScale * additionalSizeScale),
				(int) Math.round(sizeResolutionInvariant.height * resolutionScale * additionalSizeScale));
	}

	public IconDrawTask toIconDrawTask(String imagesPath, double resolutionScale, double additionalSizeScale)
	{
		if (iconName != null && !iconName.isEmpty())
		{
			Map<String, Tuple2<ImageAndMasks, Integer>> iconsWithWidths = ImageCache.getInstance(imagesPath)
					.getIconsWithWidths(IconType.cities, groupId);
			ImageAndMasks imageAndMasks = iconsWithWidths.get(iconName).getFirst();
			return new IconDrawTask(imageAndMasks, type, getLocationScaled(resolutionScale), getSizeScaled(resolutionScale, additionalSizeScale), iconName);
		}
		else
		{
			ImageAndMasks imageAndMasks = ImageCache.getInstance(imagesPath).getAllIconGroupsAndMasksForType(type).get(groupId)
					.get(iconIndex);
			return new IconDrawTask(imageAndMasks, type, getLocationScaled(resolutionScale), getSizeScaled(resolutionScale, additionalSizeScale));
		}
	}

	public FreeIcon deepCopy()
	{
		FreeIcon copy = new FreeIcon();
		copy.type = type;
		copy.locationResolutionInvariant = locationResolutionInvariant;
		copy.sizeResolutionInvariant = sizeResolutionInvariant;
		copy.iconIndex = iconIndex;
		copy.groupId = groupId;
		copy.iconName = iconName;
		copy.centerIndex = centerIndex;
		copy.density = density;

		return copy;
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
				&& Objects.equals(sizeResolutionInvariant, other.sizeResolutionInvariant) && type == other.type;
	}


}
