package nortantis;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import nortantis.nlp.CharacterNGram;
import nortantis.util.Range;

public class NameGenerator
{
	private CharacterNGram nGram;
	double averageWordLength;
	double maxWordLengthComparedToAverage;
	private double probabilityOfKeepingNameLength1;
	private double probabilityOfKeepingNameLength2;
	private double probabilityOfKeepingNameLength3;
	private Random rand;
	private final String romanNumeralString = "I,II,III,IV,V,VI,VII,VIII,IX,X,XI,XII,XIII,XIV,XV,XVI,XVII,XVIII,XIX,XX";
	private Set<String> romanNumerals;
	
	/**
	 * @param maxWordLengthComparedToAverage Any name generated which contains a word (separated by spaces) which is longer than
	 * maxWordLengthComparedToAverage * averageWordLength will be rejected.
	 * @param probabilityOfKeepingNameLength1 With this probability, words generated with length 1 will be rejected and another sample will be attempted.
	 * @param probabilityOfKeepingNameLength2 With this probability, words generated with length 2 will be rejected and another sample will be attempted.
	 * @param probabilityOfKeepingNameLength3 With this probability, words generated with length 3 will be rejected and another sample will be attempted.
	 */
	public NameGenerator(Random r, List<String> placeNames, double maxWordLengthComparedToAverage, 
			double probabilityOfKeepingNameLength1, double probabilityOfKeepingNameLength2, double probabilityOfKeepingNameLength3)
	{
		this.maxWordLengthComparedToAverage = maxWordLengthComparedToAverage;
		this.probabilityOfKeepingNameLength1 = probabilityOfKeepingNameLength1;
		this.probabilityOfKeepingNameLength2 = probabilityOfKeepingNameLength2;
		this.probabilityOfKeepingNameLength3 = probabilityOfKeepingNameLength3;
		nGram = new CharacterNGram(r, 3);
		rand = r;
		
		// Find the average word length.
		int sum = 0;
		int count = 0;
		for (String name : placeNames)
		{
			sum += name.length();
			count ++;
		}
		averageWordLength = ((double)sum)/count;
		
		// Convert all words to lower case.
		for (int i : new Range(placeNames.size()))
		{
			placeNames.set(i, placeNames.get(i).toLowerCase());
		}
		
		nGram.addData(placeNames);
		
		romanNumerals = new HashSet<String>(Arrays.asList(romanNumeralString.split(",")));
	}

	public String generateName() throws NotEnoughNamesException
	{		
		String name = null;
		String longestWord = null;
		do
		{
			name = nGram.generateNameNotInCorpora();
			longestWord = Collections.max(Arrays.asList(name.split(" ")), new Comparator<String>()
			{
				public int compare(String s1, String s2)
				{
					return Integer.compare(s1.length(), s2.length());
				}
			});
		}
		while ((longestWord.length() > averageWordLength * maxWordLengthComparedToAverage) || isTooShort(name));
		// Capitalize first letter of generated names, including for multi-word names.
		name = capitalizeAllFirstLetters(name);
		name = capitalizeRomanNumerals(name);
	
		return name;
	}
	
	private boolean isTooShort(String name)
	{
		if (name.length() == 1)
		{
			return rand.nextDouble() > probabilityOfKeepingNameLength1;
		}
		if (name.length() == 2)
		{
			return rand.nextDouble() > probabilityOfKeepingNameLength2;
		}
		if (name.length() == 3)
		{
			return rand.nextDouble() > probabilityOfKeepingNameLength3;
		}
		return false;
	}
	
	private String capitalizeAllFirstLetters(String str)
	{
		char[] chars = str.toCharArray();
		for (int i : new Range(0, chars.length))
		{
			if ((i == 0 || chars[i - 1] == ' ') && Character.isLowerCase(chars[i]))
			{
				chars[i] = Character.toUpperCase(str.charAt(i));
			}
		}
		return  String.valueOf(chars);
	}
	
	private String capitalizeRomanNumerals(String str)
	{
		String[] pieces = str.split(" ");
		List<String> piecesList =  Arrays.stream(pieces).map((s) -> romanNumerals.contains(s.toUpperCase()) ? s.toUpperCase() : s).collect(Collectors.toList());
		return String.join(" ", piecesList);
	}
}
