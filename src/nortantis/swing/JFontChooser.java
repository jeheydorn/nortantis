/************************************************************
 * Copyright 2004-2005,2007-2008 Masahiko SAWAI All Rights Reserved. 
 ************************************************************/
package nortantis.swing;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import nortantis.FontFinder;
import nortantis.FontFinder.AvailableFont;
import nortantis.FontFinder.Script;
import nortantis.swing.translation.Translation;

/**
 * The <code>JFontChooser</code> class is a swing component for font selection. This class has <code>JFileChooser</code> like APIs. The
 * following code pops up a font chooser dialog.
 * 
 * <pre>
 * JFontChooser fontChooser = new JFontChooser(); int result = fontChooser.showDialog(parent); if (result == JFontChooser.OK_OPTION) { Font
 * font = fontChooser.getSelectedFont(); System.out.println("Selected Font : " + font); }
 * 
 * <pre>
 **/
@SuppressWarnings("serial")
public class JFontChooser extends JComponent
{
	// class variables
	/**
	 * Return value from <code>showDialog()</code>.
	 * 
	 * @see #showDialog
	 **/
	public static final int OK_OPTION = 0;
	/**
	 * Return value from <code>showDialog()</code>.
	 * 
	 * @see #showDialog
	 **/
	public static final int CANCEL_OPTION = 1;
	/**
	 * Return value from <code>showDialog()</code>.
	 * 
	 * @see #showDialog
	 **/
	public static final int ERROR_OPTION = -1;
	private static final Font DEFAULT_SELECTED_FONT = new Font("Serif", Font.PLAIN, 12);
	private static final Font DEFAULT_FONT = new Font("Dialog", Font.PLAIN, 10);
	private static final int[] FONT_STYLE_CODES = { Font.PLAIN, Font.BOLD, Font.ITALIC, Font.BOLD | Font.ITALIC };
	private static final String[] DEFAULT_FONT_SIZE_STRINGS = { "8", "9", "10", "11", "12", "14", "16", "18", "20", "22", "24", "26", "28", "36", "48", "72", "96", "120", "144", "168", "192", "216",
			"240", };
	private static final int sampleWidth = 300;
	/**
	 * The height the sample opens at. It does not change with the font being previewed: the sizes go up to 240 point, which no sample the
	 * dialog could hold would show whole anyway, and a sample that resized itself would rearrange the dialog every time a size was clicked.
	 * Someone who wants to see more of a large font makes the dialog taller, which the sample does grow with.
	 */
	private static final int sampleHeight = 100;
	/** The share of the height the dialog gains from being resized that goes to the lists rather than to the sample. */
	private static final double listShareOfAddedHeight = 0.75;
	/** The size each family name is drawn at in its own font, in the family list. */
	private static final int familyPreviewFontSize = 14;
	/**
	 * The size of a row in the family list. Fixing it is what keeps the list from measuring every row in order to lay itself out, and
	 * measuring a row means loading the font it is drawn in - so without this, opening the picker loads every font on the machine before it
	 * can show anything, which takes about a second. Rows are drawn at {@link #familyPreviewFontSize}, and a family whose line box is taller
	 * than the height loses only leading, not glyphs.
	 *
	 * <p>
	 * The width is a floor, not the width a row is drawn at: a list stretches its rows to its viewport whenever they would otherwise be
	 * narrower, so keeping this well under the width of the panel is what puts the right edge of every row where it can be seen.
	 */
	private static final int familyRowHeight = 22;
	private static final int minimumFamilyRowWidth = 120;
	/** The height of the panels holding the family, style, and size lists. */
	private static final int listPanelHeight = 180;

	// instance variables
	protected int dialogResultValue = ERROR_OPTION;

	private String[] fontStyleNames = null;
	private String[] fontSizeStrings = null;

