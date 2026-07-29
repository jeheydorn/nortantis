package nortantis.swing.translation;

/**
 * Decides where spaces belong when pieces of translated text are joined, so that a seam needing a space in one language does not get an
 * unwanted one in another. Scripts written without spaces between their words, such as Chinese, take no space at a seam, and neither does
 * punctuation that attaches to the text before it.
 */
public class TextSpacing
{
	/**
	 * Joins two pieces of translated text, separating them with a space only where the text on either side calls for one.
	 */
	public static String join(String before, String after)
	{
		return needsSpaceBetween(before, after) ? before + " " + after : before + after;
	}

	/**
	 * Whether a space belongs between two adjacent pieces of text, decided from the characters on either side of the seam.
	 */
	public static boolean needsSpaceBetween(String before, String after)
	{
		if (before.isEmpty() || after.isEmpty())
		{
			return false;
		}

		char endOfBefore = before.charAt(before.length() - 1);
		char startOfAfter = after.charAt(0);
		if (isWrittenWithoutSpaces(endOfBefore) || isWrittenWithoutSpaces(startOfAfter))
		{
			return false;
		}
		return !attachesToPrecedingText(startOfAfter);
	}

	/**
	 * Whether a character belongs with the text before it, such as the period that ends a sentence, so that nothing may separate it from
	 * what it follows.
	 */
	public static boolean attachesToPrecedingText(char c)
	{
		return ".,;:!?%)]}…".indexOf(c) >= 0 || "。，、；：！？）〕】｝」』〉》".indexOf(c) >= 0;
	}

	/**
	 * Whether a character belongs to a script written without spaces between its words, such as Chinese. Such scripts also allow a line to
	 * break between any two characters.
	 */
	public static boolean isWrittenWithoutSpaces(char c)
	{
		if (Character.isIdeographic(c))
		{
			return true;
		}

		Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
		return block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
				|| block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS || block == Character.UnicodeBlock.HIRAGANA
				|| block == Character.UnicodeBlock.KATAKANA;
	}
}
