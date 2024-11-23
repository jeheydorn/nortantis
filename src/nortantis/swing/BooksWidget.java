package nortantis.swing;

import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import nortantis.SettingsGenerator;

public class BooksWidget
{
	private JPanel booksPanel;
	private JScrollPane booksScrollPane;
	private JPanel content;

	public BooksWidget(boolean createScrollPane, Runnable actionToRunWhenSelectionChanges)
	{
		booksPanel = createBooksPanel(actionToRunWhenSelectionChanges);

		if (createScrollPane)
		{
			booksScrollPane = new JScrollPane(booksPanel);
			booksScrollPane.getVerticalScrollBar().setUnitIncrement(SwingHelper.sidePanelScrollSpeed);
		}

		JPanel buttonsPanel = new JPanel();
		buttonsPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
		JButton checkAll = new JButton("Check All");
		checkAll.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				checkOrUncheckAllBooks(true);
				actionToRunWhenSelectionChanges.run();
			}
		});

		JButton uncheckAll = new JButton("Uncheck All");
		uncheckAll.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				checkOrUncheckAllBooks(false);
				actionToRunWhenSelectionChanges.run();
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
		content.add(buttonsPanel);
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