	/**
	 * The chosen family. Selection is held here rather than read back from the list because one family can occupy several rows - a font the
	 * map uses is listed both under what it is used by and under where it came from - and because a search can hide the row it is on
	 * without changing what is chosen.
	 */
	private String selectedFamily;
	/** The family the dialog opened on. */
	private String familyFromSettings;
	/** Families this map draws with, listed first so that matching one field to another does not mean hunting for it. */
	private List<String> familiesUsedByThisMap = new ArrayList<>();
	/** What has been typed to narrow the list, or empty to show every family. */
	private String searchText = "";
	/** The distinct characters the chosen font has to be able to draw, used to mark families that cannot draw them. */
	private String charactersThatMustBeDrawable = "";
	private final Map<String, Script> missingScriptByFamily = new HashMap<>();
	/** True while the family list's contents are being replaced, when the selection changes for reasons the user did not cause. */
	private boolean isRebuildingFamilyList;
	/** Which way the selection was last moving, so that arrowing onto a heading carries on in the same direction. */
	private int previousSelectedIndex = -1;
	private JTextField fontFamilyTextField = null;
	private JTextField fontStyleTextField = null;
	private JTextField fontSizeTextField = null;
	private JList<?> fontNameList = null;
	private JList<?> fontStyleList = null;
	private JList<?> fontSizeList = null;
	private JPanel fontNamePanel = null;
	private JPanel fontStylePanel = null;
	private JPanel fontSizePanel = null;
	private JPanel samplePanel = null;
	private JTextField sampleText = null;

	/**
	 * Constructs a <code>JFontChooser</code> object.
	 **/
	public JFontChooser()
	{
		this(DEFAULT_FONT_SIZE_STRINGS);
	}

	/**
	 * Constructs a <code>JFontChooser</code> object using the given font size array.
	 * 
	 * @param fontSizeStrings
	 *            the array of font size string.
	 **/
	public JFontChooser(String[] fontSizeStrings)
	{
		if (fontSizeStrings == null)
		{
			fontSizeStrings = DEFAULT_FONT_SIZE_STRINGS;
		}
		this.fontSizeStrings = fontSizeStrings;

		JPanel selectPanel = new JPanel();
		selectPanel.setLayout(new BoxLayout(selectPanel, BoxLayout.X_AXIS));
		selectPanel.add(getFontFamilyPanel());
		selectPanel.add(getFontStylePanel());
		selectPanel.add(getFontSizePanel());

		// Both rows open at the height they ask for, and height the dialog is given beyond that is split by weight rather than evenly, so
		// that making the dialog taller mostly lengthens the lists.
		JPanel contentsPanel = new JPanel(new GridBagLayout());
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.fill = GridBagConstraints.BOTH;
		constraints.weightx = 1.0;
		constraints.gridy = 0;
		constraints.weighty = listShareOfAddedHeight;
		contentsPanel.add(selectPanel, constraints);
		constraints.gridy = 1;
		constraints.weighty = 1.0 - listShareOfAddedHeight;
		contentsPanel.add(getSamplePanel(), constraints);

		this.setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		this.add(contentsPanel);
		this.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		this.setSelectedFont(DEFAULT_SELECTED_FONT);
	}

	public JTextField getFontFamilyTextField()
	{
		if (fontFamilyTextField == null)
		{
			fontFamilyTextField = new JTextField();
			fontFamilyTextField.addKeyListener(new TextFieldKeyHandlerForListSelectionUpDown(getFontFamilyList()));
			fontFamilyTextField.getDocument().addDocumentListener(new FamilySearchHandler());
			fontFamilyTextField.setFont(DEFAULT_FONT);
			fontFamilyTextField.setToolTipText(Translation.get("fontChooser.search"));
		}
		return fontFamilyTextField;
	}

	public JTextField getFontStyleTextField()
	{
		if (fontStyleTextField == null)
		{
			fontStyleTextField = new JTextField();
			fontStyleTextField.addFocusListener(new TextFieldFocusHandlerForTextSelection(fontStyleTextField));
			fontStyleTextField.addKeyListener(new TextFieldKeyHandlerForListSelectionUpDown(getFontStyleList()));
			fontStyleTextField.getDocument().addDocumentListener(new ListSearchTextFieldDocumentHandler(getFontStyleList()));
			fontStyleTextField.setFont(DEFAULT_FONT);
		}
		return fontStyleTextField;
	}

	public JTextField getFontSizeTextField()
	{
		if (fontSizeTextField == null)
		{
			fontSizeTextField = new JTextField();
			fontSizeTextField.addFocusListener(new TextFieldFocusHandlerForTextSelection(fontSizeTextField));
			fontSizeTextField.addKeyListener(new TextFieldKeyHandlerForListSelectionUpDown(getFontSizeList()));
			fontSizeTextField.getDocument().addDocumentListener(new ListSearchTextFieldDocumentHandler(getFontSizeList()));
			fontSizeTextField.setFont(DEFAULT_FONT);
		}
		return fontSizeTextField;
	}

