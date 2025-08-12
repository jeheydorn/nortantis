package nortantis.swing;

import java.awt.Color;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import nortantis.MapText;
import nortantis.editor.UserPreferences;
import nortantis.geom.Rectangle;
import nortantis.platform.awt.AwtFactory;
import nortantis.util.OSHelper;

@SuppressWarnings("serial")
public class TextSearchDialog extends JDialog
{
	private JTextField searchField;
	private MainWindow mainWindow;
	private JButton searchForward;
	private JButton searchBackward;
	private JLabel notFoundLabel;

	public TextSearchDialog(MainWindow mainWindow)
	{
		super(mainWindow, "Search Text", Dialog.ModalityType.MODELESS);
		setSize(450, 70);
		setResizable(false);

		this.mainWindow = mainWindow;

		JPanel container = new JPanel();
		getContentPane().add(container);
		container.setLayout(new BoxLayout(container, BoxLayout.X_AXIS));
		final int padding = 4;
		container.setBorder(BorderFactory.createEmptyBorder(padding, padding, padding, padding));

		notFoundLabel = new JLabel("Not found");
		notFoundLabel.setForeground(getColorForNotFoundMessage());
		notFoundLabel.setVisible(false);

		searchField = new JTextField();
		container.add(searchField);
		container.add(Box.createRigidArea(new Dimension(padding, 1)));
		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			public void changedUpdate(DocumentEvent e)
			{
				onSearchFieldChanged();
			}

			public void removeUpdate(DocumentEvent e)
			{
				onSearchFieldChanged();
			}

			public void insertUpdate(DocumentEvent e)
			{
				onSearchFieldChanged();
			}
		});

		{
			// Request focus on the text field and select all when CTRL+F is pressed.
			javax.swing.Action ctrlFAction = new javax.swing.AbstractAction()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					requestFocusAndSelectAll();
				}
			};
			getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("control F"), "ctrlF");
			getRootPane().getActionMap().put("ctrlF", ctrlFAction);
		}

		{
			// Save when CTRL+S is pressed.
			javax.swing.Action ctrlSAction = new javax.swing.AbstractAction()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					mainWindow.saveSettings(mainWindow);
				}
			};
			getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("control S"), "ctrlS");
			getRootPane().getActionMap().put("ctrlS", ctrlSAction);
		}

		container.add(notFoundLabel);
		container.add(Box.createRigidArea(new Dimension(padding, 1)));

		boolean needsSpecialHandling = OSHelper.isLinux() && UserPreferences.getInstance().lookAndFeel == LookAndFeel.System;
		final int fontSize = 24;
		searchForward = new JButton(needsSpecialHandling ? "Next" : "→");
		searchForward.setToolTipText("Search forward (enter key)");
		if (!needsSpecialHandling)
		{
			searchForward.setFont(new java.awt.Font(searchForward.getFont().getName(), searchForward.getFont().getStyle(), fontSize));
		}
		getRootPane().setDefaultButton(searchForward);
		container.add(searchForward);
		container.add(Box.createRigidArea(new Dimension(padding, 1)));
		searchForward.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				search(true);
			}
		});

		searchBackward = new JButton(needsSpecialHandling ? "Prev" : "←");
		searchBackward.setToolTipText("Search backward");
		if (!needsSpecialHandling)
		{
			searchBackward.setFont(new java.awt.Font(searchBackward.getFont().getName(), searchBackward.getFont().getStyle(), fontSize));
		}
		container.add(searchBackward);
		searchBackward.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				search(false);
			}
		});
	}
	
	private Color getColorForNotFoundMessage()
	{
		if (UserPreferences.getInstance().lookAndFeel == LookAndFeel.Dark)
		{
			return new Color(255, 120, 120);
		}
		else
		{
			return new Color(255, 0, 0);
		}
	}

	private void onSearchFieldChanged()
	{
		notFoundLabel.setVisible(false);
		setSearchButtonsEnabled(!searchField.getText().isEmpty());
	}

	private void search(boolean isForward)
	{
		if (searchField.getText().isEmpty())
		{
			notFoundLabel.setVisible(false);
			return;
		}

		TextTool textTool = mainWindow.toolsPanel.getTextTool();
		MapText lastSelected = textTool.getTextBeingEdited();
		MapText searchResult = findNext(lastSelected, searchField.getText(), isForward);
		if (searchResult == null)
		{
			notFoundLabel.setVisible(true);
		}
		else
		{
			notFoundLabel.setVisible(false);
			if (mainWindow.toolsPanel.currentTool != textTool)
			{
				mainWindow.toolsPanel.handleToolSelected(textTool);
			}
			textTool.changeToEditModeAndSelectText(searchResult, false);

			if (searchResult.line1Bounds != null)
			{
				// Scroll to make the selected text visible

				Rectangle scrollTo = searchResult.line1Bounds.getBounds();
				if (searchResult.line2Bounds != null)
				{
					scrollTo = scrollTo.add(searchResult.line2Bounds.getBounds());
				}
				double borderPadding = mainWindow.mapEditingPanel.getBorderPadding();
				scrollTo = scrollTo.translate(borderPadding, borderPadding);
				scrollTo = scrollTo.scaleAboutOrigin(mainWindow.zoom * (1.0 / mainWindow.mapEditingPanel.osScale));
				int padding = (int) (250 * (1.0 / mainWindow.mapEditingPanel.osScale));
				scrollTo = scrollTo.pad(padding, padding);

				mainWindow.mapEditingPanel.scrollRectToVisible(AwtFactory.toAwtRectangle(scrollTo));
			}
		}
	}

	private MapText findNext(MapText start, String query, boolean isForward)
	{
		List<MapText> sorted = new ArrayList<>(
				mainWindow.edits.text.stream().filter(t -> t.value != null && !t.value.isEmpty()).collect(Collectors.toList()));
		sorted.sort((text1, text2) ->
		{
			if (text1.location == null && text2.location == null)
			{
				return 0;
			}
			else if (text1.location == null)
			{
				return -1;
			}
			else if (text2.location == null)
			{
				return 1;
			}
			return text1.location.compareTo(text2.location);
		});

		int i = start == null ? (isForward ? sorted.size() - 1 : 0) : sorted.indexOf(start);
		int count = 0;
		while (count < sorted.size())
		{
			if (isForward)
			{
				i++;
				if (i >= sorted.size())
				{
					i = 0;
				}
			}
			else
			{
				i--;
				if (i < 0)
				{
					i = sorted.size() - 1;
				}
			}

			if (sorted.get(i).value.toLowerCase().contains(query.toLowerCase()))
			{
				return sorted.get(i);
			}

			count++;
		}

		if (start != null && start.value.toLowerCase().contains(query.toLowerCase()))
		{
			return start;
		}
		return null;
	}

	boolean allowSearches = true;

	public void setAllowSearches(boolean allow)
	{
		this.allowSearches = allow;
		setSearchButtonsEnabled(allow);
	}

	private void setSearchButtonsEnabled(boolean enable)
	{
		searchForward.setEnabled(enable && allowSearches);
		searchBackward.setEnabled(enable && allowSearches);
	}

	public void requestFocusAndSelectAll()
	{
		searchField.requestFocus();
		searchField.selectAll();
	}
	
	public void handleLookAndFeelChange()
	{
		notFoundLabel.setForeground(getColorForNotFoundMessage());
		SwingUtilities.updateComponentTreeUI(this);
	}
}
