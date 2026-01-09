package nortantis.platform.skia;

import java.util.List;
import org.jetbrains.skia.Canvas;
import org.jetbrains.skia.Paint;
import org.jetbrains.skia.PaintMode;
import org.jetbrains.skia.Path;
import org.jetbrains.skia.Rect;
import org.jetbrains.skia.Matrix33;
import org.jetbrains.skia.BlendMode;
import org.jetbrains.skia.SurfaceProps;
import nortantis.Stroke;
import nortantis.StrokeType;
import nortantis.geom.FloatPoint;
import nortantis.geom.Point;
import nortantis.platform.AlphaComposite;
import nortantis.platform.Color;
import nortantis.platform.Font;
import nortantis.platform.Image;
import nortantis.platform.Painter;
import nortantis.platform.Transform;

public class SkiaPainter extends Painter
{
	public final Canvas canvas;
	private Paint paint;
	private SkiaFont font;
	private SkiaColor color;

	public SkiaPainter(Canvas canvas)
	{
		this.canvas = canvas;
		this.paint = new Paint();
		this.paint.setAntiAlias(true);
	}

	@Override
	public void drawImage(Image image, int x, int y)
	{
		SkiaImage skImage = (SkiaImage) image;
		org.jetbrains.skia.Image skiaImage = skImage.getBitmap().makeImageSnapshot();
		canvas.drawImage(skiaImage, (float) x, (float) y, paint);
		skiaImage.close();
	}

	@Override
	public void drawImage(Image image, int x, int y, int width, int height)
	{
		SkiaImage skImage = (SkiaImage) image;
		org.jetbrains.skia.Image skiaImage = skImage.getBitmap().makeImageSnapshot();
		canvas.drawImageRect(skiaImage, Rect.makeXYWH(x, y, width, height), paint);
		skiaImage.close();
	}

	@Override
	public void setAlphaComposite(AlphaComposite composite, float alpha)
	{
		setAlphaComposite(composite);
		paint.setAlpha((int) (alpha * 255));
	}

	@Override
	public void setAlphaComposite(AlphaComposite composite)
	{
		paint.setBlendMode(toBlendMode(composite));
	}

	private BlendMode toBlendMode(AlphaComposite composite)
	{
		switch (composite)
		{
			case SrcOver: return BlendMode.SRC_OVER;
			case Src: return BlendMode.SRC;
			case SrcAtop: return BlendMode.SRC_ATOP;
			case DstIn: return BlendMode.DST_IN;
			case Dst: return BlendMode.DST;
			case DstOver: return BlendMode.DST_OVER;
			case SrcIn: return BlendMode.SRC_IN;
			case SrcOut: return BlendMode.SRC_OUT;
			case DstOut: return BlendMode.DST_OUT;
			case DstAtop: return BlendMode.DST_ATOP;
			case Xor: return BlendMode.XOR;
			case Clear: return BlendMode.CLEAR;
			default: return BlendMode.SRC_OVER;
		}
	}

	@Override
	public void setColor(Color color)
	{
		this.color = (SkiaColor) color;
		paint.setColor(this.color.getRGB());
	}

	@Override
	public void drawRect(int x, int y, int width, int height)
	{
		paint.setMode(PaintMode.STROKE);
		canvas.drawRect(Rect.makeXYWH(x, y, width, height), paint);
	}

	@Override
	public void dispose()
	{
		paint.close();
		// Canvas is usually owned by the Image/Surface, but we might need to close it if we created it.
	}

	@Override
	public void rotate(double angle, double pivotX, double pivotY)
	{
		canvas.rotate((float) Math.toDegrees(angle), (float) pivotX, (float) pivotY);
	}

	@Override
	public void translate(double x, double y)
	{
		canvas.translate((float) x, (float) y);
	}

	@Override
	public void setFont(Font font)
	{
		this.font = (SkiaFont) font;
	}

	@Override
	public void drawString(String string, double x, double y)
	{
		if (font != null)
		{
			paint.setMode(PaintMode.FILL);
			canvas.drawString(string, (float) x, (float) y, font.skiaFont, paint);
		}
	}

	@Override
	public void setTransform(Transform transform)
	{
		canvas.setMatrix(((SkiaTransform) transform).matrix);
	}

	@Override
	public Transform getTransform()
	{
		return new SkiaTransform(canvas.getLocalToDeviceAsMatrix33());
	}

	@Override
	public Font getFont()
	{
		return font;
	}

	@Override
	public Color getColor()
	{
		return color;
	}

	@Override
	public void fillPolygon(int[] xPoints, int[] yPoints)
	{
		if (xPoints.length == 0) return;
		Path path = new Path();
		path.moveTo(xPoints[0], yPoints[0]);
		for (int i = 1; i < xPoints.length; i++)
		{
			path.lineTo(xPoints[i], yPoints[i]);
		}
		path.closePath();
		paint.setMode(PaintMode.FILL);
		canvas.drawPath(path, paint);
		path.close();
	}

	@Override
	public void drawPolygon(int[] xPoints, int[] yPoints)
	{
		if (xPoints.length == 0) return;
		Path path = new Path();
		path.moveTo(xPoints[0], yPoints[0]);
		for (int i = 1; i < xPoints.length; i++)
		{
			path.lineTo(xPoints[i], yPoints[i]);
		}
		path.closePath();
		paint.setMode(PaintMode.STROKE);
		canvas.drawPath(path, paint);
		path.close();
	}

