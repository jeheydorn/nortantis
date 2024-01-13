package nortantis.util.platform.awt;

import nortantis.util.platform.Color;
import nortantis.util.platform.ColorFactory;

public class AwtColorFactory extends ColorFactory
{
	public AwtColorFactory()
	{
		super();
	}

	@Override
	public Color create(int rgb)
	{
		return new AwtColor(rgb);
	}

	@Override
	public Color create(int red, int green, int blue)
	{
		return new AwtColor(red, green, blue);
	}

	@Override
	public Color create(int red, int green, int blue, int alpha)
	{
		return new AwtColor(red, green, blue, alpha);
	}
}
