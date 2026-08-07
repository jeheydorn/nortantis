package nortantis.editor;

import java.util.Objects;

import nortantis.HSBColor;
import nortantis.platform.Color;

/**
 * An immutable bundle of the four color-related properties that determine how an icon is tinted when it is drawn: the fill color, the
 * filter color, and the maximize-opacity / fill-with-color flags. These mirror the same four values stored on {@link FreeIcon}.
 * <p>
 * It is attached (optionally) to {@link CenterIcon} and {@link CenterTrees} so that an icon can remember the specific colors it should be
 * drawn with, instead of falling back to the map's per-type icon colors. This is used by sub-map redistribution (so redistributed icons
 * keep the colors of the source icons they came from) and by dormant trees (so they reappear with the color they were originally drawn with
 * rather than the current per-type tree color). When a {@link CenterIcon}/{@link CenterTrees} has no {@code IconColors}, the per-type
 * colors are used, which is the normal case for generated and freshly edited maps.
 * </p>
 */
public class IconColors
{
	public final Color fillColor;
	public final HSBColor filterColor;
	public final boolean maximizeOpacity;
	public final boolean fillWithColor;

	public IconColors(Color fillColor, HSBColor filterColor, boolean maximizeOpacity, boolean fillWithColor)
	{
		this.fillColor = fillColor;
		this.filterColor = filterColor;
		this.maximizeOpacity = maximizeOpacity;
		this.fillWithColor = fillWithColor;
	}

	/**
	 * Returns the colors {@code icon} is drawn with, for copying them onto a {@link CenterIcon}/{@link CenterTrees} or comparing them
	 * against the colors one remembers.
	 */
	public static IconColors fromIcon(FreeIcon icon)
	{
		return new IconColors(icon.fillColor, icon.filterColor, icon.maximizeOpacity, icon.fillWithColor);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(fillColor, filterColor, maximizeOpacity, fillWithColor);
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		if (obj == null || getClass() != obj.getClass())
		{
			return false;
		}
		IconColors other = (IconColors) obj;
		return Objects.equals(fillColor, other.fillColor) && Objects.equals(filterColor, other.filterColor) && maximizeOpacity == other.maximizeOpacity && fillWithColor == other.fillWithColor;
	}
}
