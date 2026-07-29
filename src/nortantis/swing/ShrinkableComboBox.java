package nortantis.swing;

import javax.swing.JComboBox;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Insets;

/**
 * A combo box that layout managers are allowed to make narrower than the width of its widest item. When that happens, the look and feel
 * draws the selected item's text with a trailing ellipsis. Items in the popup list are never truncated, so opening the popup always shows
 * the full text of every item.
 * <p>
 * A plain JComboBox reports a minimum width wide enough to show its widest item, which leaves layout managers no choice but to give it
 * that much room or overflow their container. That is a problem when a combo box holds names of unbounded length, such as art pack or icon
 * group names, and shares a row with other components.
 */
@SuppressWarnings("serial")
public class ShrinkableComboBox<T> extends JComboBox<T>
{
	/**
	 * Roughly how many characters of the selected item remain visible when this combo box is shrunk as far as it will go.
	 */
	private static final int minimumVisibleCharacterCount = 6;

	public ShrinkableComboBox()
	{
		addPopupMenuListener(new PopupMenuListener()
		{
			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent event)
			{
				// Wait until the look and feel has laid the popup out, since its choices are what the width below is measured against.
				SwingUtilities.invokeLater(() -> growPopupToFitItemsIfNeeded());
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent event)
			{
				clearPopupSizeOverrides();
			}

			@Override
			public void popupMenuCanceled(PopupMenuEvent event)
			{
			}
		});
	}

	@Override
	public Dimension getMinimumSize()
	{
		Dimension minimum = super.getMinimumSize();
		Font font = getFont();
		if (isMinimumSizeSet() || font == null)
		{
			return minimum;
		}

		FontMetrics metrics = getFontMetrics(font);
		int widthOfPartsOtherThanText = Math.max(0, minimum.width - getWidestItemTextWidth(metrics));
		int shrunkWidth = widthOfPartsOtherThanText + (metrics.charWidth('n') * minimumVisibleCharacterCount);
		return new Dimension(Math.min(minimum.width, shrunkWidth), minimum.height);
	}

	/**
	 * Grows the popup, when it is not already wide enough, so that the full text of every item is visible. Some look and feels make the
	 * popup exactly as wide as the combo box, which would truncate the very names the popup was opened to read.
	 */
	private void growPopupToFitItemsIfNeeded()
	{
		JPopupMenu popup = getPopup();
		JScrollPane scrollPane = getPopupScrollPane(popup);
		if (scrollPane == null || !popup.isShowing() || popup.getWidth() <= 0 || popup.getHeight() <= 0)
		{
			return;
		}

		Component list = scrollPane.getViewport().getView();
		if (list == null)
		{
			return;
		}

		// The scroll pane's border plus, when there are enough items to need one, its scroll bar.
		int scrollPaneWidthOtherThanList = scrollPane.getWidth() - scrollPane.getViewport().getWidth();
		Insets popupInsets = popup.getInsets();
		int targetWidth = list.getPreferredSize().width + scrollPaneWidthOtherThanList + popupInsets.left + popupInsets.right;
		if (targetWidth <= popup.getWidth())
		{
			return;
		}

		// Look and feels pin the scroll pane's size to the combo box's width while showing the popup, so widening the popup alone would
		// leave the list at its old width.
		Dimension scrollPaneSize = new Dimension(targetWidth - popupInsets.left - popupInsets.right, scrollPane.getHeight());
		scrollPane.setMinimumSize(scrollPaneSize);
		scrollPane.setPreferredSize(scrollPaneSize);
		scrollPane.setMaximumSize(scrollPaneSize);
		popup.setPopupSize(targetWidth, popup.getHeight());
	}

	/**
	 * Drops the sizes {@code growPopupToFitItemsIfNeeded} set, which JPopupMenu and JScrollPane hold onto as explicitly set sizes. Without
	 * this, a popup that was widened to fit one art pack's names would stay that wide for every art pack chosen afterwards.
	 */
	private void clearPopupSizeOverrides()
	{
		JPopupMenu popup = getPopup();
		JScrollPane scrollPane = getPopupScrollPane(popup);
		if (scrollPane == null)
		{
			return;
		}

		popup.setPreferredSize(null);
		scrollPane.setMinimumSize(null);
		scrollPane.setPreferredSize(null);
		scrollPane.setMaximumSize(null);
	}

	private JPopupMenu getPopup()
	{
		return getUI().getAccessibleChild(this, 0) instanceof JPopupMenu popup ? popup : null;
	}

	/**
	 * @return The scroll pane holding the popup's list, or null when this look and feel does not build its popup that way.
	 */
	private JScrollPane getPopupScrollPane(JPopupMenu popup)
	{
		if (popup == null || popup.getComponentCount() == 0 || !(popup.getComponent(0) instanceof JScrollPane scrollPane))
		{
			return null;
		}
		return scrollPane;
	}

	private int getWidestItemTextWidth(FontMetrics metrics)
	{
		int result = 0;
		for (int i = 0; i < getItemCount(); i++)
		{
			T item = getItemAt(i);
			if (item != null)
			{
				result = Math.max(result, metrics.stringWidth(item.toString()));
			}
		}
		return result;
	}
}
