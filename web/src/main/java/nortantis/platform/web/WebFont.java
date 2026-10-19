package nortantis.platform.web;

import java.util.Objects;

import nortantis.platform.Font;
import nortantis.platform.FontStyle;

/**
 * A font, identified by its family name, style, and size. The text rasterizer draws it.
 */
@SuppressWarnings("serial")
public class WebFont extends Font
{
	final String name;
	final FontStyle style;
	final float size;

	public WebFont(String name, FontStyle style, float size)
	{
		this.name = name;
		this.style = style;
		this.size = size;
	}

	@Override
	public Font deriveFont(FontStyle style, float size)
	{
		return new WebFont(name, style, size);
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
	public int canDisplayUpTo(String str)
	{
		return -1;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(name, style, size);
	}

	@Override
	public boolean equals(Object obj)
	{
		if (!(obj instanceof WebFont))
		{
			return false;
		}
		WebFont other = (WebFont) obj;
		return name.equals(other.name) && style == other.style && size == other.size;
	}
}
