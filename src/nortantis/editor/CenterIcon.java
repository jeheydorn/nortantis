package nortantis.editor;

import java.util.Objects;

/**
 * Stores which icon, if any, to draw for a center.
 */
public class CenterIcon
{
	public final CenterIconType iconType;
	public final String artPack;
	public final String iconGroupId;
	/**
	 * When moduloed by the number of icons in a group, this gives an index into the set of icons.
	 */
	public final int iconIndex;
	/**
	 * An alternative to using iconIndex.
	 */
	public final String iconName;
	/**
	 * The specific colors to draw this icon with, or null to use the map's per-type icon colors (the normal case). Set by sub-map
	 * redistribution so a redistributed icon keeps the color of the source icon it came from. See {@link IconColors}.
	 */
	public final IconColors colors;

	public CenterIcon(CenterIconType iconType, String artPack, String iconGroupId, int iconIndex)
	{
		this(iconType, artPack, iconGroupId, iconIndex, null, null);
	}

	public CenterIcon(CenterIconType iconType, String artPack, String iconGroupId, String iconName)
	{
		this(iconType, artPack, iconGroupId, -1, iconName, null);
	}

	private CenterIcon(CenterIconType iconType, String artPack, String iconGroupId, int iconIndex, String iconName, IconColors colors)
	{
		this.iconType = iconType;
		assert artPack != null;
		this.artPack = artPack;
		assert iconGroupId != null;
		this.iconGroupId = iconGroupId;
		this.iconIndex = iconIndex;
		this.iconName = iconName;
		this.colors = colors;
	}

	public CenterIcon copyWithArtPack(String artPack)
	{
		return new CenterIcon(iconType, artPack, iconGroupId, iconIndex, iconName, colors);
	}

	public CenterIcon copyWithIconGroupId(String iconGroupId)
	{
		return new CenterIcon(iconType, artPack, iconGroupId, iconIndex, iconName, colors);
	}

	public CenterIcon copyWithIconName(String iconName)
	{
		return new CenterIcon(iconType, artPack, iconGroupId, iconIndex, iconName, colors);
	}

	public CenterIcon copyWithColors(IconColors colors)
	{
		return new CenterIcon(iconType, artPack, iconGroupId, iconIndex, iconName, colors);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(artPack, iconGroupId, iconIndex, iconName, iconType, colors);
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
		CenterIcon other = (CenterIcon) obj;
		return Objects.equals(artPack, other.artPack) && Objects.equals(iconGroupId, other.iconGroupId) && iconIndex == other.iconIndex && Objects.equals(iconName, other.iconName)
				&& iconType == other.iconType && Objects.equals(colors, other.colors);
	}
}