	public JList<?> getFontFamilyList()
	{
		if (fontNameList == null)
		{
			fontNameList = new JList<Object>(buildFontFamilyRows());
			fontNameList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			fontNameList.setCellRenderer(new FontFamilyRenderer());
			fontNameList.setFixedCellHeight(familyRowHeight);
			fontNameList.setFixedCellWidth(minimumFamilyRowWidth);
			fontNameList.addListSelectionListener(new FamilySelectionHandler());
			fontNameList.setFont(DEFAULT_FONT);
			fontNameList.setFocusable(false);
		}
		return fontNameList;
	}

	public JList<?> getFontStyleList()
	{
		if (fontStyleList == null)
		{
			fontStyleList = new JList<Object>(getFontStyleNames());
			fontStyleList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			fontStyleList.addListSelectionListener(new ListSelectionHandler(getFontStyleTextField()));
			fontStyleList.setSelectedIndex(0);
			fontStyleList.setFont(DEFAULT_FONT);
			fontStyleList.setFocusable(false);
		}
		return fontStyleList;
	}

	public JList<?> getFontSizeList()
	{
		if (fontSizeList == null)
		{
			fontSizeList = new JList<Object>(this.fontSizeStrings);
			fontSizeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			fontSizeList.addListSelectionListener(new ListSelectionHandler(getFontSizeTextField()));
			fontSizeList.setSelectedIndex(0);
			fontSizeList.setFont(DEFAULT_FONT);
			fontSizeList.setFocusable(false);
		}
		return fontSizeList;
	}

	/**
	 * Get the family name of the selected font.
	 * 
	 * @return the font family of the selected font.
	 *
	 * @see #setSelectedFontFamily
	 **/
	public String getSelectedFontFamily()
	{
		return selectedFamily != null ? selectedFamily : familyFromSettings;
	}

	/**
	 * Get the style of the selected font.
	 * 
	 * @return the style of the selected font. <code>Font.PLAIN</code>, <code>Font.BOLD</code>, <code>Font.ITALIC</code>,
	 *         <code>Font.BOLD|Font.ITALIC</code>
	 *
	 * @see java.awt.Font#PLAIN
	 * @see java.awt.Font#BOLD
	 * @see java.awt.Font#ITALIC
	 * @see #setSelectedFontStyle
	 **/
	public int getSelectedFontStyle()
	{
		int index = getFontStyleList().getSelectedIndex();
		return FONT_STYLE_CODES[index];
	}

	/**
	 * Get the size of the selected font.
	 * 
	 * @return the size of the selected font
	 *
	 * @see #setSelectedFontSize
	 **/
	public int getSelectedFontSize()
	{
		int fontSize = 1;
		String fontSizeString = getFontSizeTextField().getText();
		while (true)
		{
			try
			{
				fontSize = Integer.parseInt(fontSizeString);
				break;
			}
			catch (NumberFormatException e)
			{
				fontSizeString = (String) getFontSizeList().getSelectedValue();
				getFontSizeTextField().setText(fontSizeString);
			}
		}

		return fontSize;
	}

	/**
	 * Get the selected font.
	 * 
	 * @return the selected font
	 *
	 * @see #setSelectedFont
	 * @see java.awt.Font
	 **/
	public Font getSelectedFont()
	{
		Font font = new Font(getSelectedFontFamily(), getSelectedFontStyle(), getSelectedFontSize());
		return font;
	}

	/**
	 * Set the family name of the selected font.
	 * 
	 * @param name
	 *            the family name of the selected font.
	 **/
	public void setSelectedFontFamily(String name)
	{
		familyFromSettings = name;
		selectedFamily = name;
		rebuildFontFamilyList();
		scrollSelectedFamilyIntoView();
		updateSampleFont();
	}

	/**
	 * The families this map draws with, which the list shows first. A family the map uses but this machine does not have belongs here too:
	 * it is the only group it can appear under, and leaving it out would make the picker look like it had dropped the map's font.
	 */
	public void setFamiliesUsedByThisMap(List<String> families)
	{
		familiesUsedByThisMap = families == null ? new ArrayList<>() : new ArrayList<>(families);
		rebuildFontFamilyList();
		scrollSelectedFamilyIntoView();
	}

