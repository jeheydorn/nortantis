package nortantis.platform.awt;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Stroke;

import org.apache.commons.lang3.NotImplementedException;

import nortantis.StrokeType;
import nortantis.platform.Color;
import nortantis.platform.Font;
import nortantis.platform.Image;
import nortantis.platform.Painter;
import nortantis.platform.Transform;

class AwtPainter extends Painter
{
	public Graphics2D g;

	public AwtPainter(Graphics2D graphics)
	{
		this.g = graphics;
	}

	@Override
	public void drawImage(Image image, int x, int y)
	{
		g.drawImage(((AwtImage) image).image, x, y, null);
	}
	
	@Override
	public void drawImage(Image image, int x, int y, int width, int height)
	{
		g.drawImage(((AwtImage) image).image, x, y, width, height, null);
	}

	@Override
	public void dispose()
	{
		g.dispose();
	}

	@Override
	public void drawRect(int x, int y, int width, int height)
	{
		g.drawRect(x, y, width, height);
	}

	@Override
	public void setColor(Color color)
	{
		g.setColor(((AwtColor) color).color);
	}

	@Override
	public void rotate(double angle, double pivotX, double pivotY)
	{
		g.rotate(angle, pivotX, pivotY);
	}

	@Override
	public void translate(double x, double y)
	{
		g.translate(x, y);
	}

	@Override
	public void setFont(Font font)
	{
		g.setFont(((AwtFont) font).font);
	}
	
	@Override
	public void drawString(String string, double x, double y)
	{
		g.drawString(string, (float) x, (float) y);
	}

	@Override
	public void setTransform(Transform transform)
	{
		g.setTransform(((AwtTransform) transform).transform);
	}

	@Override
	public Transform getTransform()
	{
		return new AwtTransform(g.getTransform());
	}

	@Override
	public Font getFont()
	{
		return new AwtFont(g.getFont());
	}

	@Override
	public Color getColor()
	{
		return new AwtColor(g.getColor());
	}

	@Override
	public void fillPolygon(int[] xPoints, int[] yPoints)
	{
		g.fillPolygon(xPoints, yPoints, xPoints.length);
	}

	@Override
	public void drawPolygon(int[] xPoints, int[] yPoints)
	{
		g.drawPolygon(xPoints, yPoints, xPoints.length);
	}

	@Override
	public void setGradient(float x1, float y1, Color color1, float x2, float y2, Color color2)
	{
		g.setPaint(new java.awt.GradientPaint(x1, y1, ((AwtColor) color1).color, x2, y2, ((AwtColor) color2).color));
	}

	@Override
	public void drawLine(int x1, int y1, int x2, int y2)
	{
		g.drawLine(x1, y1, x2, y2);
	}

	@Override
	public void fillOval(int x, int y, int width, int height)
	{
		g.fillOval(x, y, width, height);
	}

	@Override
	public void drawPolyline(int[] xPoints, int[] yPoints)
	{
		g.drawPolyline(xPoints, yPoints, xPoints.length);
	}

	@Override
	public void fillRect(int x, int y, int width, int height)
	{
		g.fillRect(x, y, width, height);
	}

