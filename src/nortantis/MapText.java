package nortantis;

import nortantis.geom.Point;
import nortantis.geom.RotatedRectangle;

import java.io.Serializable;
import java.util.Objects;

/**
 * Stores a piece of text (and data about it) drawn onto a map.
 *
 * @author joseph
 *
 */
@SuppressWarnings("serial")
public class MapText implements Serializable
{
	public String value;
	/**
	 * The (possibly rotated) bounding boxes of the text, including what its background draws around it. These are a rendering cache
	 * populated by {@link nortantis.TextDrawer} during draws and depend on the current displayQualityScale — not part of the persistent edit
	 * state. {@link #deepCopy()} preserves the references so that a restored undo snapshot stays clickable during the brief window before
	 * the next draw fixes up bounds at the current resolution; {@link nortantis.swing.MapEdits#textBoundsNeedRefresh} tracks whether those
	 * preserved bounds can still be trusted.
	 *
	 * {@code volatile} because the background draw thread writes these while the EDT may be reading them in hit-testing. {@code transient}
	 * so Java serialization (used by {@link nortantis.util.Helper#deepCopy}) never persists them.
	 */
	public transient volatile RotatedRectangle line1Bounds;
	public transient volatile RotatedRectangle line2Bounds;

	public TextType type;

	/**
	 * If the user has rotated the text, then this stores the angle. 0 means horizontal.
	 */
	public double angle;

	/**
	 * For text that has one line, this is the center of the text both horizontally and vertically. For text that has multiple lines, this
	 * is the horizontal center and the vertical center between the two lines.
	 *
	 * This is stored in a resolution-invariant way, meaning the creating the map at a different resolution will give the same location
	 * (within the limits of floating point precision).
	 */
	public Point location;

	/**
	 * Allows the user to override whether the text can be split into two lines.
	 */
	public LineBreak lineBreak;

	public double curvature;
	public int spacing;

	public TextStyle style;

	/**
	 * Seeds the hand-drawn wobble of shape backgrounds. Stored so the wobble stays the same when the text is moved, rotated, or edited.
	 */
	public long backgroundSeed;

	private MapText(String text, Point location, double angle, TextType type, RotatedRectangle line1Bounds, RotatedRectangle line2Bounds, LineBreak lineBreak, double curvature,
			int spacing, TextStyle style, long backgroundSeed)
	{
		this.value = text;
		this.line1Bounds = line1Bounds;
		this.line2Bounds = line2Bounds;
		this.location = location;
		this.angle = angle;
		this.type = type;
		this.lineBreak = lineBreak;
		this.curvature = curvature;
		this.spacing = spacing;
		this.style = style;
		this.backgroundSeed = backgroundSeed;
	}

	public MapText(String text, Point location, double angle, TextType type, LineBreak lineBreak, double curvature, int spacing, TextStyle style, long backgroundSeed)
	{
		this(text, location, angle, type, null, null, lineBreak, curvature, spacing, style, backgroundSeed);
	}

	public MapText deepCopy()
	{
		return new MapText(value, new Point(location.x, location.y), angle, type, line1Bounds, line2Bounds, lineBreak, curvature, spacing, style.copy(), backgroundSeed);
	}

	/**
	 * See equals(...) for a list of fields to exclude.
	 */
	@Override
	public int hashCode()
	{
		return Objects.hash(angle, backgroundSeed, curvature, lineBreak, location, spacing, style, type, value);
	}

	/**
	 * Excludes fields that get filled in on the fly during map creation: line1Bounds, line2Bounds
	 */
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
		MapText other = (MapText) obj;
		return Double.doubleToLongBits(angle) == Double.doubleToLongBits(other.angle) && backgroundSeed == other.backgroundSeed
				&& Double.doubleToLongBits(curvature) == Double.doubleToLongBits(other.curvature) && lineBreak == other.lineBreak && Objects.equals(location, other.location)
				&& spacing == other.spacing && Objects.equals(style, other.style) && type == other.type && Objects.equals(value, other.value);
	}

	@Override
	public String toString()
	{
		return "MapText [value=" + value + ", line1Bounds=" + line1Bounds + ", line2Bounds=" + line2Bounds + ", type=" + type + ", angle=" + angle + ", location=" + location + ", lineBreak="
				+ lineBreak + ", curvature=" + curvature + ", spacing=" + spacing + ", style=" + style + "]";
	}

}