	/**
	 * Set the style of the selected font.
	 * 
	 * @param style
	 *            the size of the selected font. <code>Font.PLAIN</code>, <code>Font.BOLD</code>, <code>Font.ITALIC</code>, or
	 *            <code>Font.BOLD|Font.ITALIC</code>.
	 *
	 * @see java.awt.Font#PLAIN
	 * @see java.awt.Font#BOLD
	 * @see java.awt.Font#ITALIC
	 * @see #getSelectedFontStyle
	 **/
	public void setSelectedFontStyle(int style)
	{
		for (int i = 0; i < FONT_STYLE_CODES.length; i++)
		{
			if (FONT_STYLE_CODES[i] == style)
			{
				getFontStyleList().setSelectedIndex(i);
				break;
			}
		}
		updateSampleFont();
	}

	/**
	 * Set the size of the selected font.
	 * 
	 * @param size
	 *            the size of the selected font
	 *
	 * @see #getSelectedFontSize
	 **/
	public void setSelectedFontSize(int size)
	{
		String sizeString = String.valueOf(size);
		for (int i = 0; i < this.fontSizeStrings.length; i++)
		{
			if (this.fontSizeStrings[i].equals(sizeString))
			{
				getFontSizeList().setSelectedIndex(i);
				break;
			}
		}
		getFontSizeTextField().setText(sizeString);
		updateSampleFont();
	}

	/**
	 * Set the selected font.
	 * 
	 * @param font
	 *            the selected font
	 *
	 * @see #getSelectedFont
	 * @see java.awt.Font
	 **/
	public void setSelectedFont(Font font)
	{
		// getName, not getFamily: for a family this machine does not have, getFamily reports "Dialog" rather than what the map says.
		setSelectedFontFamily(font.getName());
		setSelectedFontStyle(font.getStyle());
		setSelectedFontSize(font.getSize());
	}

	/**
	 * Show font selection dialog.
	 * 
	 * @param parent
	 *            Dialog's Parent component.
	 * @return OK_OPTION, CANCEL_OPTION or ERROR_OPTION
	 *
	 * @see #OK_OPTION
	 * @see #CANCEL_OPTION
	 * @see #ERROR_OPTION
	 **/
	public int showDialog(Component parent)
	{
		dialogResultValue = ERROR_OPTION;
		JDialog dialog = createDialog(parent);
		dialog.addWindowListener(new WindowAdapter()
		{
			public void windowClosing(WindowEvent e)
			{
				dialogResultValue = CANCEL_OPTION;
			}
		});

		dialog.setVisible(true);
		dialog.dispose();
		dialog = null;

		return dialogResultValue;
	}

	/**
	 * Copies the style or size the user picked into the text field above its list.
	 */
	protected class ListSelectionHandler implements ListSelectionListener
	{
		private JTextComponent textComponent;

		ListSelectionHandler(JTextComponent textComponent)
		{
			this.textComponent = textComponent;
		}

		public void valueChanged(ListSelectionEvent e)
		{
			if (e.getValueIsAdjusting())
			{
				return;
			}

			Object selected = ((JList<?>) e.getSource()).getSelectedValue();
			if (selected == null)
			{
				return;
			}

			textComponent.setText((String) selected);
			updateSampleFont();
		}
	}

	/**
	 * Keeps {@link #selectedFamily} in step with the row the user picked, and steps over headings, which name a group rather than a font.
	 */
	private class FamilySelectionHandler implements ListSelectionListener
	{
		public void valueChanged(ListSelectionEvent e)
		{
			if (e.getValueIsAdjusting() || isRebuildingFamilyList)
			{
				return;
			}

			JList<?> list = (JList<?>) e.getSource();
			Object selected = list.getSelectedValue();
			if (selected instanceof FontFamilySections.SectionHeading)
			{
				// Step past it in whichever direction the selection was moving, so arrowing through the list never lands on a heading.
				int index = list.getSelectedIndex();
				int next = index + (index >= previousSelectedIndex ? 1 : -1);
				if (next >= 0 && next < list.getModel().getSize())
				{
					list.setSelectedIndex(next);
				}
				return;
			}

			previousSelectedIndex = list.getSelectedIndex();
			if (selected instanceof String)
			{
				selectedFamily = (String) selected;
				// Every row for this family is drawn as selected, so repaint rather than relying on the two rows the list knows changed.
				list.repaint();
				updateSampleFont();
			}
		}
	}

	protected class TextFieldFocusHandlerForTextSelection extends FocusAdapter
	{
		private JTextComponent textComponent;

		public TextFieldFocusHandlerForTextSelection(JTextComponent textComponent)
		{
			this.textComponent = textComponent;
		}

		public void focusGained(FocusEvent e)
		{
			textComponent.selectAll();
		}