	@Override
	public void setBasicStroke(float width)
	{
		// Use CAP_ROUND to avoid corners sticking out of the sides of thick lines (like rivers) when drawn piecewise.
		g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, null, 0f));
	}
	
	@Override
	public void setStrokeToSolidLineWithNoEndDecorations(float width)
	{
		g.setStroke(new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, null, 0f));
	}

	@Override
	public void setStroke(nortantis.Stroke stroke, double resolutionScale)
	{
		if (stroke.type == StrokeType.Solid)
		{
			setBasicStroke(stroke.width * (float) resolutionScale);
		}
		else
		{
			float scale = ((float) resolutionScale) * stroke.width;
			if (stroke.type == StrokeType.Dashes)
			{
				Stroke dashed = new BasicStroke(stroke.width * (float) resolutionScale, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f,
						new float[]
						{
								6f * (float) scale, 3f * (float) scale
						}, 0f);
				g.setStroke(dashed);
			}
			else if (stroke.type == StrokeType.Rounded_Dashes)
			{
				Stroke dashed = new BasicStroke(stroke.width * (float) resolutionScale, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
						new float[]
						{
								6f * (float) scale, 4f * (float) scale
						}, 0f);
				g.setStroke(dashed);
			}
			else if (stroke.type == StrokeType.Dots)
			{
				final float scaleBecauseDotsLookSmallerThanDashes = (3.9f / 2.7f);
				Stroke dashed = new BasicStroke(stroke.width * (float) resolutionScale * scaleBecauseDotsLookSmallerThanDashes,
						BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]
						{
								0f * (float) scale * scaleBecauseDotsLookSmallerThanDashes,
								2.0f * (float) scale * scaleBecauseDotsLookSmallerThanDashes
						}, 0f);
				g.setStroke(dashed);
			}
			else
			{
				throw new NotImplementedException("Unrecognized stroke type: " + stroke);
			}
		}
	}

	@Override
	public void drawOval(int x, int y, int width, int height)
	{
		g.drawOval(x, y, width, height);
	}

	@Override
	public int stringWidth(String string)
	{
		return g.getFontMetrics().stringWidth(string);
	}
	
	@Override
	public int charWidth(char c)
	{
		return g.getFontMetrics().charWidth(c);
	}

	@Override
	public int getFontAscent()
	{
		return g.getFontMetrics().getAscent();
	}

	@Override
	public int getFontDescent()
	{
		return g.getFontMetrics().getDescent();
	}

	@Override
	public void setAlphaComposite(nortantis.platform.AlphaComposite composite)
	{
		if (composite == nortantis.platform.AlphaComposite.Src)
		{
			g.setComposite(AlphaComposite.Src);
		}
		else if (composite == nortantis.platform.AlphaComposite.SrcAtop)
		{
			g.setComposite(AlphaComposite.SrcAtop);
		}
		else if (composite == nortantis.platform.AlphaComposite.SrcOver)
		{
			g.setComposite(AlphaComposite.SrcOver);
		}
		else if (composite == nortantis.platform.AlphaComposite.DstIn)
		{
			g.setComposite(AlphaComposite.DstIn);
		}
		else if (composite == nortantis.platform.AlphaComposite.Dst)
		{
			g.setComposite(AlphaComposite.Dst);
		}
		else if (composite == nortantis.platform.AlphaComposite.DstOver)
		{
			g.setComposite(AlphaComposite.DstOver);
		}
		else if (composite == nortantis.platform.AlphaComposite.SrcIn)
		{
			g.setComposite(AlphaComposite.SrcIn);
		}
		else if (composite == nortantis.platform.AlphaComposite.SrcOut)
		{
			g.setComposite(AlphaComposite.SrcOut);
		}
		else if (composite == nortantis.platform.AlphaComposite.DstOut)
		{
			g.setComposite(AlphaComposite.DstOut);
		}
		else if (composite == nortantis.platform.AlphaComposite.DstAtop)
		{
			g.setComposite(AlphaComposite.DstAtop);
		}
		else if (composite == nortantis.platform.AlphaComposite.Xor)
		{
			g.setComposite(AlphaComposite.Xor);
		}
		else if (composite == nortantis.platform.AlphaComposite.Clear)
		{
			g.setComposite(AlphaComposite.Clear);
		}
		else
		{
			throw new UnsupportedOperationException("Unimplemented alpha composite method: " + composite);
		}
	}

	@Override
	public void setAlphaComposite(nortantis.platform.AlphaComposite composite, float alpha)
	{
		if (composite == nortantis.platform.AlphaComposite.SrcAtop)
		{
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, alpha));
		}
		else if (composite == nortantis.platform.AlphaComposite.SrcOver)
		{
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
		}
		else
		{
			throw new UnsupportedOperationException("Unimplemented alpha composite method with alpha parameter. Composite method: " + composite);
		}
	}
	
	@Override
	public void setClip(int x, int y, int width, int height)
	{
		g.setClip(x, y, width, height);
		
	}
}
