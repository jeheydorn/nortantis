package nortantis.swing;

import java.awt.Color;
import java.awt.image.BufferedImage;

import javax.swing.JColorChooser;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import nortantis.platform.awt.AwtFactory;
import nortantis.util.ImageHelper;

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
	private ImageHelper.ColorifyAlgorithm colorifyAlgorithm;

	public BGColorPreviewPanel()
	{
	}

	@Override
	public void stateChanged(ChangeEvent arg0)
	{
		this.colorBeingSelected = colorChooser.getColor();
		colorBeingSelected = colorChooser.getColor();
		colorifyImageIfPresent(colorBeingSelected);
	}

	public void setColor(Color color)
	{
		this.color = color;
		colorifyImageIfPresent(color);
	}

	public void finishSelectingColor()
	{
		if (colorBeingSelected != null)
		{
			color = colorBeingSelected;
			colorifyImageIfPresent(color);
		}
	}

	private void colorifyImageIfPresent(Color color)
	{
		if (originalBackground != null)
		{
			colorifyImage(color);
			repaint();
		}
	}

	public void setColorifyAlgorithm(ImageHelper.ColorifyAlgorithm colorfyAlgorithm)
	{
		this.colorifyAlgorithm = colorfyAlgorithm;
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

		if (color == null || colorifyAlgorithm == ImageHelper.ColorifyAlgorithm.none)
		{
			super.setImage(originalBackground);
		}
		else
		{
			colorifyImage(color);
		}
	}

	private void colorifyImage(Color color)
	{
		if (colorifyAlgorithm != ImageHelper.ColorifyAlgorithm.none)
		{
			super.setImage(AwtFactory
					.unwrap(ImageHelper.colorify(AwtFactory.wrap(originalBackground), AwtFactory.wrap(color), colorifyAlgorithm)));
		}
	}

}
