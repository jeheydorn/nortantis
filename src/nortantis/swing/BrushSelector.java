package nortantis.swing;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * The combo box for choosing a brush in the editing tools. While it offers rectangle selection, which Edit modes do, rectangle selection is
 * its first option, followed by the brush sizes. The choice made while rectangle selection is offered is remembered separately from the
 * choice made while it isn't, so Edit mode and the painting modes each keep their own.
 */
public class BrushSelector
{
	private static final int maxBrushSizeDisplay = 70;

	private final JComboBox<Icon> comboBox;
	private RowHider hider;
	private final List<Integer> brushSizes;
	private final DefaultComboBoxModel<Icon> brushSizesModel;
	private final DefaultComboBoxModel<Icon> rectangleAndBrushSizesModel;

	BrushSelector(List<Integer> brushSizes, JComboBox<Icon> comboBox)
	{
		this.brushSizes = brushSizes;
		this.comboBox = comboBox;

		int displaySize = getIconDisplaySize(brushSizes);
		brushSizesModel = new DefaultComboBoxModel<>();
		rectangleAndBrushSizesModel = new DefaultComboBoxModel<>();
		rectangleAndBrushSizesModel.addElement(new RectangleSelectIcon(displaySize));
		for (int brushSize : brushSizes)
		{
			BrushSizeIcon icon = new BrushSizeIcon(brushSize, displaySize);
			brushSizesModel.addElement(icon);
			rectangleAndBrushSizesModel.addElement(icon);
		}
		comboBox.setModel(brushSizesModel);
	}

	static int getIconDisplaySize(List<Integer> brushSizes)
	{
		return Math.min(maxBrushSizeDisplay, java.util.Collections.max(brushSizes));
	}

	void setHider(RowHider hider)
	{
		this.hider = hider;
	}

	public void setVisible(boolean visible)
	{
		hider.setVisible(visible);
	}

	public boolean isVisible()
	{
		return hider.isVisible();
	}

	/**
	 * Sets whether rectangle selection is one of the options. Each setting keeps its own selected option, so switching back restores the
	 * option that was chosen the last time it was in effect.
	 */
	public void setOffersRectangleSelect(boolean offersRectangleSelect)
	{
		DefaultComboBoxModel<Icon> model = offersRectangleSelect ? rectangleAndBrushSizesModel : brushSizesModel;
		if (comboBox.getModel() != model)
		{
			comboBox.setModel(model);
		}
	}

	public boolean isRectangleSelect()
	{
		return comboBox.getModel() == rectangleAndBrushSizesModel && comboBox.getSelectedIndex() == 0;
	}

	/**
	 * The diameter, in panel pixels, of the selected brush. 1 means a single point. Rectangle selection reports 1, since a click without a
	 * drag selects what is under a single point.
	 */
	public int getBrushDiameter()
	{
		int index = comboBox.getSelectedIndex();
		if (comboBox.getModel() == rectangleAndBrushSizesModel)
		{
			index--;
		}
		if (index < 0 || index >= brushSizes.size())
		{
			return 1;
		}
		return brushSizes.get(index);
	}

	/**
	 * Draws a brush size as a filled circle. Drawn as shapes rather than an image so that it stays sharp at any display scaling.
	 */
	private static class BrushSizeIcon implements Icon
	{
		private final int brushSize;
		private final int displaySize;

		BrushSizeIcon(int brushSize, int displaySize)
		{
			this.brushSize = brushSize;
			this.displaySize = displaySize;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				setRenderingHints(g2);
				// A single-point brush is drawn larger so that it is visible.
				int sizeToDraw = Math.min(maxBrushSizeDisplay, brushSize == 1 ? 4 : brushSize);
				g2.setColor(Color.black);
				g2.fill(new Ellipse2D.Double(x + (displaySize - sizeToDraw) / 2.0, y + (displaySize - sizeToDraw) / 2.0, sizeToDraw, sizeToDraw));
				if (brushSize > maxBrushSizeDisplay)
				{
					g2.setColor(new Color(187, 187, 187));
					g2.setFont(c.getFont().deriveFont(Font.BOLD, 24f));
					String text = (brushSize / maxBrushSizeDisplay) + "x";
					// Center the glyphs' drawn bounds, since centering the font's line height leaves room for descenders this text
					// doesn't have, which puts it low.
					Rectangle2D textBounds = g2.getFont().createGlyphVector(g2.getFontRenderContext(), text).getVisualBounds();
					float textX = (float) (x + (displaySize - textBounds.getWidth()) / 2.0 - textBounds.getX());
					float textY = (float) (y + (displaySize - textBounds.getHeight()) / 2.0 - textBounds.getY());
					g2.drawString(text, textX, textY);
				}
			}
			finally
			{
				g2.dispose();
			}
		}

		@Override
		public int getIconWidth()
		{
			return displaySize;
		}

		@Override
		public int getIconHeight()
		{
			return displaySize;
		}
	}

	/**
	 * Draws the rectangle selection option as a dashed selection box.
	 */
	private static class RectangleSelectIcon implements Icon
	{
		private final int displaySize;

		RectangleSelectIcon(int displaySize)
		{
			this.displaySize = displaySize;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				setRenderingHints(g2);
				double width = displaySize * 0.8;
				double height = displaySize * 0.6;
				Rectangle2D box = new Rectangle2D.Double(x + (displaySize - width) / 2.0, y + (displaySize - height) / 2.0, width, height);
				g2.setColor(new Color(0, 0, 0, 70));
				g2.fill(box);
				g2.setColor(Color.black);
				g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] { 7f, 5f }, 0f));
				g2.draw(box);
			}
			finally
			{
				g2.dispose();
			}
		}

		@Override
		public int getIconWidth()
		{
			return displaySize;
		}

		@Override
		public int getIconHeight()
		{
			return displaySize;
		}
	}

	private static void setRenderingHints(Graphics2D g2)
	{
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
	}
}
