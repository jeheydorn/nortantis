package nortantis.platform;

public enum FontStyle
{
	Plain(0), Bold(1), Italic(2), BoldItalic(3);

	public final int value;
	private FontStyle(int value)
	{
		this.value = value;
	}

	public static FontStyle fromNumber(int number)
	{
		if (number == Plain.value)
		{
			return Plain;
		}
		if (number == Bold.value)
		{
			return Bold;
		}
		if (number == Italic.value)
		{
			return Italic;
		}
		if (number == BoldItalic.value)
		{
			return BoldItalic;
		}
		throw new IllegalArgumentException("Unrecognized font style number: " + number);
	}
}
