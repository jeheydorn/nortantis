package nortantis.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import nortantis.MapText;

public class TextSearchDialog extends JDialog
{
	public JTextField searchField;
	private MainWindow mainWindow;
	private JButton searchForward;
	private JButton searchBackward;

	// TODO Null out MainWindow.textSearchDialog when closing.

	public TextSearchDialog(MainWindow mainWindow)
	{
		super(mainWindow, "Search Text", Dialog.ModalityType.MODELESS);
		setSize(450, 70);

		this.mainWindow = mainWindow;

		JPanel container = new JPanel();
		getContentPane().add(container);
		container.setLayout(new BoxLayout(container, BoxLayout.X_AXIS));
		final int padding = 4;
		container.setBorder(BorderFactory.createEmptyBorder(padding, padding, padding, padding));

		JLabel notFoundLabel = new JLabel("Not found");
		notFoundLabel.setForeground(new Color(255, 120, 120));
		notFoundLabel.setVisible(false);

		searchField = new JTextField();
		container.add(searchField);
		container.add(Box.createRigidArea(new Dimension(padding, 1)));
		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			public void changedUpdate(DocumentEvent e)
			{
				notFoundLabel.setVisible(false);
			}

			public void removeUpdate(DocumentEvent e)
			{
				notFoundLabel.setVisible(false);
			}

			public void insertUpdate(DocumentEvent e)
			{
				notFoundLabel.setVisible(false);
			}
		});
		
		container.add(notFoundLabel);
		container.add(Box.createRigidArea(new Dimension(padding, 1)));

		final int fontSize = 24;
		searchForward = new JButton("→");
		searchForward.setToolTipText("Search forward (enter key)");
		searchForward.setFont(new java.awt.Font(searchForward.getFont().getName(), searchForward.getFont().getStyle(), fontSize));
		getRootPane().setDefaultButton(searchForward);
		container.add(searchForward);
		container.add(Box.createRigidArea(new Dimension(padding, 1)));
		searchForward.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (searchField.getText().isEmpty())
				{
					notFoundLabel.setVisible(false);
					return;
				}
				
				MapText lastSelected = mainWindow.toolsPanel.currentTool instanceof TextTool
						? ((TextTool) mainWindow.toolsPanel.currentTool).getTextBeingEdited()
						: null;
				MapText searchResult = findNext(lastSelected, searchField.getText());
				if (searchResult == null)
				{
					notFoundLabel.setVisible(true);
				}
				else
				{
					notFoundLabel.setVisible(false);
					TextTool textTool = mainWindow.toolsPanel.getTextTool();
					if (mainWindow.toolsPanel.currentTool != textTool)
					{
						mainWindow.toolsPanel.handleToolSelected(textTool);	
					}
					textTool.handleSelectingTextToEdit(searchResult, false);
					
				}
			}
		});

		searchBackward = new JButton("←");
		searchBackward.setToolTipText("Search backward");
		searchBackward.setFont(new java.awt.Font(searchBackward.getFont().getName(), searchBackward.getFont().getStyle(), fontSize));
		container.add(searchBackward);
	}

	private MapText findNext(MapText start, String query)
	{
		List<MapText> sorted = new ArrayList<>(mainWindow.edits.text);
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

		int startIndex = start == null ? 0 : sorted.indexOf(start) + 1;
		if (startIndex > sorted.size() - 1)
		{
			startIndex = 0;
		}
		for (int i = startIndex; i < sorted.size(); i++)
		{
			if (sorted.get(i).value.toLowerCase().contains(query.toLowerCase()))
			{
				return sorted.get(i);
			}
		}
		return null;
	}
	
	public void setSearchButtonsEnabled(boolean enable)
	{
		searchForward.setEnabled(enable);
		searchBackward.setEnabled(enable);
	}
}
