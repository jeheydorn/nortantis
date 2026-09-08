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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nortantis.FontFinder;
import nortantis.FontFinder.AvailableFont;
import nortantis.FontFinder.FontCategory;
import nortantis.FontFinder.FontSource;
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
	private static final int minSampleHeight = 100;
	/** Beyond this the sample clips rather than growing, so that a 240 point sample can't push the dialog off the screen. */
	private static final int maxSampleHeight = 260;
	private static final int sampleVerticalPadding = 8;
	/** The size each family name is drawn at in its own font, in the family list. */
	private static final int familyPreviewFontSize = 14;

	/**
	 * The row shown above a family that the selected font source excludes but that the map uses. It is a heading rather than a font, so it
	 * cannot be selected.
	 */
	private static final Object currentFontSeparatorRow = new Object()
	{
		@Override
		public String toString()
		{
			return Translation.get("fontChooser.source.currentFont");
		}
	};

	// instance variables
	protected int dialogResultValue = ERROR_OPTION;

	private String[] fontStyleNames = null;
	private String[] fontSizeStrings = null;

	/** Which fonts the family list shows. Derived from the font the dialog opens on, never stored with the map. */
	private FontSource selectedSource = FontSource.Bundled;
	/** The family the dialog opened on, which is always listed whatever the selected source excludes. */
	private String familyFromSettings;
	/** The distinct characters the chosen font has to be able to draw, used to mark families that cannot draw them. */
	private String charactersThatMustBeDrawable = "";
	private final Map<String, Script> missingScriptByFamily = new HashMap<>();
	private JRadioButton bundledSourceButton;
	private JRadioButton artPackSourceButton;
	private JRadioButton systemSourceButton;
	private JComponent sourcePanel;
	private JPanel noCoverageNoticePanel;
	/** The category the map recorded for the family it opened on, kept so that reopening the picker doesn't discard it. */
	private FontCategory categoryFromSettings;
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

		JPanel contentsPanel = new JPanel();
		contentsPanel.setLayout(new GridLayout(2, 1));
		contentsPanel.add(selectPanel, BorderLayout.NORTH);
		contentsPanel.add(getSamplePanel(), BorderLayout.CENTER);

		JPanel headerPanel = new JPanel();
		headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
		headerPanel.add(getSourcePanel());
		headerPanel.add(getNoCoverageNoticePanel());

		JPanel outerPanel = new JPanel(new BorderLayout());
		outerPanel.add(headerPanel, BorderLayout.NORTH);
		outerPanel.add(contentsPanel, BorderLayout.CENTER);

		this.setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		this.add(outerPanel);
		this.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		this.setSelectedFont(DEFAULT_SELECTED_FONT);
	}

	public JTextField getFontFamilyTextField()
	{
		if (fontFamilyTextField == null)
		{
			fontFamilyTextField = new JTextField();
			fontFamilyTextField.addFocusListener(new TextFieldFocusHandlerForTextSelection(fontFamilyTextField));
			fontFamilyTextField.addKeyListener(new TextFieldKeyHandlerForListSelectionUpDown(getFontFamilyList()));
			fontFamilyTextField.getDocument().addDocumentListener(new ListSearchTextFieldDocumentHandler(getFontFamilyList()));
			fontFamilyTextField.setFont(DEFAULT_FONT);

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
			fontNameList.addListSelectionListener(new ListSelectionHandler(getFontFamilyTextField()));
			fontNameList.setSelectedIndex(0);
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
		Object selected = getFontFamilyList().getSelectedValue();
		return selected instanceof String ? (String) selected : familyFromSettings;
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
		// The source selector reflects where the font in the field came from. Deriving it rather than storing it is what makes an existing
		// map keep its own fonts with nothing to migrate and no flag that can disagree with what the map says.
		familyFromSettings = name;
		selectedSource = FontFinder.getSource(name);
		if (selectedSource == FontSource.ArtPack && !hasFontsFromSource(FontSource.ArtPack))
		{
			selectedSource = FontSource.System;
		}

		rebuildFontFamilyList(name);
		updateSampleFont();
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

	protected class ListSelectionHandler implements ListSelectionListener
	{
		private JTextComponent textComponent;

		ListSelectionHandler(JTextComponent textComponent)
		{
			this.textComponent = textComponent;
		}

		public void valueChanged(ListSelectionEvent e)
		{
			if (e.getValueIsAdjusting() == false)
			{
				JList<?> list = (JList<?>) e.getSource();
				Object selected = list.getSelectedValue();
				if (selected == currentFontSeparatorRow)
				{
					// It is a heading, not a font, so step past it in whichever direction the selection was moving.
					int index = list.getSelectedIndex();
					list.setSelectedIndex(index + 1 < list.getModel().getSize() ? index + 1 : index - 1);
					return;
				}
				String selectedValue = (String) selected;
				String oldValue = textComponent.getText();
				textComponent.setText(selectedValue);
				if (!oldValue.equalsIgnoreCase(selectedValue))
				{
					textComponent.selectAll();
					textComponent.requestFocus();
				}

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

	protected class TextFieldKeyHandlerForListSelectionUpDown extends KeyAdapter
	{
		private JList<?> targetList;

		public TextFieldKeyHandlerForListSelectionUpDown(JList<?> list)
		{
			this.targetList = list;
		}

		public void keyPressed(KeyEvent e)
		{
			int i = targetList.getSelectedIndex();
			switch (e.getKeyCode())
			{
				case KeyEvent.VK_UP:
					i = targetList.getSelectedIndex() - 1;
					if (i < 0)
					{
						i = 0;
					}
					targetList.setSelectedIndex(i);
					break;
				case KeyEvent.VK_DOWN:
					int listSize = targetList.getModel().getSize();
					i = targetList.getSelectedIndex() + 1;
					if (i >= listSize)
					{
						i = listSize - 1;
					}
					targetList.setSelectedIndex(i);
					break;
				default:
					break;
			}
		}
	}

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
				int index = targetList.getNextMatch(newValue, 0, Position.Bias.Forward);
				if (index < 0)
				{
					index = 0;
				}
				targetList.ensureIndexIsVisible(index);

				String matchedName = targetList.getModel().getElementAt(index).toString();
				if (newValue.equalsIgnoreCase(matchedName))
				{
					if (index != targetList.getSelectedIndex())
					{
						SwingUtilities.invokeLater(new ListSelector(index));
					}
				}
			}
		}

		public class ListSelector implements Runnable
		{
			private int index;

			public ListSelector(int index)
			{
				this.index = index;
			}

			public void run()
			{
				targetList.setSelectedIndex(this.index);
			}
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
		dialog.setLocationRelativeTo(frame);
		return dialog;
	}

	protected void updateSampleFont()
	{
		Font font = getSelectedFont();
		JTextField sampleField = getSampleTextField();
		sampleField.setFont(font);

		// Measure the font rather than assuming a height, since the size list goes up to 240. getMaxAscent is used rather than getAscent
		// because script faces routinely draw swashes and ascenders above the typical ascent.
		FontMetrics metrics = sampleField.getFontMetrics(font);
		int neededHeight = metrics.getMaxAscent() + metrics.getMaxDescent() + sampleVerticalPadding;
		int height = Math.max(minSampleHeight, Math.min(neededHeight, maxSampleHeight));
		sampleField.setPreferredSize(new Dimension(sampleWidth, height));
		sampleField.revalidate();
	}

	protected JPanel getFontFamilyPanel()
	{
		if (fontNamePanel == null)
		{
			fontNamePanel = new JPanel();
			fontNamePanel.setLayout(new BorderLayout());
			fontNamePanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
			// Wide enough for a family name and the name of the art pack it came from side by side.
			fontNamePanel.setPreferredSize(new Dimension(240, 130));

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
			fontStylePanel.setPreferredSize(new Dimension(140, 130));

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
			fontSizePanel.setPreferredSize(new Dimension(70, 130));
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
			sampleText.setPreferredSize(new Dimension(sampleWidth, minSampleHeight));
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
		rebuildFontFamilyList(getSelectedFontFamily());
	}

	private JComponent getSourcePanel()
	{
		if (sourcePanel == null)
		{
			bundledSourceButton = createSourceButton(Translation.get("fontChooser.source.bundled"), FontSource.Bundled);
			artPackSourceButton = createSourceButton(Translation.get("fontChooser.source.artPack"), FontSource.ArtPack);
			systemSourceButton = createSourceButton(Translation.get("fontChooser.source.system"), FontSource.System);

			List<JRadioButton> buttons = new ArrayList<>();
			buttons.add(bundledSourceButton);
			// Showing a source that leads to an empty list is worse than not offering it.
			if (hasFontsFromSource(FontSource.ArtPack))
			{
				buttons.add(artPackSourceButton);
			}
			buttons.add(systemSourceButton);

			ButtonGroup group = new ButtonGroup();
			JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
			panel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
			panel.add(new JLabel(Translation.get("fontChooser.source.label")));
			for (JRadioButton button : buttons)
			{
				group.add(button);
				panel.add(Box.createHorizontalStrut(8));
				panel.add(button);
			}

			sourcePanel = panel;
			sourcePanel.setAlignmentX(LEFT_ALIGNMENT);
		}
		return sourcePanel;
	}

	private JRadioButton createSourceButton(String text, FontSource source)
	{
		JRadioButton button = new JRadioButton(text);
		button.addActionListener(e ->
		{
			selectedSource = source;
			// A source the user picked keeps whatever family is selected if that source also has it, and otherwise falls back to the
			// family the picker opened on, so that switching sources to look around never silently changes the map's font.
			rebuildFontFamilyList(getSelectedFontFamily());
		});
		return button;
	}

	private JPanel getNoCoverageNoticePanel()
	{
		if (noCoverageNoticePanel == null)
		{
			noCoverageNoticePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
			noCoverageNoticePanel.setAlignmentX(LEFT_ALIGNMENT);

			JLabel notice = new JLabel(Translation.get("fontChooser.noFontsCoverMapText"));
			notice.setForeground(SwingHelper.warningMessageColor);
			noCoverageNoticePanel.add(notice);
			noCoverageNoticePanel.add(Box.createHorizontalStrut(8));
			noCoverageNoticePanel.add(SwingHelper.createActionLink(Translation.get("fontChooser.switchToSystemFonts"), () ->
			{
				selectedSource = FontSource.System;
				systemSourceButton.setSelected(true);
				rebuildFontFamilyList(getSelectedFontFamily());
			}));
			noCoverageNoticePanel.setVisible(false);
		}
		return noCoverageNoticePanel;
	}

	/**
	 * The category of the selected family, which decides what a replacement in the same style would be if the font is ever missing. A
	 * bundled or art pack font knows its category from the folder it ships in; a font on this computer is categorized by its name, except
	 * that the family the picker opened on keeps whatever category the map recorded for it.
	 */
	public FontCategory getSelectedCategory()
	{
		String family = getSelectedFontFamily();
		FontCategory stored = family != null && family.equalsIgnoreCase(familyFromSettings) ? categoryFromSettings : null;
		return FontFinder.getCategory(family, stored);
	}

	public void setSelectedCategory(FontCategory category)
	{
		categoryFromSettings = category;
	}

	private static boolean hasFontsFromSource(FontSource source)
	{
		for (AvailableFont font : FontFinder.listAvailableFonts())
		{
			if (font.source == source)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The rows of the family list: every family from the selected source, plus the family the dialog opened on when that source excludes
	 * it. Without the latter the picker looks like it silently cleared the map's font.
	 */
	private Object[] buildFontFamilyRows()
	{
		List<Object> rows = new ArrayList<>();
		boolean containsFamilyFromSettings = false;
		for (AvailableFont font : FontFinder.listAvailableFonts())
		{
			if (font.source == selectedSource)
			{
				rows.add(font.family);
				if (font.family.equalsIgnoreCase(familyFromSettings))
				{
					containsFamilyFromSettings = true;
				}
			}
		}

		if (familyFromSettings != null && !containsFamilyFromSettings)
		{
			rows.add(currentFontSeparatorRow);
			rows.add(familyFromSettings);
		}
		return rows.toArray();
	}

	@SuppressWarnings("unchecked")
	private void rebuildFontFamilyList(String familyToSelect)
	{
		((JList<Object>) getFontFamilyList()).setListData(buildFontFamilyRows());
		if (!selectFamilyInList(familyToSelect))
		{
			selectFamilyInList(familyFromSettings);
		}

		getSourcePanel();
		bundledSourceButton.setSelected(selectedSource == FontSource.Bundled);
		artPackSourceButton.setSelected(selectedSource == FontSource.ArtPack);
		systemSourceButton.setSelected(selectedSource == FontSource.System);
		getNoCoverageNoticePanel().setVisible(selectedSource != FontSource.System && !anyListedFamilyCanDrawTheText());
	}

	private boolean anyListedFamilyCanDrawTheText()
	{
		if (charactersThatMustBeDrawable.isEmpty())
		{
			return true;
		}
		ListModel<?> model = getFontFamilyList().getModel();
		for (int i = 0; i < model.getSize(); i++)
		{
			Object row = model.getElementAt(i);
			if (row != currentFontSeparatorRow && getMissingScript((String) row) == null
					&& FontFinder.canDisplay((String) row, charactersThatMustBeDrawable))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Selects the given family in the list and scrolls it into view.
	 *
	 * @return True if the list contained the family.
	 */
	private boolean selectFamilyInList(String family)
	{
		if (family == null)
		{
			return false;
		}
		ListModel<?> model = getFontFamilyList().getModel();
		for (int i = 0; i < model.getSize(); i++)
		{
			Object row = model.getElementAt(i);
			if (row != currentFontSeparatorRow && ((String) row).equalsIgnoreCase(family))
			{
				getFontFamilyList().setSelectedIndex(i);
				getFontFamilyList().ensureIndexIsVisible(i);
				// Selecting a row that was already selected fires no event, so the text field is set here rather than left to the
				// selection listener, which is what otherwise leaves the field showing a family the list no longer has selected.
				getFontFamilyTextField().setText((String) row);
				return true;
			}
		}
		return false;
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
	 * Draws each family in its own font, with the art pack it came from named at the right of the row and the script it cannot draw named
	 * where that applies.
	 */
	private class FontFamilyRenderer extends JPanel implements ListCellRenderer<Object>
	{
		private final JLabel familyLabel = new JLabel();
		private final JLabel artPackLabel = new JLabel();

		FontFamilyRenderer()
		{
			super(new BorderLayout());
			artPackLabel.setFont(DEFAULT_FONT);
			artPackLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
			add(familyLabel, BorderLayout.CENTER);
			add(artPackLabel, BorderLayout.EAST);
			setOpaque(true);
		}

		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
		{
			setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
			Color foreground = isSelected ? list.getSelectionForeground() : list.getForeground();
			familyLabel.setForeground(foreground);
			artPackLabel.setForeground(foreground);
			setBorder(BorderFactory.createEmptyBorder());

			if (value == currentFontSeparatorRow)
			{
				setBackground(list.getBackground());
				familyLabel.setFont(DEFAULT_FONT);
				familyLabel.setText(value.toString());
				familyLabel.setEnabled(false);
				artPackLabel.setText("");
				setToolTipText(null);
				setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, list.getForeground()),
						BorderFactory.createEmptyBorder(2, 0, 0, 0)));
				return this;
			}

			String family = (String) value;
			// Showing each family in its own font is worth more to someone browsing unfamiliar fonts than any amount of categorising.
			familyLabel.setFont(new Font(family, Font.PLAIN, familyPreviewFontSize));
			familyLabel.setText(family);
			artPackLabel.setText(FontFinder.getSource(family) == FontSource.ArtPack ? FontFinder.getArtPack(family) : "");

			Script missingScript = getMissingScript(family);
			familyLabel.setEnabled(missingScript == null);
			artPackLabel.setEnabled(missingScript == null);
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