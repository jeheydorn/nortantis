package nortantis.platform.web;

import nortantis.geom.Rectangle;

/**
 * Measures text and turns it into pixel coverage, for platforms whose fonts are drawn by something outside Java, such as a browser.
 */
public interface TextRasterizer
{
	/**
	 * Makes a font file available under the given family name.
	 */
	void registerFont(String family, boolean isBold, boolean isItalic, boolean isVariable, byte[] fontFileData);

	/**
	 * How far the font reaches above the baseline, rounded the way java.awt.FontMetrics.getAscent rounds it.
	 */
	int getAscent(WebFont font);

	/**
	 * How far the font reaches below the baseline, rounded the way java.awt.FontMetrics.getDescent rounds it.
	 */
	int getDescent(WebFont font);

	/**
	 * The distance the text advances the pen, unrounded.
	 */
	double getAdvance(WebFont font, String text);

	/**
	 * The area the text's glyphs put ink on, relative to the point it's drawn at, with y increasing downward.
	 */
	Rectangle getVisualBounds(WebFont font, String text);

	/**
	 * Draws the text with its baseline starting at (x, y), transformed into device space by the given affine transform, and returns the
	 * coverage of each pixel it touches, or null if it touches none inside the given device bounds.
	 *
	 * @param transform
	 *            {m00, m10, m01, m11, m02, m12}, in the order java.awt.geom.AffineTransform.getMatrix gives them.
	 */
	TextCoverage rasterize(WebFont font, String text, double x, double y, double[] transform, int deviceWidth, int deviceHeight);

	/**
	 * Pixel coverage for a block of device pixels starting at (x, y). Each coverage value runs from 0 (untouched) to 255 (fully covered).
	 */
	class TextCoverage
	{
		public final int x;
		public final int y;
		public final int width;
		public final int height;
		public final byte[] coverage;

		public TextCoverage(int x, int y, int width, int height, byte[] coverage)
		{
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
			this.coverage = coverage;
		}
	}
}
