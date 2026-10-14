package nortantis.swing;

/**
 * The item a click with the command key just added to or removed from the selection, for as long as the mouse stays over it. While it does,
 * the hover preview of what the next such click would do is left off that item, so the click's result isn't immediately replaced by a
 * preview of undoing it.
 *
 * @param <T>
 *            The type of the items, compared by identity.
 */
class ToggledItemUnderMouse<T>
{
	private T item;

	void set(T item)
	{
		this.item = item;
	}

	void clear()
	{
		item = null;
	}

	boolean isSet()
	{
		return item != null;
	}

	/**
	 * Forgets the toggled item if the given item, the one now under the mouse, is a different one.
	 */
	void update(T underMouse)
	{
		if (underMouse != item)
		{
			item = null;
		}
	}

	/**
	 * Updates from the item under the mouse, then returns that item to preview a click on, which is null while it's the toggled item.
	 */
	T getItemToPreview(T underMouse)
	{
		update(underMouse);
		return underMouse == item ? null : underMouse;
	}
}
