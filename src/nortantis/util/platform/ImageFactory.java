package nortantis.util.platform;

public abstract class ImageFactory
{
	private static ImageFactory instance;
	
	public static ImageFactory getInstance()
	{
		return instance;
	}
	
	public static void setInstance(ImageFactory instance)
	{
		ImageFactory.instance = instance;
	}
	
	public abstract Image create(int width, int height, ImageType type);
	
	public abstract Image read(String filePath);
	
	public abstract Image write(Image image, String filePath);
}
