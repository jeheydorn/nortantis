package nortantis;

import nortantis.geom.IntDimension;
import nortantis.swing.translation.Translation;

/**
 * The allowed map dimensions for generated backgrounds.
 */
public enum GeneratedDimension
{
	Square(4096, 4096), Sixteen_by_9(4096, 2304), Golden_Ratio(4096, 2531), Custom(0, 0);

	public final int width;
	public final int height;

	/**
	 * The longer side shared by every preset dimension. Custom aspect ratios are normalized to this scale (see
	 * {@link #normalizeToPresetScale}).
	 */
	public static final int PRESET_LONG_SIDE = 4096;

	/**
	 * Maximum allowed aspect ratio (width:height or height:width). Aspect ratios more extreme than this are rejected by map generation.
	 */
	public static final int MAX_ASPECT_RATIO = 10;

	GeneratedDimension(int width, int height)
	{
		this.width = width;
		this.height = height;
	}

	public String displayName()
	{
		return Translation.get("GeneratedDimension." + name());
	}

	public double aspectRatio()
	{
		if (height == 0)
		{
			return 0;
		}
		return (double) width / height;
	}

	@Override
	public String toString()
	{
		if (this == Custom)
		{
			return displayName();
		}
		return displayName() + " (" + width + " \u00d7 " + height + ")";
	}

	public static GeneratedDimension fromDimensions(int w, int h)
	{
		for (GeneratedDimension d : values())
		{
			if (d == Custom)
			{
				continue;
			}
			if (d.width == w && d.height == h)
			{
				return d;
			}
		}
		return Custom;
	}

	/**
	 * Scales (width, height) so its longer side equals {@link #PRESET_LONG_SIDE} (rounding the shorter side), matching the scale at which
	 * preset dimensions are defined. This is the normalization used to produce the generated dimensions for a custom aspect ratio.
	 */
	public static IntDimension normalizeToPresetScale(int width, int height)
	{
		if (width >= height)
		{
			return new IntDimension(PRESET_LONG_SIDE, Math.max(1, (int) Math.round((double) PRESET_LONG_SIDE * height / width)));
		}
		return new IntDimension(Math.max(1, (int) Math.round((double) PRESET_LONG_SIDE * width / height)), PRESET_LONG_SIDE);
	}

	/**
	 * Returns the preset matching the given width:height aspect ratio (in either orientation), or {@link #Custom} if none match. Used to
	 * label a selection box (or sub-map) by its aspect ratio. A selection matches a preset if its shorter side is within one pixel of the
	 * shorter side that preset's ratio gives for its longer side. That tolerance absorbs the rounding of a ratio-locked selection to whole
	 * pixels, while still being far tighter than the gaps between presets. Matching is orientation-independent, so a rotated (portrait)
	 * selection still maps to its named ratio.
	 */
	public static GeneratedDimension fromAspectRatio(double width, double height)
	{
		if (width <= 0 || height <= 0)
		{
			return Custom;
		}
		double longSide = Math.max(width, height);
		double shortSide = Math.min(width, height);
		for (GeneratedDimension d : presets())
		{
			if (Math.abs(longSide / d.aspectRatio() - shortSide) <= 1.0)
			{
				return d;
			}
		}
		return Custom;
	}

	/**
	 * Returns all preset dimensions — all values except {@link #Custom}.
	 */
	public static GeneratedDimension[] presets()
	{
		GeneratedDimension[] all = values();
		GeneratedDimension[] result = new GeneratedDimension[all.length - 1];
		int j = 0;
		for (GeneratedDimension d : all)
		{
			if (d != Custom)
			{
				result[j++] = d;
			}
		}
		return result;
	}
}
