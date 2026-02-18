package nortantis.swing;

import nortantis.platform.Image;
import nortantis.platform.awt.AwtBridge;
import nortantis.platform.ImageHelper;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * For showing a preview of a background color when choosing the background color of a map.
 */
@SuppressWarnings("serial")
public class BGColorPreviewPanel extends ImagePanel implements ChangeListener
{
	private JColorChooser colorChooser;
	private BufferedImage originalBackground;
	private Color color;
	private Color colorBeingSelected;
	private ImageHelper.ColorizeAlgorithm colorizeAlgorithm;

	public BGColorPreviewPanel()
	{
	}

	@Override
	public void stateChanged(ChangeEvent arg0)
	{
		this.colorBeingSelected = colorChooser.getColor();
		colorBeingSelected = colorChooser.getColor();
		colorizeImageIfPresent(colorBeingSelected);
	}

	public void setColor(Color color)
	{
		this.color = color;
		colorizeImageIfPresent(color);
	}

	public void finishSelectingColor()
	{
		if (colorBeingSelected != null)
		{
			color = colorBeingSelected;
			colorizeImageIfPresent(color);
		}
	}

	private void colorizeImageIfPresent(Color color)
	{
		if (originalBackground != null)
		{
			colorizeImage(color);
			repaint();
		}
	}

	public void setColorizeAlgorithm(ImageHelper.ColorizeAlgorithm colorizeAlgorithm)
	{
		this.colorizeAlgorithm = colorizeAlgorithm;
	}

	public Color getColor()
	{
		return color;
	}

	public void setColorChooser(JColorChooser chooser)
	{
		this.colorChooser = chooser;
	}

	@Override
	public void setImage(BufferedImage image)
	{
		originalBackground = image;

		if (color == null || colorizeAlgorithm == ImageHelper.ColorizeAlgorithm.none)
		{
			super.setImage(originalBackground);
		}
		else
		{
			colorizeImage(color);
		}
	}

	private void colorizeImage(Color color)
	{
		if (colorizeAlgorithm != ImageHelper.ColorizeAlgorithm.none)
		{
			Image grayscale = ImageHelper.getInstance().convertToGrayscale(AwtBridge.fromBufferedImage(originalBackground));
			super.setImage(AwtBridge.toBufferedImage(ImageHelper.getInstance().colorize(grayscale, AwtBridge.fromAwtColor(color), colorizeAlgorithm)));
		}
	}

}
