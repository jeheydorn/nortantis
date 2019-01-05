package nortantis;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import nortantis.util.AssetsPath;
import nortantis.util.Counter;
import nortantis.util.Function;
import nortantis.util.Helper;
import nortantis.util.Pair;
import nortantis.util.Range;

/**
 * Creates names for rivers and mountains by putting nouns, verbs, and adjectives together.
 * @author joseph
 *
 */
public class NameCompiler
{
	// The first part of each pair is the noun.
	List<Pair<String>> nounAdjectivePairs;
	List<Pair<String>> nounVerbPairs;
	// Used to decide whether to return a result from nounAdjectivePairs or nounVerbPairs.
	private Counter<String> counter;
	Random r;
	public void setSeed(long seed)
	{
		r.setSeed(seed);
	}
	private Set<String> dict;

	public NameCompiler(Random r, List<Pair<String>> nounAdjectivePairs, 
			List<Pair<String>> nounVerbPairs)
	{		
		// Load the word dictionary.
		List<String> lines;
		try
		{
			lines = Files.readAllLines(Paths.get(AssetsPath.get(), "internal/en_GB.dic"), Charset.defaultCharset());
		} catch (IOException e)
		{
			throw new RuntimeException("Unable to read word dictionary file.", e);
		}
		dict = new TreeSet<>();
		for (String line : lines)
		{
			String[] parts = line.split("[\\s0-9/]");
			if (parts.length == 0)
				continue;
			String word = parts[0];
			word = word.trim();
			dict.add(word);
		}

		this.nounVerbPairs = convertToPresentTense(nounVerbPairs);
		nounVerbPairs = null;

		// Make all first letters capital.
		this.nounAdjectivePairs = capitalizeFirstLetters(nounAdjectivePairs);
		this.nounVerbPairs = capitalizeFirstLetters(this.nounVerbPairs);
		nounAdjectivePairs = null;
		
				
		this.r = r;
		counter = new Counter<>();
		counter.addCount("adjectives", this.nounAdjectivePairs.size());
		counter.addCount("verbs", this.nounVerbPairs.size());
				
		
	}
	
	private List<Pair<String>> convertToPresentTense(List<Pair<String>> verbPairs)
	{
		// Convert verbs to present tense.
		List<Pair<String>> result = new ArrayList<>();
		for (int i : new Range(verbPairs.size()))
		{
			String verb = verbPairs.get(i).getSecond();
			String presentTenseVerb = convertVerbToPresentTense(verb);
			result.add(new Pair<>(verbPairs.get(i).getFirst(), presentTenseVerb));
		}
		return result;
	}
	
	private List<Pair<String>> capitalizeFirstLetters(List<Pair<String>> pairs)
	{
		List<Pair<String>> result = new ArrayList<>();
		for (int i : new Range(pairs.size()))
		{
			String noun = capitalizeAllFirstLetter(pairs.get(i).getFirst());
			String pos = capitalizeAllFirstLetter(pairs.get(i).getSecond());
			result.add(new Pair<>(noun, pos));
		}
		return result;
	}
	
	private String capitalizeAllFirstLetter(String str)
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
	
	public String compileName()
	{
		if (counter.sample(r).equals("adjectives"))
		{
			if (nounAdjectivePairs.size() == 0)
			{
				return "";
			}
			Pair<String> pair = nounAdjectivePairs.get(r.nextInt(nounAdjectivePairs.size()));
			double d = r.nextDouble();
			String result;
			if (d < 1.0/3.0)
			{
				// Just return the noun.
				result = pair.getFirst();
			}
			else if (d < 2.0/3.0)
			{
				// Just return the adjective.
				result = pair.getSecond();
			}
			else
			{
				// Return both.
				result = pair.getSecond() + " " + pair.getFirst();
			}
			return result;
		}
		else
		{
			if (nounVerbPairs.size() == 0)
			{
				return "";
			}
			Pair<String> pair = nounVerbPairs.get(r.nextInt(nounVerbPairs.size()));
			double d = r.nextDouble();
			String result;
			if (d < 0.5) 
			{
				// Just return the noun.
				result = pair.getFirst();
			}
			else
			{
				// Return both.
				result = pair.getSecond() + " " + pair.getFirst();
			}			
			
			return result;
		}
	}
	
