package nortantis.platform;

public abstract class PlatformFactory
{
	private static PlatformFactory instance; 
	
	public static PlatformFactory getInstance()
	{
		return instance;
	}
	
	public static void setInstance(PlatformFactory instance)
	{
		PlatformFactory.instance = instance;
	}
	
	public abstract Image createImage(int width, int height, ImageType type);
	
	public abstract Image readImage(String filePath);
	
	public abstract void writeImage(Image image, String filePath);
	
	public abstract Font createFont(String name, FontStyle style, int size);
	
	public abstract Color createColor(int rgb, boolean hasAlpha);
	
	public abstract Color createColor(int red, int green, int blue);

	public abstract Color createColor(float red, float green, float blue);

	public abstract Color createColor(int red, int green, int blue, int alpha);
	
	public abstract Color createColorFromHSB(float hue, float saturation, float brightness);
}
