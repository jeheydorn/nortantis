package nortantis;

import nortantis.swing.translation.Translation;

/**
 * What is drawn behind a piece of text. Effects in the same family share their settings, so switching between them keeps the settings.
 */
public enum TextBackgroundEffect
{
	None, Glow, Outline, BoldBackground, Box, Scroll, Banner;

	/**
	 * Glow and Outline: a band of color around the letters, with a color and a size.
	 */
	public boolean isHalo()
	{
		return this == Glow || this == Outline;
	}

	/**
	 * Box, Scroll, and Banner: a shape drawn behind the text and sized to fit it, with a fill color, line color, line width, and jitter.
	 */
	public boolean isShape()
	{
		return this == Box || this == Scroll || this == Banner;
	}

	/**
	 * Whether background fade applies along with this effect. A shape covers what fade would erase, so fade is not used with one.
	 */
	public boolean allowsFade()
	{
		return !isShape();
	}

	@Override
	public String toString()
	{
		return Translation.get("TextBackgroundEffect." + name());
	}
}
