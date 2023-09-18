package nortantis.editor;

import java.io.Serializable;
import java.util.Objects;

/**
 * Stores which icon, if any, to draw for a center.
 */
@SuppressWarnings("serial")
public class CenterIcon implements Serializable
{
	public CenterIconType iconType;
	public String iconGroupId;
	/**
	 * When moduloed by the number of icons in a group, this gives an index into the set of icons.
	 */
	public int iconIndex;
	/**
	 * An alternative to using iconIndex.
	 */
	public String iconName;
	
	public CenterIcon(CenterIconType iconType, String iconGroupId, int iconIndex)
	{
		this.iconType = iconType;
		this.iconGroupId = iconGroupId;
		this.iconIndex = iconIndex;
	}
	
	public CenterIcon(CenterIconType iconType, String iconName)
	{
		this.iconType = iconType;
		this.iconGroupId = "";
		this.iconIndex = -1;
		this.iconName = iconName;
	}
	
	private CenterIcon()
	{
	}
	
	public CenterIcon deepCopy()
	{
		CenterIcon copy = new CenterIcon();
		copy.iconType = iconType;
		copy.iconGroupId = iconGroupId;
		copy.iconIndex = iconIndex;
		copy.iconName = iconName;
		return copy;
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
