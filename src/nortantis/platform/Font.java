package nortantis.platform;

public abstract class Font
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

	public abstract String getFontName();
	public abstract String getName();
	public abstract String getFamily();

	public abstract FontStyle getStyle();
	public boolean isItalic()
	{
		FontStyle style = getStyle();
		return style == FontStyle.Italic || style == FontStyle.BoldItalic;
	}
	public abstract float getSize();

	public static boolean isInstalled(String fontFamily)
	{
		return PlatformFactory.getInstance().isFontInstalled(fontFamily);
	}
}
