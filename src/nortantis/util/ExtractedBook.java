package nortantis.util;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Stores all of the names and other words the map generator needs to generate names.
 */
public class ExtractedBook
{
	public Set<String> placeNames;
	public Set<String> personNames;
	public Set<Tuple2Comp<String, String>> nounAdjectivePairs;
	public Set<Tuple2Comp<String, String>> nounVerbPairs;
	
	public ExtractedBook(Set<String> placeNames, Set<String> personNames,
			Set<Tuple2Comp<String, String>> nounAdjectivePairs, Set<Tuple2Comp<String, String>> nounVerbPairs)
	{
		this.placeNames = placeNames;
		this.personNames = personNames;
		this.nounAdjectivePairs = nounAdjectivePairs;
		this.nounVerbPairs = nounVerbPairs;
	}
	
	public ExtractedBook()
	{
		placeNames = new HashSet<>(); 
		personNames = new HashSet<>();
		nounAdjectivePairs = new TreeSet<>(); 
		nounVerbPairs = new TreeSet<>();
	}
	
	public void addAll(ExtractedBook other)
	{
		placeNames.addAll(other.placeNames);
		personNames.addAll(other.personNames);
		nounAdjectivePairs.addAll(other.nounAdjectivePairs);
		nounVerbPairs.addAll(other.nounVerbPairs);
	}
}