		public void focusLost(FocusEvent e)
		{
			textComponent.select(0, 0);
			updateSampleFont();
		}
	}

	/**
	 * Keeps the style and size lists in step with what is typed above them. The family list has its own handler, because it narrows the
	 * list rather than jumping within it.
	 */
	protected class ListSearchTextFieldDocumentHandler implements DocumentListener
	{
		JList<?> targetList;

		public ListSearchTextFieldDocumentHandler(JList<?> targetList)
		{
			this.targetList = targetList;
		}

		public void insertUpdate(DocumentEvent e)
		{
			update(e);
		}

		public void removeUpdate(DocumentEvent e)
		{
			update(e);
		}

		public void changedUpdate(DocumentEvent e)
		{
			update(e);
		}

		private void update(DocumentEvent event)
		{
			String newValue = "";
			try
			{
				Document doc = event.getDocument();
				newValue = doc.getText(0, doc.getLength());
			}
			catch (BadLocationException e)
			{
				e.printStackTrace();
			}

			if (newValue.length() > 0)
			{
				int match = targetList.getNextMatch(newValue, 0, Position.Bias.Forward);
				final int index = match < 0 ? 0 : match;
				targetList.ensureIndexIsVisible(index);

				String matchedName = targetList.getModel().getElementAt(index).toString();
				if (newValue.equalsIgnoreCase(matchedName) && index != targetList.getSelectedIndex())
				{
					SwingUtilities.invokeLater(() -> targetList.setSelectedIndex(index));
				}
			}
		}
	}

	protected class TextFieldKeyHandlerForListSelectionUpDown extends KeyAdapter
	{
		private JList<?> targetList;

		public TextFieldKeyHandlerForListSelectionUpDown(JList<?> list)
		{
			this.targetList = list;
		}

		public void keyPressed(KeyEvent e)
		{
			int listSize = targetList.getModel().getSize();
			if (listSize == 0)
			{
				return;
			}

			switch (e.getKeyCode())
			{
				case KeyEvent.VK_UP:
					targetList.setSelectedIndex(Math.max(0, targetList.getSelectedIndex() - 1));
					break;
				case KeyEvent.VK_DOWN:
					targetList.setSelectedIndex(Math.min(listSize - 1, targetList.getSelectedIndex() + 1));
					break;
				default:
					break;
			}
			targetList.ensureIndexIsVisible(targetList.getSelectedIndex());
		}
	}

	/**
	 * Narrows the family list to the families whose names contain what has been typed.
	 *
	 * <p>
	 * Narrowing rather than jumping to the first match is what makes a long list usable: every match is visible at once, and a search that
	 * matches nothing says so. It hides families, but only the ones the user just asked to hide, and clearing the box brings them back.
	 */
	private class FamilySearchHandler implements DocumentListener
	{
		public void insertUpdate(DocumentEvent e)
		{
			update(e);
		}

		public void removeUpdate(DocumentEvent e)
		{
			update(e);
		}

		public void changedUpdate(DocumentEvent e)
		{
			update(e);
		}

		private void update(DocumentEvent event)
		{
			try
			{
				Document document = event.getDocument();
				searchText = document.getText(0, document.getLength()).trim();
			}
			catch (BadLocationException e)
			{
				searchText = "";
			}

			rebuildFontFamilyList();
			scrollSelectedFamilyIntoView();
		}
	}

	protected class DialogOKAction extends AbstractAction
	{
		protected static final String ACTION_NAME = "OK";
		private JDialog dialog;

		protected DialogOKAction(JDialog dialog)
		{
			this.dialog = dialog;
			putValue(Action.DEFAULT, ACTION_NAME);
			putValue(Action.ACTION_COMMAND_KEY, ACTION_NAME);
			putValue(Action.NAME, Translation.get("common.ok"));
		}

		public void actionPerformed(ActionEvent e)
		{
			dialogResultValue = OK_OPTION;
			dialog.setVisible(false);
		}
	}

	protected class DialogCancelAction extends AbstractAction
	{
		protected static final String ACTION_NAME = "Cancel";
		private JDialog dialog;

		protected DialogCancelAction(JDialog dialog)
		{
			this.dialog = dialog;
			putValue(Action.DEFAULT, ACTION_NAME);
			putValue(Action.ACTION_COMMAND_KEY, ACTION_NAME);
			putValue(Action.NAME, Translation.get("common.cancel"));
		}

		public void actionPerformed(ActionEvent e)
		{
			dialogResultValue = CANCEL_OPTION;
			dialog.setVisible(false);
		}
	}

