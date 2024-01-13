package nortantis.util.platform;

public abstract class Image
{
	private ImageType type;
	
	protected Image(ImageType type)
	{
		this.type = type;
	}	
	public abstract int getPixelLevel(int x, int y);

	public abstract void setPixelColor(int x, int y, Color color);

	public abstract int getRGB(int x, int y);
	
	public abstract void setRGB(int x, int y, int rgb);

	public abstract Color getPixelColor(int x, int y);

	public abstract void setPixelLevel(int x, int y, int level);
	
	
	
	public abstract float getPixelLevelFloat(int x, int y);
	
	public abstract int getWidth();
	
	public abstract int getHeight();
	
	public ImageType getType()
	{
		return type;
	}
	
	public boolean isGrayscaleOrBinary()
	{
		return type == ImageType.Grayscale || type == ImageType.Binary;
	}
	
	public abstract Painter createPainter(DrawQuality quality);
	
	public Painter createPainter()
	{
		return createPainter(DrawQuality.Normal);
	}
	
	public static Image create(int width, int height, ImageType type)
	{
		return ImageFactory.getInstance().create(width, height, type);
	}
	
	public static Image read(String filePath)
	{
		return ImageFactory.getInstance().read(filePath);
	}
	
	public void write(String filePath)
	{
		ImageFactory.getInstance().write(this, filePath);
	}
}
