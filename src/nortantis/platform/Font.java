package nortantis.platform;

import java.io.Serializable;

public abstract class Font implements Serializable
{
	public static Font create(String name, FontStyle style, float size)
	{
		return PlatformFactory.getInstance().createFont(name, style, size);
	}

	/**
	 * Creates a new font with the same name as this one but with the given style and size.
	 * 
	 * @param style
	 * @param size
	 * @return
	 */
	public abstract Font deriveFont(FontStyle style, float size);

	public abstract String getName();

	public abstract String getFamily();

	public abstract FontStyle getStyle();

	public boolean isItalic()
	{
		FontStyle style = getStyle();
		return style == FontStyle.Italic || style == FontStyle.BoldItalic;
	}

	public abstract float getSize();

	/**
	 * Returns the index of the first character in the given string that this font cannot display, or -1 if the font can display the entire
	 * string.
	 */
	public abstract int canDisplayUpTo(String str);

	public static boolean isInstalled(String fontFamily)
	{
		return PlatformFactory.getInstance().isFontInstalled(fontFamily);
	}

	@Override
	public String toString()
	{
		return "Font [getName()=" + getName() + ", getSize()=" + getSize() + "]";
	}

}
