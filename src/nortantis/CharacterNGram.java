package nortantis;

import nortantis.util.ComparableList;
import nortantis.util.Range;
import nortantis.util.StringCounterMap;

import java.util.*;

/**
 * Used to generate words using character level n-grams.
 * 
 * @author joseph
 *
 */
public class CharacterNGram
{
	int n;
	Random r;
	StringCounterMap scMap;
	Set<String> namesFromCorpora;

	final char startToken = 0;
	final char endToken = 4;

	/**
	 * 
	 * @param r
	 * @param n
	 *            The size of the n-grams. For bi-grams n=2, for tri-grams n=3, etc.
	 */
	public CharacterNGram(Random r, int n)
	{
		this.n = n;
		this.r = r;
		this.scMap = new StringCounterMap();
	}

	public void addData(Collection<String> phrases)
	{
		for (String phrase : phrases)
		{
			for (int i = 0; i < phrase.length(); i++)
			{
				String lastChars = "";
				for (int j = i - n + 1; j < i; j++)
				{
					if (j < 0)
						lastChars += startToken;
					else
						lastChars += phrase.charAt(j);
				}

				scMap.incrementCount(lastChars, phrase.charAt(i));
			}
			// Add the end token.
			String lastChars = "";
			for (int j = phrase.length() - n + 1; j < phrase.length(); j++)
			{
				if (j < 0)
					lastChars += startToken;
				else
					lastChars += phrase.charAt(j);
			}
			scMap.incrementCount(lastChars, endToken);
		}

		namesFromCorpora = new HashSet<>(phrases);
	}

	public String generateNameNotInCorpora(String requiredPrefix) throws NotEnoughNamesException
	{
		final int maxRetries = 20;
		for (@SuppressWarnings("unused")
		int retry : new Range(maxRetries))
		{
			String name = generateName(requiredPrefix);
			if (name.length() < 2)
			{
				continue;
			}
			if (!namesFromCorpora.contains(name))
			{
				// This name never appeared in the corpora.
				return name;
			}
		}

		throw new NotEnoughNamesException();
	}

	private String generateName(String requiredPrefix)
	{
		if (scMap.size() == 0)
			throw new IllegalStateException("At least one book must be selected to generate text.");
		List<Character> lastChars = new ComparableList<>();
		for (@SuppressWarnings("unused")
		int i : new Range(n - 1))
		{
			lastChars.add(startToken);
		}

		for (char c : requiredPrefix.toLowerCase().toCharArray())
		{
			lastChars.remove(0);
			lastChars.add(c);
		}

		String result = requiredPrefix.toLowerCase();

		Character next;
		do
		{

			StringBuilder lc = new StringBuilder(lastChars.size());
			for (Character c : lastChars)
			{
				lc.append(c);
			}

			next = scMap.sampleConditional(r, lc.toString());
			if (next == null)
			{
				throw new NotEnoughNamesException();
			}
			lastChars.remove(0);
			lastChars.add(next);
			if (next != endToken)
			{
				result += next;
			}
		}
		while (next != endToken);

		return result;
	}

	public boolean isEmpty()
	{
		return scMap.size() == 0;
	}

	public static void main(String[] args)
	{
		List<String> strs = Arrays.asList("yellow", "banana", "yellowish", "corn", "corn and rice", "corn without rice", "yellow corn");
		CharacterNGram generator = new CharacterNGram(new Random(), 3);
		generator.addData(strs);
		for (@SuppressWarnings("unused")
		int i : new Range(10))
			System.out.println(generator.generateName(""));
	}
}
