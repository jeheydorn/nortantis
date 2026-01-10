package nortantis.platform.skia;

import java.io.Serializable;
import java.util.Objects;
import org.jetbrains.skia.Font;
import org.jetbrains.skia.FontMgr;
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

		org.jetbrains.skia.FontStyle skiaStyle = org.jetbrains.skia.FontStyle.Companion.getNORMAL();
		if (style == FontStyle.Bold)
		{
			skiaStyle = org.jetbrains.skia.FontStyle.Companion.getBOLD();
		}
		else if (style == FontStyle.Italic)
		{
			skiaStyle = org.jetbrains.skia.FontStyle.Companion.getITALIC();
		}
		else if (style == FontStyle.BoldItalic)
		{
			skiaStyle = org.jetbrains.skia.FontStyle.Companion.getBOLD_ITALIC();
		}

		Typeface typeface = FontMgr.Companion.getDefault().matchFamilyStyle(name, skiaStyle);
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
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		SkiaFont skiaFont1 = (SkiaFont) o;
		return Float.compare(skiaFont1.size, size) == 0 && Objects.equals(name, skiaFont1.name) && style == skiaFont1.style;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(name, style, size);
	}
}
