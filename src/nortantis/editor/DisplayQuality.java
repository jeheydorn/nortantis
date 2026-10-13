package nortantis.editor;

import nortantis.swing.translation.Translation;

public enum DisplayQuality
{
	Very_Low(0.5), Low(0.75), Medium(1.0), High(1.25), Ultra(1.5);

	private final double scale;

	DisplayQuality(double scale)
	{
		this.scale = scale;
	}

	/**
	 * The resolution the editor draws the map at with this quality.
	 */
	public double getScale()
	{
		return scale;
	}

	public String toString()
	{
		return Translation.get("DisplayQuality." + name());
	}
}
