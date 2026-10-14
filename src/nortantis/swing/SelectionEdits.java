package nortantis.swing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * How the modifier keys change a selection in the Edit modes of the editing tools. A click on a single item selects only it, or with the
 * command key held, toggles it. An area (a selection rectangle, or a brush larger than a single point) selects what it covers, or with the
 * command key held, adds it. With both the command key and Alt held, a click or an area removes what it hits.
 *
 * <p>
 * The methods take a factory for empty sets, which decides when two items are the same item: a {@code HashSet} for items compared by
 * equality, or {@link #newIdentitySet()} for items compared by identity. The returned selections keep the order of the selection they
 * started from, followed by the items added.
 */
final class SelectionEdits
{
	private SelectionEdits()
	{
	}

	/**
	 * Whether a click or an area made with these modifiers removes what it hits from the selection.
	 */
	static boolean isRemoving(boolean isCommandKeyDown, boolean isAltDown)
	{
		return isCommandKeyDown && isAltDown;
	}

	/**
	 * An empty set that compares items by identity.
	 */
	static <T> Set<T> newIdentitySet()
	{
		return Collections.newSetFromMap(new IdentityHashMap<>());
	}

	/**
	 * The selection after a click on a single item.
	 *
	 * @param hit
	 *            The item clicked, or null if the click hit nothing.
	 */
	static <T> List<T> clicked(Collection<T> selection, T hit, boolean isCommandKeyDown, boolean isAltDown, Supplier<Set<T>> newSet)
	{
		List<T> result = new ArrayList<>();
		if (!isCommandKeyDown)
		{
			if (hit != null)
			{
				result.add(hit);
			}
			return result;
		}

		result.addAll(selection);
		if (hit == null)
		{
			return result;
		}
		Set<T> selected = newSet.get();
		selected.addAll(selection);
		if (isAltDown || selected.contains(hit))
		{
			Set<T> hitSet = newSet.get();
			hitSet.add(hit);
			result.removeIf(hitSet::contains);
		}
		else
		{
			result.add(hit);
		}
		return result;
	}

	/**
	 * How a selection would change: the items it keeps, the items added to it, and the items removed from it, each in the order of the
	 * selection it comes from.
	 */
	record Change<T>(List<T> kept, List<T> added, List<T> removed)
	{
	}

	/**
	 * The change from the selection before to the selection after.
	 */
	static <T> Change<T> change(Collection<T> before, Collection<T> after, Supplier<Set<T>> newSet)
	{
		Set<T> beforeSet = newSet.get();
		beforeSet.addAll(before);
		Set<T> afterSet = newSet.get();
		afterSet.addAll(after);

		List<T> kept = new ArrayList<>();
		List<T> removed = new ArrayList<>();
		for (T item : before)
		{
			(afterSet.contains(item) ? kept : removed).add(item);
		}
		List<T> added = new ArrayList<>();
		for (T item : after)
		{
			if (!beforeSet.contains(item))
			{
				added.add(item);
			}
		}
		return new Change<>(kept, added, removed);
	}

	/**
	 * The selection after an area covers the given items.
	 *
	 * @param selectionAtStart
	 *            The selection when the area began, which the command key adds to or removes from.
	 */
	static <T> List<T> area(Collection<T> selectionAtStart, Collection<T> covered, boolean isCommandKeyDown, boolean isAltDown, Supplier<Set<T>> newSet)
	{
		if (!isCommandKeyDown)
		{
			return new ArrayList<>(covered);
		}

		List<T> result = new ArrayList<>(selectionAtStart);
		if (isAltDown)
		{
			Set<T> coveredSet = newSet.get();
			coveredSet.addAll(covered);
			result.removeIf(coveredSet::contains);
		}
		else
		{
			Set<T> inResult = newSet.get();
			inResult.addAll(result);
			for (T item : covered)
			{
				if (inResult.add(item))
				{
					result.add(item);
				}
			}
		}
		return result;
	}
}
