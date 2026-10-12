package nortantis;

/**
 * The parts of a text style that a piece of text can have its own value for, rather than following the style of its type.
 */
public enum TextStylePart
{
	/** The font family and bold/italic. */
	Font,
	Size,
	Color,
	/** The background effect, the settings of every effect, and fade behind. */
	Background
}