	protected JDialog createDialog(Component parent)
	{
		Frame frame = parent instanceof Frame ? (Frame) parent : (Frame) SwingUtilities.getAncestorOfClass(Frame.class, parent);
		JDialog dialog = new JDialog(frame, Translation.get("fontChooser.title"), true);

		Action okAction = new DialogOKAction(dialog);
		Action cancelAction = new DialogCancelAction(dialog);

		JButton okButton = new JButton(okAction);
		okButton.setFont(DEFAULT_FONT);
		JButton cancelButton = new JButton(cancelAction);
		cancelButton.setFont(DEFAULT_FONT);

		JPanel buttonsPanel = new JPanel();
		buttonsPanel.setLayout(new GridLayout(2, 1, 0, 5));
		buttonsPanel.add(okButton);
		buttonsPanel.add(cancelButton);
		buttonsPanel.setBorder(BorderFactory.createEmptyBorder(25, 0, 10, 10));

		ActionMap actionMap = buttonsPanel.getActionMap();
		actionMap.put(cancelAction.getValue(Action.DEFAULT), cancelAction);
		actionMap.put(okAction.getValue(Action.DEFAULT), okAction);
		InputMap inputMap = buttonsPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		inputMap.put(KeyStroke.getKeyStroke("ESCAPE"), cancelAction.getValue(Action.DEFAULT));
		inputMap.put(KeyStroke.getKeyStroke("ENTER"), okAction.getValue(Action.DEFAULT));

		JPanel dialogEastPanel = new JPanel();
		dialogEastPanel.setLayout(new BorderLayout());
		dialogEastPanel.add(buttonsPanel, BorderLayout.NORTH);

		dialog.getContentPane().add(this, BorderLayout.CENTER);
		dialog.getContentPane().add(dialogEastPanel, BorderLayout.EAST);
		dialog.pack();
		// Scrolling only lands where it should once the list has a height, which it gets from packing, so this waits until after it rather
		// than happening when the font was set.
		scrollSelectedFamilyIntoView();
		dialog.setLocationRelativeTo(frame);
		return dialog;
	}

	protected void updateSampleFont()
	{
		getSampleTextField().setFont(getSelectedFont());
	}

	protected JPanel getFontFamilyPanel()
	{
		if (fontNamePanel == null)
		{
			fontNamePanel = new JPanel();
			fontNamePanel.setLayout(new BorderLayout());
			fontNamePanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
			fontNamePanel.setPreferredSize(new Dimension(240, listPanelHeight));

			JScrollPane scrollPane = new JScrollPane(getFontFamilyList());
			scrollPane.getVerticalScrollBar().setFocusable(false);
			scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

			JPanel p = new JPanel();
			p.setLayout(new BorderLayout());
			p.add(getFontFamilyTextField(), BorderLayout.NORTH);
			p.add(scrollPane, BorderLayout.CENTER);

			JLabel label = new JLabel(Translation.get("fontChooser.fontName"));
			label.setHorizontalAlignment(JLabel.LEFT);
			label.setHorizontalTextPosition(JLabel.LEFT);
			label.setLabelFor(getFontFamilyTextField());
			label.setDisplayedMnemonic('F');

			fontNamePanel.add(label, BorderLayout.NORTH);
			fontNamePanel.add(p, BorderLayout.CENTER);
		}
		return fontNamePanel;
	}

	protected JPanel getFontStylePanel()
	{
		if (fontStylePanel == null)
		{
			fontStylePanel = new JPanel();
			fontStylePanel.setLayout(new BorderLayout());
			fontStylePanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
			fontStylePanel.setPreferredSize(new Dimension(140, listPanelHeight));

			JScrollPane scrollPane = new JScrollPane(getFontStyleList());
			scrollPane.getVerticalScrollBar().setFocusable(false);
			scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

			JPanel p = new JPanel();
			p.setLayout(new BorderLayout());
			p.add(getFontStyleTextField(), BorderLayout.NORTH);
			p.add(scrollPane, BorderLayout.CENTER);

			JLabel label = new JLabel(Translation.get("fontChooser.fontStyle"));
			label.setHorizontalAlignment(JLabel.LEFT);
			label.setHorizontalTextPosition(JLabel.LEFT);
			label.setLabelFor(getFontStyleTextField());
			label.setDisplayedMnemonic('Y');

			fontStylePanel.add(label, BorderLayout.NORTH);
			fontStylePanel.add(p, BorderLayout.CENTER);
		}
		return fontStylePanel;
	}

