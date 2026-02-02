package nortantis.platform.skia;

import nortantis.platform.FontStyle;
import org.jetbrains.skia.Font;
import org.jetbrains.skia.FontMgr;
import org.jetbrains.skia.Typeface;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SkiaFont extends nortantis.platform.Font implements Serializable
{
	public transient Font skiaFont;
	private final String name;
	private final FontStyle style;
	private final float size;

	// Map Java logical font names to actual system font names that Skia can find
	private static final Map<String, String> LOGICAL_FONT_MAPPING = new HashMap<>();
	static
	{
		// Sans-serif fonts
		LOGICAL_FONT_MAPPING.put("SansSerif", "Arial");
		LOGICAL_FONT_MAPPING.put("Dialog", "Arial");
		LOGICAL_FONT_MAPPING.put("DialogInput", "Arial");
		// Serif fonts
		LOGICAL_FONT_MAPPING.put("Serif", "Times New Roman");
		// Monospace fonts
		LOGICAL_FONT_MAPPING.put("Monospaced", "Consolas");
	}

	/**
	 * Maps Java logical font names to actual system font names. If the font name is already a system font name, it is returned unchanged.
	 */
	public static String mapFontName(String fontName)
	{
		return LOGICAL_FONT_MAPPING.getOrDefault(fontName, fontName);
	}

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
		String mappedName = mapFontName(name);
		Typeface typeface = FontMgr.Companion.getDefault().matchFamilyStyle(mappedName, skiaStyle);
		this.skiaFont = new Font(typeface, size);
		this.skiaFont.setSubpixel(true);
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

	// Custom serialization methods
	private void writeObject(ObjectOutputStream out) throws IOException
	{
		out.defaultWriteObject();
	}

	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException
	{
		in.defaultReadObject();
		// Reconstruct the transient skiaFont field after deserialization
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
		String mappedName = mapFontName(name);
		Typeface typeface = FontMgr.Companion.getDefault().matchFamilyStyle(mappedName, skiaStyle);
		this.skiaFont = new Font(typeface, size);
		this.skiaFont.setSubpixel(true);
	}
}
