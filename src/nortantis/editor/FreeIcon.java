package nortantis.editor;

import java.util.Objects;

import nortantis.IconType;
import nortantis.geom.IntDimension;
import nortantis.geom.Point;

public class FreeIcon
{
	public IconType iconType;
	public String iconGroupId;
	/**
	 * When moduloed by the number of icons in a group, this gives an index into the set of icons.
	 */
	public int iconIndex;
	/**
	 * An alternative to using iconIndex.
	 */
	public String iconName;
	/**
	 * Where the center of the icon will be drawn on the map.
	 */
	public Point loc;
	public IntDimension size;
	/**
	 * If this icon is attached to a Center, then this is the Center's index.
	 */
	public Integer centerIndex;
	/**
	 * For icons that add multiple per center (currently only trees), this is the density of the icons.
	 */
	public double density;

	public FreeIcon(Point loc, IconType iconType, String iconGroupId, int iconIndex)
	{
		this.loc = loc;
		this.iconType = iconType;
		this.iconGroupId = iconGroupId;
		this.iconIndex = iconIndex;
	}

	public FreeIcon(Point loc, IconType iconType, String iconGroupId, String iconName)
	{
		this(loc, iconType, iconGroupId, -1);
		this.iconName = iconName;
	}

	public FreeIcon deepCopy()
	{
		FreeIcon copy = new FreeIcon(new Point(loc), iconType, iconGroupId, iconIndex);
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
				&& Objects.equals(iconGroupId, other.iconGroupId) && iconIndex == other.iconIndex
				&& Objects.equals(iconName, other.iconName) && iconType == other.iconType && Objects.equals(loc, other.loc)
				&& Objects.equals(size, other.size);
	}

	

	
}