	protected JPanel getFontSizePanel()
	{
		if (fontSizePanel == null)
		{
			fontSizePanel = new JPanel();
			fontSizePanel.setLayout(new BorderLayout());
			fontSizePanel.setPreferredSize(new Dimension(70, listPanelHeight));
			fontSizePanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

			JScrollPane scrollPane = new JScrollPane(getFontSizeList());
			scrollPane.getVerticalScrollBar().setFocusable(false);
			scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

			JPanel p = new JPanel();
			p.setLayout(new BorderLayout());
			p.add(getFontSizeTextField(), BorderLayout.NORTH);
			p.add(scrollPane, BorderLayout.CENTER);

			JLabel label = new JLabel(Translation.get("fontChooser.fontSize"));
			label.setHorizontalAlignment(JLabel.LEFT);
			label.setHorizontalTextPosition(JLabel.LEFT);
			label.setLabelFor(getFontSizeTextField());
			label.setDisplayedMnemonic('S');

			fontSizePanel.add(label, BorderLayout.NORTH);
			fontSizePanel.add(p, BorderLayout.CENTER);
		}
		return fontSizePanel;
	}

	protected JPanel getSamplePanel()
	{
		if (samplePanel == null)
		{
			Border titledBorder = BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), Translation.get("fontChooser.sample"));
			Border empty = BorderFactory.createEmptyBorder(5, 10, 10, 10);
			Border border = BorderFactory.createCompoundBorder(titledBorder, empty);

			samplePanel = new JPanel();
			samplePanel.setLayout(new BorderLayout());
			samplePanel.setBorder(border);