	/**
	 * Use rules from http://www.oxforddictionaries.com/us/words/verb-tenses-adding-ed-and-ing
	 * and some rules I made to convert a verb to present tense.
	 * @param verb
	 * @return
	 */
	private String convertVerbToPresentTense(String verb)
	{
		List<Character> vowels = Arrays.asList('a', 'e', 'i', 'o', 'u');
		
		if (verb.endsWith("ing"))
			return verb;
		
		if (verb.endsWith("ee") || verb.endsWith("ye") || verb.endsWith("oe"))
		{
			// Keep silent e.
			return verb + "ing";
		}
		
		if (verb.endsWith("ed"))
		{
			return verb.substring(0, verb.length() - 2) + "ing";
		}
		
		
		if (verb.endsWith("aid"))
		{
			return verb.substring(0, verb.length() - 2) + "ying";
		}
		
		if (verb.endsWith("ood"))
		{
			return verb.substring(0, verb.length() - 3) + "anding";
		}

		if (verb.endsWith("ave"))
		{
			return verb.substring(0, verb.length() - 3) + "iving";
		}

		if (verb.endsWith("een"))
		{
			return verb.substring(0, verb.length() - 1) + "ing";
		}

		if (verb.endsWith("e") && dict.contains(verb.substring(0, verb.length() - 1) + "ing"))
		{
			return verb.substring(0, verb.length() - 1) + "ing";
		}
		
		if (verb.endsWith("ought"))
		{
			if (dict.contains(verb + "ing"))
			{
				return verb + "ing";
			}
			// Give up.
			return verb;
		}

		if (verb.length() >= 3 && 
				!vowels.contains(verb.charAt(verb.length() - 1)) && vowels.contains(verb.charAt(verb.length() - 2))
				&& vowels.contains(verb.charAt(verb.length() - 3)))
		{
			// 2 vowels vowels by a consonant.
			return verb + "ing";
		}
		
		if (verb.endsWith("c"))
		{
			return verb + "king";
		}
		
		if (verb.length() >= 2 &&
				!vowels.contains(verb.charAt(verb.length() - 1)) && vowels.contains(verb.charAt(verb.length() - 2)))
		{
			// Use a massive dictionary to determine if I should double the consonant.
			if (dict.contains(verb + "ing"))
					return verb + "ing";
			char consonant = verb.charAt(verb.length() - 1);
			if (dict.contains(verb + consonant + "ing"))
					return verb + consonant + "ing";
			// Give up.
			return verb;
		}
		
		if (verb.endsWith("ept"))
		{
			return verb.substring(0, verb.length() - 2) + "eping";
		}
						
		if (dict.contains(verb + "ing"))
			return verb + "ing";
		// Give up.
		return verb;
	}
	
	public static void test()
	{
		final NameCompiler compiler = new NameCompiler(new Random(), new ArrayList<Pair<String>>(),
				new ArrayList<Pair<String>>());
		// My examples.
		{
			List<String> before = Arrays.asList(
					"travel",
					"distil",
					"equal",
					"bake",
					"free",
					"dye",
					"tiptoe",
					"running",
					"wheel",
					"picnic",
					"stood",
					"forgave",
					"seen",
					"set");
			List<String> after = Helper.map(before, new Function<String, String>()
					{
						public String apply(String item)
						{
							return compiler.convertVerbToPresentTense(item);
						}
					});
			List<String> expected = Arrays.asList(
					"travelling", // I'm using a British dictionary apparently.
					"distilling",
					"equaling",
					"baking",
					"freeing",
					"dyeing",
					"tiptoeing",
					"running",
					"wheeling",
					"picnicking",
					"standing",
					"forgiving",
					"seeing",
					"setting");
			for (int i : new Range(expected.size()))
			{
				assertEquals(expected.get(i), after.get(i));
			}
		}
		
		// Examples from text.
		{
			List<String> before = Arrays.asList(
					"redeem",
					"stretched",
					"set",
					"wept",
					"appeared",
					"rid",
					"plucked",
					"put",
					"laid",
					"stand",
					"send",
					"speak",
					"afflict",
					"looked",
					"rest");
			List<String> after = Helper.map(before, new Function<String, String>()
					{
						public String apply(String item)
						{
							return compiler.convertVerbToPresentTense(item);
						}			
					});
			List<String> expected = Arrays.asList(
					"redeeming",
					"stretching",
					"setting",
					"weeping",
					"appearing",
					"riding",
					"plucking",
					"putting",
					"laying",
					"standing",
					"sending",
					"speaking",
					"afflicting",
					"looking",
					"resting");
			for (int i : new Range(expected.size()))
			{
				assertEquals(expected.get(i), after.get(i));
			}

		}
	}

}
