package nortantis.util.platform;

public abstract class ColorFactory
{
	private static ColorFactory instance; 
	
	public static ColorFactory getInstance()
	{
		return instance;
	}
	
	public static void setInstance(ColorFactory instance)
	{
		ColorFactory.instance = instance;
	}
	
	public abstract Color create(int rgb);
	
	public abstract Color create(int red, int green, int blue);
	
	public abstract Color create(int red, int green, int blue, int alpha);
}
