package nortantis.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSByRef;

import nortantis.geom.Rectangle;
import nortantis.platform.FontStyle;
import nortantis.platform.web.TextRasterizer;
import nortantis.platform.web.WebFont;

/**
 * Draws text with the browser's canvas, through the functions the page provides in {@code window.nortantisText}.
 */
class BrowserTextRasterizer implements TextRasterizer
{
	private final double[] metrics = new double[7];
	private final int[] bounds = new int[4];

	@Override
	public void registerFont(String family, boolean isBold, boolean isItalic, boolean isVariable, byte[] fontFileData)
	{
		String weight = isVariable ? "100 900" : (isBold ? "bold" : "normal");
		registerFontWithBrowser(family, isItalic ? "italic" : "normal", weight, fontFileData);
	}

	@Override
	public int getAscent(WebFont font)
	{
		measure(toCss(font), "", metrics);
		return (int) (0.95 + metrics[5]);
	}

	@Override
	public int getDescent(WebFont font)
	{
		measure(toCss(font), "", metrics);
		return (int) (0.95 + metrics[6]);
	}

	@Override
	public double getAdvance(WebFont font, String text)
	{
		measure(toCss(font), text, metrics);
		return metrics[0];
	}

	@Override
	public Rectangle getVisualBounds(WebFont font, String text)
	{
		measure(toCss(font), text, metrics);
		double left = metrics[1];
		double right = metrics[2];
		double ascent = metrics[3];
		double descent = metrics[4];
		return new Rectangle(-left, -ascent, left + right, ascent + descent);
	}

	@Override
	public TextCoverage rasterize(WebFont font, String text, double x, double y, double[] transform, int deviceWidth, int deviceHeight)
	{
		int pixelCount = rasterizeInBrowser(toCss(font), text, x, y, transform[0], transform[1], transform[2], transform[3], transform[4], transform[5], deviceWidth,
				deviceHeight, bounds);
		if (pixelCount <= 0)
		{
			return null;
		}
		byte[] coverage = new byte[pixelCount];
		copyCoverage(coverage);
		return new TextCoverage(bounds[0], bounds[1], bounds[2], bounds[3], coverage);
	}

	private static String toCss(WebFont font)
	{
		FontStyle style = font.getStyle();
		boolean isItalic = style == FontStyle.Italic || style == FontStyle.BoldItalic;
		boolean isBold = style == FontStyle.Bold || style == FontStyle.BoldItalic;
		return (isItalic ? "italic " : "") + (isBold ? "bold " : "") + font.getSize() + "px \"" + font.getFamily() + "\"";
	}

	@JSBody(params = { "family", "style", "weight", "data" }, script = "nortantisText.registerFont(family, style, weight, data);")
	private static native void registerFontWithBrowser(String family, String style, String weight, @JSByRef byte[] data);

	@JSBody(params = { "font", "text", "out" }, script = "nortantisText.measure(font, text, out);")
	private static native void measure(String font, String text, @JSByRef double[] out);

	@JSBody(params = { "font", "text", "x", "y", "m00", "m10", "m01", "m11", "m02", "m12", "deviceWidth", "deviceHeight", "bounds" },
			script = "return nortantisText.rasterize(font, text, x, y, m00, m10, m01, m11, m02, m12, deviceWidth, deviceHeight, bounds);")
	private static native int rasterizeInBrowser(String font, String text, double x, double y, double m00, double m10, double m01, double m11, double m02,
			double m12, int deviceWidth, int deviceHeight, @JSByRef int[] bounds);

	@JSBody(params = { "destination" }, script = "nortantisText.copyCoverage(destination);")
	private static native void copyCoverage(@JSByRef byte[] destination);
}
