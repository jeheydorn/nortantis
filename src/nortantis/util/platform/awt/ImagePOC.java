package nortantis.util.platform.awt;

import java.awt.image.BufferedImage;
import java.io.IOException;

import nortantis.Stopwatch;
import nortantis.util.platform.ColorFactory;
import nortantis.util.platform.Image;
import nortantis.util.platform.ImageFactory;
import nortantis.util.platform.ImageType;

// TODO Remove this class when I'm done with it.
public class ImagePOC
{
	final static int size = 16000;
	
	public static void main(String[] args) throws IOException
	{
		ImageFactory.setInstance(new AwtImageFactory());
		ColorFactory.setInstance(new AwtColorFactory());

		testAsBufferedImage();
		testAsNortantisImage();
		testAsBufferedImage();
		testAsNortantisImage();
		testAsBufferedImage();
		testAsNortantisImage();
	}
	
	private static void testAsBufferedImage()
	{
		Stopwatch sw = new Stopwatch("create image using old method");
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				image.setRGB(x, y, new java.awt.Color(100, 100, 100).getRGB());
			}
		}
		
		sw.printElapsedTime();
	}
	
	private static void testAsNortantisImage()
	{
		Stopwatch sw = new Stopwatch("create image using new method");
		Image image = ImageFactory.getInstance().create(size, size, ImageType.ARGB);
		
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				image.setPixelColor(x, y, ColorFactory.getInstance().create(100, 100, 100));
			}
		}
		
		sw.printElapsedTime();
	}
}
