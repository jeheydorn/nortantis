package nortantis;

import java.io.Serializable;

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
}
