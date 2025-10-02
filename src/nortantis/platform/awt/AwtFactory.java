package nortantis.platform.awt;

import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.apache.commons.io.FilenameUtils;

import nortantis.geom.IntRectangle;
import nortantis.geom.Rectangle;
import nortantis.geom.RotatedRectangle;
import nortantis.platform.BackgroundTask;
import nortantis.platform.Color;
import nortantis.platform.Font;
import nortantis.platform.FontStyle;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.Painter;
import nortantis.platform.PlatformFactory;
import nortantis.swing.SwingHelper;

public class AwtFactory extends PlatformFactory
{

	@Override
	public Image createImage(int width, int height, ImageType type)
	{
		return new AwtImage(width, height, type);
	}

	@Override
	public Image readImage(String filePath)
	{
		try
		{
			BufferedImage image = ImageIO.read(new File(filePath));
			if (image == null)
			{
				throw new RuntimeException(
						"Can't read the file " + filePath + ". This can happen if the file is an unsupported format or is corrupted, "
								+ "such as if you saved it with a file extension that doesn't match its actual format.");
			}

			return new AwtImage(image);
		}
		catch (IOException e)
		{
			throw new RuntimeException("Can't read the file " + filePath);
		}
	}

	@Override
	public Image readImage(InputStream stream)
	{
		if (stream == null)
		{
			throw new RuntimeException("Unable to read an image file stream because it is null.");
		}

		try
		{
			return wrap(ImageIO.read(stream));
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	@Override
	public void writeImage(Image image, String filePath)
	{
		try
		{
			String extension = FilenameUtils.getExtension(filePath).toLowerCase();
			if (extension.equals("jpg") || extension.equals("jpeg"))
			{
				if (image.getType() == ImageType.ARGB)
				{
					// JPEG does not support transparency. Trying to write an
					// image with transparent pixels causes
					// it to silently not be created.
					image = convertARGBToRGB(image);
				}

				Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");

				if (!writers.hasNext())
					throw new IllegalStateException("No writers found for jpg format.");

				ImageWriter writer = null;
				OutputStream os = null;
				try
				{
					writer = (ImageWriter) writers.next();
					os = new FileOutputStream(new File(filePath));
					ImageOutputStream ios = ImageIO.createImageOutputStream(os);
					writer.setOutput(ios);

					ImageWriteParam param = writer.getDefaultWriteParam();

					param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
					final float quality = 0.95f;
					param.setCompressionQuality(quality);

					writer.write(null, new IIOImage(((AwtImage) convertARGBToRGB(image)).image, null, null), param);

				}
				finally
				{
					if (writer != null)
					{
						writer.dispose();
					}
					if (os != null)
					{
						os.close();
					}
				}

			}
			else
			{
				ImageIO.write(((AwtImage) image).image, FilenameUtils.getExtension(filePath), new File(filePath));
			}
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	public static Image convertARGBToRGB(Image image)
	{
		Image newImage = Image.create(image.getWidth(), image.getHeight(), ImageType.RGB);
		Painter p = newImage.createPainter();
		p.drawImage(image, 0, 0);
		p.dispose();
		for (int i = 0; i < newImage.getWidth(); i++)
		{
			for (int j = 0; j < newImage.getHeight(); j++)
			{
				int argb = newImage.getRGB(i, j);
				int alpha = (argb >> 24) & 0xff;
				int rgb = argb & 0x00ffffff;
				if (alpha != 0)
				{
					newImage.setRGB(i, j, rgb);
				}
				else
				{
					newImage.setRGB(i, j, 0x000000);
				}
			}
		}
		return newImage;
	}

	@Override
	public Font createFont(String name, FontStyle style, float size)
	{
		return new AwtFont(new java.awt.Font(name, style.value, (int) size));
	}

	@Override
	public Color createColor(int rgb, boolean hasAlpha)
	{
		return new AwtColor(rgb, hasAlpha);
	}

	@Override
	public Color createColor(int red, int green, int blue)
	{
		return new AwtColor(red, green, blue);
	}

	@Override
	public Color createColor(float red, float green, float blue)
	{
		return new AwtColor(red, green, blue);
	}

	@Override
	public Color createColor(int red, int green, int blue, int alpha)
	{
		return new AwtColor(red, green, blue, alpha);
	}

	@Override
	public Color createColorFromHSB(float hue, float saturation, float brightness)
	{
		return Color.create(java.awt.Color.HSBtoRGB(hue, saturation, brightness));
	}

	public static BufferedImage unwrap(Image image)
	{
		if (image == null)
		{
			return null;
		}
		return ((AwtImage) image).image;
	}

	public static Graphics2D unwrap(Painter p)
	{
		if (p == null)
		{
			return null;
		}
		return ((AwtPainter) p).g;
	}

	public static Image wrap(BufferedImage image)
	{
		if (image == null)
		{
			return null;
		}
		return new AwtImage(image);
	}

	public static Color wrap(java.awt.Color color)
	{
		if (color == null)
		{
			return null;
		}
		return new AwtColor(color);
	}

	public static java.awt.Color unwrap(Color color)
	{
		if (color == null)
		{
			return null;
		}
		return ((AwtColor) color).color;
	}

	public static Font wrap(java.awt.Font font)
	{
		if (font == null)
		{
			return null;
		}
		return new AwtFont(font);
	}

	public static java.awt.Font unwrap(Font font)
	{
		if (font == null)
		{
			return null;
		}
		return ((AwtFont) font).font;
	}

	public static java.awt.Rectangle toAwtRectangle(Rectangle rect)
	{
		return new java.awt.Rectangle((int) rect.x, (int) rect.y, (int) rect.width, (int) rect.height);
	}

	public static java.awt.Rectangle toAwtRectangle(IntRectangle rect)
	{
		return new java.awt.Rectangle(rect.x, rect.y, rect.width, rect.height);
	}

	public static java.awt.geom.Area toAwtArea(RotatedRectangle rect)
	{
		AffineTransform transform = new AffineTransform();
		transform.rotate(rect.angle, rect.pivotX, rect.pivotY);
		java.awt.Shape rotatedRect = transform
				.createTransformedShape(new java.awt.Rectangle((int) rect.x, (int) rect.y, (int) rect.width, (int) rect.height));
		return new java.awt.geom.Area(rotatedRect);
	}

	public static java.awt.geom.Area toAwtArea(Rectangle rect)
	{
		return new java.awt.geom.Area(new java.awt.Rectangle((int) rect.x, (int) rect.y, (int) rect.width, (int) rect.height));
	}

	public static Painter wrap(java.awt.Graphics2D g)
	{
		if (g == null)
		{
			return null;
		}
		return new AwtPainter(g);
	}

	@Override
	public boolean isFontInstalled(String fontFamily)
	{
		String[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
		for (String font : fonts)
		{
			if (font.equals(fontFamily))
			{
				return true;
			}
		}
		return false;
	}

	@Override
	public <T> void doInBackgroundThread(BackgroundTask<T> task)
	{
		SwingWorker<T, Void> worker = new SwingWorker<>()
		{
			@Override
			protected T doInBackground() throws Exception
			{
				return task.doInBackground();
			}

			@Override
			protected void done()
			{
				T result = null;
				try
				{
					result = get();
				}
				catch (InterruptedException ex)
				{
					throw new RuntimeException(ex);
				}
				catch (Exception ex)
				{
					SwingHelper.handleBackgroundThreadException(ex, null, false);
				}

				task.done(result);
			}
		};

		worker.execute();
	}

	@Override
	public void doInMainUIThreadAsynchronous(Runnable toRun)
	{
		if (SwingUtilities.isEventDispatchThread())
		{
			toRun.run();
		}
		else
		{
			SwingUtilities.invokeLater(toRun);
		}
	}

}
