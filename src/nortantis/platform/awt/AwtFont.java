package nortantis.platform.awt;

import java.io.Serializable;
import java.util.Objects;

import nortantis.platform.Font;
import nortantis.platform.FontStyle;

@SuppressWarnings("serial")
public class AwtFont extends Font implements Serializable
{
	public java.awt.Font font;

	public AwtFont(java.awt.Font font)
	{
		this.font = font;
	}

	@Override
	public Font deriveFont(FontStyle style, float size)
	{
		return new AwtFont(font.deriveFont(convertFontStyle(style), size));
	}

	private int convertFontStyle(FontStyle style)
	{
		if (style == FontStyle.Italic)
		{
			return java.awt.Font.ITALIC;
		}
		if (style == FontStyle.Bold)
		{
			return java.awt.Font.BOLD;
		}
		if (style == FontStyle.BoldItalic)
		{
			return java.awt.Font.BOLD | java.awt.Font.ITALIC;
		}
		if (style == FontStyle.Plain)
		{
			return 0;
		}
		throw new IllegalArgumentException("Unrecognized font style: " + style);
	}

	@Override
	public String getFontName()
	{
		return font.getFontName();
	}

	@Override
	public float getSize()
	{
		return font.getSize();
	}

	@Override
	public String getName()
	{
		return font.getName();
	}

	@Override
	public String getFamily()
	{
		return font.getFamily();
	}

	@Override
	public FontStyle getStyle()
	{
		if (font.isItalic() && font.isBold())
		{
			return FontStyle.BoldItalic;
		}
		if (font.isBold())
		{
			return FontStyle.Bold;
		}
		if (font.isItalic())
		{
			return FontStyle.Italic;
		}
		return FontStyle.Plain;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(font);
	}

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
		AwtFont other = (AwtFont) obj;
		return Objects.equals(font, other.font);
	}
}
