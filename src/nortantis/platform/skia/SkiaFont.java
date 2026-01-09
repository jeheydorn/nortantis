package nortantis.platform.skia;

import java.util.Objects;
import org.jetbrains.skia.Font;
import org.jetbrains.skia.Typeface;
import nortantis.platform.FontStyle;

public class SkiaFont extends nortantis.platform.Font
{
	public final Font skiaFont;
	private final String name;
	private final FontStyle style;
	private final float size;

	public SkiaFont(String name, FontStyle style, float size)
	{
		this.name = name;
		this.style = style;
		this.size = size;
		
		int weight = 400; // Normal
		boolean italic = false;
		if (style == FontStyle.Bold || style == FontStyle.BoldItalic)
		{
			weight = 700; // Bold
		}
		if (style == FontStyle.Italic || style == FontStyle.BoldItalic)
		{
			italic = true;
		}

		Typeface typeface = Typeface.makeFromName(name, new org.jetbrains.skia.FontStyle(weight, 5, italic ? org.jetbrains.skia.FontSlant.ITALIC : org.jetbrains.skia.FontSlant.UPRIGHT));
		this.skiaFont = new Font(typeface, size);
	}

	public SkiaFont(Font skiaFont, String name, FontStyle style, float size)
	{
		this.skiaFont = skiaFont;
		this.name = name;
		this.style = style;
		this.size = size;
	}

	@Override
	public nortantis.platform.Font deriveFont(FontStyle style, float size)
	{
		return new SkiaFont(this.name, style, size);
	}

	@Override
	public String getFontName()
	{
		return name;
	}

	@Override
	public String getName()
	{
		return name;
	}

	@Override
	public String getFamily()
	{
		return name;
	}

	@Override
	public FontStyle getStyle()
	{
		return style;
	}

	@Override
	public float getSize()
	{
		return size;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		SkiaFont skiaFont1 = (SkiaFont) o;
		return Float.compare(skiaFont1.size, size) == 0 && Objects.equals(name, skiaFont1.name) && style == skiaFont1.style;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(name, style, size);
	}
}
