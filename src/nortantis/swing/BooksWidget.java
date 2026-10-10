package nortantis.swing;

import nortantis.SettingsGenerator;
import nortantis.swing.translation.Translation;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Set;
import java.util.TreeSet;

public class BooksWidget
{
	private JPanel booksPanel;
	private JScrollPane booksScrollPane;
	private JPanel content;
	private JPanel buttonsPanel;

	public BooksWidget(boolean createScrollPane, Runnable actionToRunWhenSelectionChanges)
	{
		this(createScrollPane, true, actionToRunWhenSelectionChanges);
	}

	/**
	 * @param putButtonsBelowBooks
	 *            Whether the Check All and Uncheck All buttons go below the books in {@link #getContentPanel()}. When false, they're left out
	 *            of it, for the caller to place from {@link #getButtonsPanel()}.
	 */
	public BooksWidget(boolean createScrollPane, boolean putButtonsBelowBooks, Runnable actionToRunWhenSelectionChanges)
	{
		booksPanel = createBooksPanel(actionToRunWhenSelectionChanges);

		if (createScrollPane)
		{
			booksScrollPane = new JScrollPane(booksPanel);
			booksScrollPane.getVerticalScrollBar().setUnitIncrement(SwingHelper.sidePanelScrollSpeed);
		}

		buttonsPanel = new JPanel();
		buttonsPanel.setLayout(putButtonsBelowBooks ? new FlowLayout(FlowLayout.CENTER) : new FlowLayout(FlowLayout.RIGHT, 5, 0));
		JButton checkAll = new JButton(Translation.get("books.checkAll"));
		checkAll.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				checkOrUncheckAllBooks(true);
				if (actionToRunWhenSelectionChanges != null)
				{
					actionToRunWhenSelectionChanges.run();
				}
			}
		});

		JButton uncheckAll = new JButton(Translation.get("books.uncheckAll"));
		uncheckAll.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				checkOrUncheckAllBooks(false);
				if (actionToRunWhenSelectionChanges != null)
				{
					actionToRunWhenSelectionChanges.run();
				}
			}
		});
		buttonsPanel.add(checkAll);
		buttonsPanel.add(uncheckAll);

		content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		if (createScrollPane)
		{
			content.add(booksScrollPane);
		}
		else
		{
			content.add(booksPanel);
		}
		if (putButtonsBelowBooks)
		{
			content.add(buttonsPanel);
		}
	}

	/**
	 * The Check All and Uncheck All buttons.
	 */
	public JPanel getButtonsPanel()
	{
		return buttonsPanel;
	}

	private JPanel createBooksPanel(Runnable actionToRunWhenSelectionChanges)
	{
		booksPanel = new JPanel();
		booksPanel.setLayout(new BoxLayout(booksPanel, BoxLayout.Y_AXIS));

		createBooksCheckboxes(actionToRunWhenSelectionChanges);

		return booksPanel;
	}

	private void createBooksCheckboxes(Runnable actionToRunWhenSelectionChanges)
	{
		for (String book : SettingsGenerator.getAllBooks())
		{
			final JCheckBox checkBox = new JCheckBox(book);
			booksPanel.add(checkBox);
			if (actionToRunWhenSelectionChanges != null)
			{
				SwingHelper.addListener(checkBox, actionToRunWhenSelectionChanges);
			}
		}
	}

	public void checkSelectedBooks(Set<String> selectedBooks)
	{
		for (Component component : booksPanel.getComponents())
		{
			if (component instanceof JCheckBox)
			{
				JCheckBox checkBox = (JCheckBox) component;
				checkBox.setSelected(selectedBooks.contains(checkBox.getText()));
			}
		}
	}

	private void checkOrUncheckAllBooks(boolean check)
	{
		for (Component component : booksPanel.getComponents())
		{
			if (component instanceof JCheckBox)
			{
				JCheckBox checkBox = (JCheckBox) component;
				checkBox.setSelected(check);
			}
		}
	}

	public Set<String> getSelectedBooks()
	{
		Set<String> books = new TreeSet<>();
		for (Component component : booksPanel.getComponents())
		{
			if (component instanceof JCheckBox)
			{
				JCheckBox checkBox = (JCheckBox) component;
				if (checkBox.isSelected())
				{
					books.add(checkBox.getText());
				}
			}
		}

		return books;
	}

	public JPanel getContentPanel()
	{
		return content;
	}
}