	@Override
	public void drawPolyline(int[] xPoints, int[] yPoints)
	{
		if (xPoints.length == 0) return;
		Path path = new Path();
		path.moveTo(xPoints[0], yPoints[0]);
		for (int i = 1; i < xPoints.length; i++)
		{
			path.lineTo(xPoints[i], yPoints[i]);
		}
		paint.setMode(PaintMode.STROKE);
		canvas.drawPath(path, paint);
		path.close();
	}

	@Override
	public void drawPolygonFloat(List<FloatPoint> points)
	{
		if (points.isEmpty()) return;
		Path path = new Path();
		path.moveTo(points.get(0).x, points.get(0).y);
		for (int i = 1; i < points.size(); i++)
		{
			path.lineTo(points.get(i).x, points.get(i).y);
		}
		path.closePath();
		paint.setMode(PaintMode.STROKE);
		canvas.drawPath(path, paint);
		path.close();
	}

	@Override
	public void setGradient(float x1, float y1, Color color1, float x2, float y2, Color color2)
	{
		paint.setShader(org.jetbrains.skia.Shader.Companion.makeLinearGradient(x1, y1, x2, y2, 
				new int[] { color1.getRGB(), color2.getRGB() }, null, org.jetbrains.skia.GradientStyle.Companion.getDEFAULT()));
	}

	@Override
	public void setBasicStroke(float width)
	{
		paint.setMode(PaintMode.STROKE);
		paint.setStrokeWidth(width);
		paint.setStrokeCap(org.jetbrains.skia.PaintStrokeCap.ROUND);
		paint.setStrokeJoin(org.jetbrains.skia.PaintStrokeJoin.ROUND);
	}

	@Override
	public void setStrokeToSolidLineWithNoEndDecorations(float width)
	{
		paint.setMode(PaintMode.STROKE);
		paint.setStrokeWidth(width);
		paint.setStrokeCap(org.jetbrains.skia.PaintStrokeCap.BUTT);
		paint.setStrokeJoin(org.jetbrains.skia.PaintStrokeJoin.ROUND);
	}

	@Override
	public void setStroke(Stroke stroke, double resolutionScale)
	{
		float width = stroke.width * (float) resolutionScale;
		if (stroke.type == StrokeType.Solid)
		{
			setBasicStroke(width);
		}
		else
		{
			float scale = ((float) resolutionScale) * stroke.width;
			float[] intervals;
			if (stroke.type == StrokeType.Dashes)
			{
				intervals = new float[] { 6f * scale, 3f * scale };
				paint.setStrokeCap(org.jetbrains.skia.PaintStrokeCap.BUTT);
			}
			else if (stroke.type == StrokeType.Rounded_Dashes)
			{
				intervals = new float[] { 6f * scale, 4f * scale };
				paint.setStrokeCap(org.jetbrains.skia.PaintStrokeCap.ROUND);
			}
			else if (stroke.type == StrokeType.Dots)
			{
				final float scaleBecauseDotsLookSmallerThanDashes = (3.9f / 2.7f);
				float dotScale = scale * scaleBecauseDotsLookSmallerThanDashes;
				intervals = new float[] { 0f, 2.0f * dotScale };
				paint.setStrokeCap(org.jetbrains.skia.PaintStrokeCap.ROUND);
				width *= scaleBecauseDotsLookSmallerThanDashes;
			}
			else
			{
				throw new UnsupportedOperationException("Unrecognized stroke type: " + stroke.type);
			}
			
			paint.setMode(PaintMode.STROKE);
			paint.setStrokeWidth(width);
			paint.setPathEffect(org.jetbrains.skia.PathEffect.Companion.makeDash(intervals, 0f));
			paint.setStrokeJoin(org.jetbrains.skia.PaintStrokeJoin.ROUND);
		}
	}

	@Override
	public void drawLine(int x1, int y1, int x2, int y2)
	{
		paint.setMode(PaintMode.STROKE);
		canvas.drawLine(x1, y1, x2, y2, paint);
	}

	@Override
	public void drawLine(float x1, float y1, float x2, float y2)
	{
		paint.setMode(PaintMode.STROKE);
		canvas.drawLine(x1, y1, x2, y2, paint);
	}

	@Override
	public void drawOval(int x, int y, int width, int height)
	{
		paint.setMode(PaintMode.STROKE);
		canvas.drawOval(Rect.makeXYWH(x, y, width, height), paint);
	}

	@Override
	public void fillOval(int x, int y, int width, int height)
	{
		paint.setMode(PaintMode.FILL);
		canvas.drawOval(Rect.makeXYWH(x, y, width, height), paint);
	}

	@Override
	public void fillRect(int x, int y, int width, int height)
	{
		paint.setMode(PaintMode.FILL);
		canvas.drawRect(Rect.makeXYWH(x, y, width, height), paint);
	}

	@Override
	public int stringWidth(String string)
	{
		if (font == null) return 0;
		return (int) font.skiaFont.measureTextWidth(string, paint);
	}

	@Override
	public int charWidth(char c)
	{
		if (font == null) return 0;
		return (int) font.skiaFont.measureTextWidth(String.valueOf(c), paint);
	}

	@Override
	public int getFontAscent()
	{
		if (font == null) return 0;
		return (int) -font.skiaFont.getMetrics().getAscent();
	}

	@Override
	public int getFontDescent()
	{
		if (font == null) return 0;
		return (int) font.skiaFont.getMetrics().getDescent();
	}

	@Override
	public void setClip(int x, int y, int width, int height)
	{
		canvas.clipRect(Rect.makeXYWH(x, y, width, height));
	}
}
