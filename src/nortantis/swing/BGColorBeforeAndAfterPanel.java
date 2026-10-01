package nortantis.swing;

import nortantis.platform.Image;
import nortantis.platform.ImageHelper;
import nortantis.platform.awt.AwtBridge;
import nortantis.swing.translation.Translation;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.image.BufferedImage;

/**
 * For previewing a new background color beside the old one when choosing the land or ocean color. Shows one strip of background, the left
 * half colored with the old color and the right half with the new one.
 */
@SuppressWarnings("serial")
public class BGColorBeforeAndAfterPanel extends JPanel
{
	private final Image grayscaleBackground;
	private final ImageHelper.ColorizeAlgorithm colorizeAlgorithm;
	private final BufferedImage beforeImage;
	private BufferedImage afterImage;

	/**
	 * @param background
	 *            The background to color. The strip is scaled to cover the panel, so it should be at least as large as the panel is expected
	 *            to be.
	 */
	public BGColorBeforeAndAfterPanel(Image background, ImageHelper.ColorizeAlgorithm colorizeAlgorithm, Color beforeColor, int stripHeight)
	{
		this.colorizeAlgorithm = colorizeAlgorithm;
		grayscaleBackground = colorizeAlgorithm == ImageHelper.ColorizeAlgorithm.none ? background : ImageHelper.getInstance().convertToGrayscale(background);
		beforeImage = colorize(beforeColor);
		afterImage = beforeImage;

		setLayout(new BorderLayout(0, 5));

		JPanel labels = new JPanel(new GridLayout(1, 2));
		labels.add(new JLabel(Translation.get("colorPicker.before"), SwingConstants.CENTER));
		labels.add(new JLabel(Translation.get("colorPicker.after"), SwingConstants.CENTER));
		add(labels, BorderLayout.NORTH);

		JPanel strip = new JPanel()
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				super.paintComponent(g);
				paintStrip(g, getWidth(), getHeight());
			}
		};
		// Narrow, so that the strip stretches to the width the color chooser already has rather than widening it.
		strip.setPreferredSize(new Dimension(200, stripHeight));
		add(strip, BorderLayout.CENTER);
	}

	/**
	 * As wide as the space the parent has for it, since the color chooser lays its preview panel out at its preferred width rather than
	 * stretching it.
	 */
	@Override
	public Dimension getPreferredSize()
	{
		Dimension size = super.getPreferredSize();
		Container parent = getParent();
		if (parent != null && parent.getWidth() > 0)
		{
			Insets insets = parent.getInsets();
			int gaps = parent.getLayout() instanceof FlowLayout flowLayout ? flowLayout.getHgap() * 2 : 0;
			size.width = Math.max(size.width, parent.getWidth() - insets.left - insets.right - gaps);
		}
		return size;
	}

	@Override
	public void addNotify()
	{
		super.addNotify();
		getParent().addComponentListener(parentResizeListener);
	}

	@Override
	public void removeNotify()
	{
		getParent().removeComponentListener(parentResizeListener);
		super.removeNotify();
	}

	private final ComponentListener parentResizeListener = new ComponentAdapter()
	{
		@Override
		public void componentResized(ComponentEvent e)
		{
			revalidate();
		}
	};

	public void setAfterColor(Color color)
	{
		afterImage = colorize(color);
		SwingHelper.repaintIncludingEdges(this);
	}

	private BufferedImage colorize(Color color)
	{
		if (colorizeAlgorithm == ImageHelper.ColorizeAlgorithm.none)
		{
			return AwtBridge.toBufferedImage(grayscaleBackground);
		}
		return AwtBridge.toBufferedImage(ImageHelper.getInstance().colorize(grayscaleBackground, AwtBridge.fromAwtColor(color), colorizeAlgorithm));
	}

	private void paintStrip(Graphics g, int width, int height)
	{
		// Both halves are drawn at the same position and scale, so the texture continues unbroken across the boundary between them.
		double scale = Math.max(width / (double) beforeImage.getWidth(), height / (double) beforeImage.getHeight());
		int drawnWidth = (int) Math.ceil(beforeImage.getWidth() * scale);
		int drawnHeight = (int) Math.ceil(beforeImage.getHeight() * scale);
		int beforeWidth = width / 2;
		drawClipped(g, beforeImage, 0, beforeWidth, drawnWidth, drawnHeight, height);
		drawClipped(g, afterImage, beforeWidth, width - beforeWidth, drawnWidth, drawnHeight, height);
	}

	private static void drawClipped(Graphics g, BufferedImage image, int clipX, int clipWidth, int drawnWidth, int drawnHeight, int height)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		try
		{
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g2.clipRect(clipX, 0, clipWidth, height);
			g2.drawImage(image, 0, 0, drawnWidth, drawnHeight, null);
		}
		finally
		{
			g2.dispose();
		}
	}
}
