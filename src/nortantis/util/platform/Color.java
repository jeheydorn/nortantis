package nortantis.util.platform;

public abstract class Color
{
	public abstract int getRGB();
	
	public abstract int getRed();
	public abstract int getGreen();
	public abstract int getBlue();
	public abstract int getAlpha();
	
	public static Color create(int rgb)
	{
		return ColorFactory.getInstance().create(rgb);
	}
	
	public Color create(int red, int green, int blue)
	{
		return ColorFactory.getInstance().create(red, green, blue);
	}
	
	public Color create(int red, int green, int blue, int alpha)
	{
		return ColorFactory.getInstance().create(red, green, blue, alpha);
	}

}