			samplePanel.add(getSampleTextField(), BorderLayout.CENTER);
		}
		return samplePanel;
	}

	protected JTextField getSampleTextField()
	{
		if (sampleText == null)
		{
			Border lowered = BorderFactory.createLoweredBevelBorder();

			sampleText = new JTextField(Translation.get("fontChooser.sampleText"));
			sampleText.setBorder(lowered);
			sampleText.setPreferredSize(new Dimension(sampleWidth, sampleHeight));
		}
		return sampleText;
	}

	/**
	 * The text the chosen font has to be able to draw. Families that cannot draw it are shown greyed out, with the script they are missing
	 * named in the row.
	 */
	public void setTextThatMustBeDrawable(String text)
	{
		// Only the distinct characters matter, and reducing to them keeps the coverage check cheap when a map has a lot of labels.
		Set<Integer> distinct = new LinkedHashSet<>();
		StringBuilder builder = new StringBuilder();
		if (text != null)
		{
			text.codePoints().forEach(codePoint ->
			{
				if (distinct.add(codePoint))
				{
					builder.appendCodePoint(codePoint);
				}
			});
		}

		charactersThatMustBeDrawable = builder.toString();
		missingScriptByFamily.clear();
		rebuildFontFamilyList();
	}

	private Object[] buildFontFamilyRows()
	{
		return FontFamilySections.buildRows(getFamiliesUsedByThisMapIncludingCurrent(), searchText).toArray();
	}

	/**
	 * The families the map uses, plus the one the picker opened on. They are normally the same set, but a picker opened on a font nothing
	 * has drawn with yet would otherwise leave that font out of the only group it belongs to.
	 */
	private List<String> getFamiliesUsedByThisMapIncludingCurrent()
	{
		List<String> families = new ArrayList<>(familiesUsedByThisMap);
		if (familyFromSettings != null && families.stream().noneMatch(family -> family.equalsIgnoreCase(familyFromSettings)))
		{
			families.add(0, familyFromSettings);
		}
		return families;
	}

	@SuppressWarnings("unchecked")
	private void rebuildFontFamilyList()
	{
		isRebuildingFamilyList = true;
		try
		{
			((JList<Object>) getFontFamilyList()).setListData(buildFontFamilyRows());
			selectFamilyInList(getSelectedFontFamily());
		}
		finally
		{
			isRebuildingFamilyList = false;
		}
	}

	/**
	 * Puts the list's own selection on the first row for the given family, or clears it when a search has hidden every row for it. What is
	 * chosen is {@link #selectedFamily} either way; this only keeps the list's idea of the selection from contradicting it.
	 */
	private void selectFamilyInList(String family)
	{
		int index = findFirstRowForFamily(family);
		if (index < 0)
		{
			getFontFamilyList().clearSelection();
			return;
		}
		getFontFamilyList().setSelectedIndex(index);
		previousSelectedIndex = index;
	}

	private void scrollSelectedFamilyIntoView()
	{
		int index = findFirstRowForFamily(getSelectedFontFamily());
		if (index < 0)
		{
			return;
		}

		// Scrolling to the top first is what shows the heading above the selected family. Ensuring a row is visible only ever scrolls far
		// enough to reveal it, so on its own it leaves the viewport wherever an earlier scroll put it, hiding whatever sits above.
		getFontFamilyList().ensureIndexIsVisible(0);
		getFontFamilyList().ensureIndexIsVisible(index);
	}

	private int findFirstRowForFamily(String family)
	{
		if (family == null)
		{
			return -1;
		}
		ListModel<?> model = getFontFamilyList().getModel();
		for (int i = 0; i < model.getSize(); i++)
		{
			Object row = model.getElementAt(i);
			if (row instanceof String && ((String) row).equalsIgnoreCase(family))
			{
				return i;
			}
		}
		return -1;
	}

	/**
	 * The script the given family has no glyphs for among the characters it has to draw, or null when it can draw all of them. Memoized
	 * because the list renderer asks on every repaint.
	 */
	private Script getMissingScript(String family)
	{
		if (charactersThatMustBeDrawable.isEmpty())
		{
			return null;
		}
		return missingScriptByFamily.computeIfAbsent(family, key -> FontFinder.findMissingScript(key, charactersThatMustBeDrawable));
	}

	/**
	 * Draws each family in its own font, with the script it cannot draw named where that applies. Where a family came from is not named in
	 * the row, since the heading the row sits under already says it.
	 */
	private class FontFamilyRenderer extends JPanel implements ListCellRenderer<Object>
	{
		private final JLabel familyLabel = new JLabel();

		FontFamilyRenderer()
		{
			super(new BorderLayout());
			add(familyLabel, BorderLayout.CENTER);
			setOpaque(true);
		}

		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
		{
			setBorder(BorderFactory.createEmptyBorder());

			if (value instanceof FontFamilySections.SectionHeading)
			{
				setBackground(list.getBackground());
				FontFamilySections.applyHeadingStyle(familyLabel, (FontFamilySections.SectionHeading) value, list, DEFAULT_FONT);
				familyLabel.setBorder(null);
				setToolTipText(null);
				setBorder(FontFamilySections.createHeadingBorder(list, index == 0));
				return this;
			}

			// The indent is on the label rather than on the row, so that the band a chosen family is highlighted in still runs the whole
			// width of the list.
			familyLabel.setBorder(FontFamilySections.createFamilyBorder());

			String family = (String) value;
			// A family can occupy more than one row, so what counts as selected is the family rather than the row the list happens to have
			// its own selection on. Without this, the same font would look chosen in one place and not in another.
			boolean isFamilySelected = family.equalsIgnoreCase(getSelectedFontFamily());
			setBackground(isFamilySelected ? list.getSelectionBackground() : list.getBackground());
			familyLabel.setForeground(isFamilySelected ? list.getSelectionForeground() : list.getForeground());

			// Showing each family in its own font is worth more to someone browsing unfamiliar fonts than any amount of categorising.
			familyLabel.setFont(new Font(family, Font.PLAIN, familyPreviewFontSize));
			familyLabel.setText(family);

			Script missingScript = getMissingScript(family);
			familyLabel.setEnabled(missingScript == null);
			if (missingScript != null)
			{
				// Greying a row without saying why is worse than not marking it at all, so the reason goes in the row rather than only in a
				// tooltip that a user may never hover.
				String scriptName = Translation.get("script." + missingScript.name());
				familyLabel.setText(family + "   " + Translation.get("fontChooser.missingScriptSuffix", scriptName));
				setToolTipText(Translation.get("fontChooser.cannotDisplayMapText", family, scriptName));
			}
			else
			{
				setToolTipText(null);
			}
			return this;
		}
	}

	protected String[] getFontStyleNames()
	{
		if (fontStyleNames == null)
		{
			int i = 0;
			fontStyleNames = new String[4];
			fontStyleNames[i++] = Translation.get("fontChooser.plain");
			fontStyleNames[i++] = Translation.get("fontChooser.bold");
			fontStyleNames[i++] = Translation.get("fontChooser.italic");
			fontStyleNames[i++] = Translation.get("fontChooser.boldItalic");
		}
		return fontStyleNames;
	}
}