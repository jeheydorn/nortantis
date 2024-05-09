package nortantis.editor;

import java.util.Objects;

/**
 * Stores which icon, if any, to draw for a center.
 */
public class CenterIcon
{
	public final CenterIconType iconType;
	public final String iconGroupId;
	/**
	 * When moduloed by the number of icons in a group, this gives an index into the set of icons.
	 */
	public final int iconIndex;
	/**
	 * An alternative to using iconIndex.
	 */
	public final String iconName;

	public CenterIcon(CenterIconType iconType, String iconGroupId, int iconIndex)
	{
		this(iconType, iconGroupId, iconIndex, null);
	}

	public CenterIcon(CenterIconType iconType, String iconGroupId, String iconName)
	{
		this(iconType, iconGroupId, -1, iconName);
	}

	private CenterIcon(CenterIconType iconType, String iconGroupId, int iconIndex, String iconName)
	{
		this.iconType = iconType;
		this.iconGroupId = iconGroupId;
		this.iconIndex = iconIndex;
		this.iconName = iconName;
	}
	
	public CenterIcon copyWithIconGroupId(String iconGroupId)
	{
		return new CenterIcon(iconType, iconGroupId, iconIndex, iconName);
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CenterIcon other = (CenterIcon) obj;
		return Objects.equals(iconGroupId, other.iconGroupId) && iconIndex == other.iconIndex && Objects.equals(iconName, other.iconName)
				&& iconType == other.iconType;
	}
}
