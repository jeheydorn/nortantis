package nortantis.util.platform.awt;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.apache.commons.io.FilenameUtils;

import nortantis.util.ImageHelper;
import nortantis.util.platform.Image;
import nortantis.util.platform.ImageFactory;
import nortantis.util.platform.ImageType;

public class AwtImageFactory extends ImageFactory
{
	public AwtImageFactory()
	{
		super();
	}
	
	@Override
	public Image create(int width, int height, ImageType type)
	{
		return new AwtImage(width, height, type);
	}

	@Override
	public Image read(String filePath)
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
	public Image write(Image image, String filePath)
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
					image = ImageHelper.convertARGBtoRGB(image);
				}

				Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");

				if (!writers.hasNext())
					throw new IllegalStateException("No writers found for jpg format.");

				ImageWriter writer = (ImageWriter) writers.next();
				OutputStream os = new FileOutputStream(new File(filePath));
				ImageOutputStream ios = ImageIO.createImageOutputStream(os);
				writer.setOutput(ios);

				ImageWriteParam param = writer.getDefaultWriteParam();

				param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
				final float quality = 0.95f;
				param.setCompressionQuality(quality);

				writer.write(null, new IIOImage(((AwtImage) ImageHelper.convertARGBtoRGB(image)).image, null, null), param);

			}
			else
			{
				ImageIO.write(image, FilenameUtils.getExtension(filePath), new File(filePath));
			}
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}
}
