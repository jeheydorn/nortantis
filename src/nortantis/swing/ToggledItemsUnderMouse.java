package nortantis.swing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The items clicks with the command key added to or removed from the selection, each for as long as the mouse stays over it. While it
 * does, the hover preview of what the next such click would do is left off that item, so a click's result isn't immediately replaced by a
 * preview of undoing it. When other items are under the mouse too, the preview and the next click go to them instead.
 *
 * @param <T>
 *            The type of the items, compared by identity.
 */
class ToggledItemsUnderMouse<T>
{
	private final Set<T> items = SelectionEdits.newIdentitySet();

	/**
	 * Records an item a click just toggled, if there is one.
	 */
	void add(T item)
	{
		if (item != null)
		{
			items.add(item);
		}
	}

	void clear()
	{
		items.clear();
	}

	boolean isEmpty()
	{
		return items.isEmpty();
	}

	/**
	 * Forgets the toggled items that aren't among the given items, the ones now under the mouse.
	 */
	void update(Collection<T> itemsUnderMouse)
	{
		Set<T> underMouse = SelectionEdits.newIdentitySet();
		underMouse.addAll(itemsUnderMouse);
		items.retainAll(underMouse);
	}

	/**
	 * Updates from the items under the mouse, then returns those to preview a click on, which leaves out the toggled items.
	 */
	List<T> getItemsToPreview(List<T> itemsUnderMouse)
	{
		update(itemsUnderMouse);
		List<T> result = new ArrayList<>(itemsUnderMouse);
		result.removeIf(items::contains);
		return result;
	}

	/**
	 * Updates from the items under the mouse, then returns those a click can hit, which leave out the toggled items unless they're the only
	 * ones.
	 */
	List<T> getItemsToClick(List<T> itemsUnderMouse)
	{
		List<T> toPreview = getItemsToPreview(itemsUnderMouse);
		return toPreview.isEmpty() ? itemsUnderMouse : toPreview;
	}
}
