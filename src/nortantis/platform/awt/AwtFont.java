package nortantis.platform.awt;

import nortantis.platform.Font;
import nortantis.platform.FontStyle;

public class AwtFont extends Font
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
		if (style == FontStyle.None)
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
	public int getSize()
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
		return FontStyle.None;
	}
}
