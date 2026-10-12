package nortantis.swing;

import nortantis.FontFinder;
import nortantis.LineBreak;
import nortantis.MapFonts;
import nortantis.MapSettings;
import nortantis.MapText;
import nortantis.TextDrawer;
import nortantis.TextStyle;
import nortantis.TextStylePart;
import nortantis.TextType;
import nortantis.editor.MapUpdater;
import nortantis.geom.IntPoint;
import nortantis.geom.Rectangle;
import nortantis.geom.RotatedRectangle;
import nortantis.platform.Color;
import nortantis.platform.DrawQuality;
import nortantis.platform.Font;
import nortantis.platform.Image;
import nortantis.swing.translation.Translation;
import nortantis.util.Assets;
import nortantis.util.Tuple2;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

public class TextTool extends EditorTool
{
	private DrawModeWidget modeWidget;
	private JLabel drawTextDisabledLabel;
	private RowHider drawTextDisabledLabelHider;
	private RowHider actionsSeparatorHider;

	/*
	 * Add mode.
	 */
	private JComboBox<TextType> addTextTypeComboBox;
	private RowHider addTextTypeHider;
	private TextType textTypeForAdds;
	private JTextField addNameField;
	/** The name most recently generated into addNameField, used to tell a generated name from one the user typed. */
	private String lastGeneratedNameForAdds;
	private RowHider addNameHider;
	/**
	 * The seed for the background of the next text added, chosen ahead of time so that the preview of that text matches it.
	 */
	private long backgroundSeedForNextAdd;
	/**
	 * True from when text is added until the mouse next moves, so that the preview of the next text doesn't cover the text just added.
	 */
	private boolean isAddModeHoverPreviewHidden;
	private RowHider booksHider;
	private BooksWidget booksWidget;

	/*
	 * Edit and Erase modes.
	 */
	private JComboBox<ImageIcon> brushSizeComboBox;
	private RowHider brushSizeHider;
	private ControlClickBehaviorWidget controlClickBehavior;
	private RowHider controlClickBehaviorHider;
	private JTextField editTextField;
	private RowHider editTextFieldHider;
	private JTextArea fontCoverageWarningArea;
	private RowHider fontCoverageWarningHider;
	private JComboBox<TextType> editTextTypeComboBox;
	private RowHider editTextTypeHider;
	private RowHider editStyleHeadingHider;
	private JButton resetStyleButton;
	private TextStyleControls editStyleControls;
	private JButton copyStyleToButton;
	private RowHider styleButtonsHider;
	private RowHider editLayoutRows;
	private static final int curvatureSliderDivider = 100;
	private SliderWithDisplayedValue curvatureSliderWithDisplay;
	private SliderWithDisplayedValue spacingSliderWithDisplay;
	private JComboBoxFixed<LineBreak> lineBreakComboBox;
	private RowHider copyPasteDeleteButtonsHider;
	private RowHider copyPasteDeleteButtonsSeparatorHider;
	/**
	 * True while the edit controls are being set from the selection, when their listeners must not change the selected texts.
	 */
	private boolean isLoadingEditControls;
	/**
	 * The type styles as of the last time settings were loaded, used to tell when they change.
	 */
	private EnumMap<TextType, TextStyle> typeStylesShown;

	/**
	 * The selected texts, in the order they were selected. Compared by identity, since two texts can be equal.
	 */
	private List<MapText> selectedTexts;
	/**
	 * In-memory clipboard of copied texts. Mirrors the {@code copied} pattern in IconsTool — not the OS clipboard, since pasting text from
	 * other apps doesn't translate to a MapText with its full set of properties (type, color, font, rotation, etc.).
	 */
	private List<MapText> textClipboard;

	/**
	 * The location where the mouse was pressed to begin moving or rotating text, stored in graph coordinates rather than panel pixels so it
	 * stays correct if the zoom changes mid-drag. A panel-pixel press point would map to a different graph location under the new zoom,
	 * corrupting the move delta and the rotation reference angle.
	 */
	private nortantis.geom.Point mousePressedLocation;
	/**
	 * The point, in graph coordinates, that several selected texts rotate about together. Fixed when the rotation starts.
	 */
	private nortantis.geom.Point groupRotationCenter;
	/**
	 * The box around several selected texts after they were moved or rotated together, in resolution-invariant coordinates. It keeps the
	 * box, and so the rotation handle, where the drag left it rather than snapping to the axis-aligned box around the texts' new positions.
	 * Null means the box is the axis-aligned one. Cleared when the selection changes or the selected texts change in any other way.
	 */
	private RotatedRectangle groupBoxRI;
	private boolean isRotating;
	private boolean isMoving;
	/**
	 * True while the mouse is held after pressing it to select text, so that dragging adds the texts the brush passes over.
	 */
	private boolean isSelectingWithBrush;
	/**
	 * Draws the picture of text about to be added that follows the mouse, at the resolution it was made for.
	 */
	private TextDrawer hoverPreviewTextDrawer;
	/** The settings hoverPreviewTextDrawer draws with, which hold the style of the type of text Add mode adds. */
	private MapSettings hoverPreviewSettings;
	private double hoverPreviewTextDrawerResolution;
	private boolean hoverPreviewTextDrawerDrawsRegionBoundaries;

	public TextTool(MainWindow parent, ToolsPanel toolsPanel, MapUpdater mapUpdater)
	{
		super(parent, toolsPanel, mapUpdater);
	}

	@Override
	protected JPanel createToolOptionsPanel()
	{
		// Field initializers would run after this method, which the superclass constructor calls.
		textTypeForAdds = TextType.Region;
		selectedTexts = new ArrayList<>();
		backgroundSeedForNextAdd = new Random().nextLong();

		GridBagOrganizer organizer = new GridBagOrganizer();

		JPanel toolOptionsPanel = organizer.panel;
		toolOptionsPanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

		drawTextDisabledLabel = new JLabel("<html>" + Translation.get("textTool.disabled") + "</html>");
		drawTextDisabledLabelHider = organizer.addLeftAlignedComponent(drawTextDisabledLabel);
		drawTextDisabledLabelHider.setVisible(false);

		modeWidget = new DrawModeWidget(Translation.get("textTool.addMode"), Translation.get("textTool.eraseMode"), false, "", true, Translation.get("textTool.editMode"), () -> handleActionChanged());
		modeWidget.configureDrawButton(Translation.get("textTool.add"), Translation.get("textTool.addMode"), KeyEvent.VK_A);
		modeWidget.addToOrganizer(organizer, "");

		actionsSeparatorHider = organizer.addSeparator();

		createAddModeControls(organizer);
		createEditModeControls(organizer);

		Tuple2<JComboBox<ImageIcon>, RowHider> brushSizeTuple = organizer.addBrushSizeComboBox(brushSizes);
		brushSizeComboBox = brushSizeTuple.getFirst();
		brushSizeHider = brushSizeTuple.getSecond();

		controlClickBehavior = new ControlClickBehaviorWidget();
		controlClickBehaviorHider = controlClickBehavior.addToOrganizer(organizer, "textTool.ctrlClickBehavior.help");

		booksWidget = new BooksWidget(false, () ->
		{
			updater.reprocessBooks();
		});
		CollapsiblePanel booksPanel = new CollapsiblePanel("text_tool_books", "Books for generating text", Translation.get("textTool.booksForText.title"),
				booksWidget.getContentPanel(), true);
		booksHider = organizer.addSeparator();
		booksHider.add(organizer.addLeftAlignedComponent(booksPanel, GridBagOrganizer.rowVerticalInset, GridBagOrganizer.rowVerticalInset, false));

		SelectionCycling.addAltChangeListener(() ->
		{
			if (isSelected())
			{
				updateSelectionCycleCandidates(mapEditingPanel.getMousePosition());
				mapEditingPanel.repaint();
			}
		});

		modeWidget.selectEditMode();

		organizer.addHorizontalSpacerRowToHelpComponentAlignment(0.64);
		organizer.addVerticalFillerRow();
		return toolOptionsPanel;
	}

	private void createAddModeControls(GridBagOrganizer organizer)
	{
		addTextTypeComboBox = new JComboBoxFixed<>();
		for (TextType type : TextType.values())
		{
			addTextTypeComboBox.addItem(type);
		}
		addTextTypeComboBox.setSelectedItem(textTypeForAdds);
		addTextTypeComboBox.addActionListener(e ->
		{
			if (addTextTypeComboBox.getSelectedItem() != null)
			{
				textTypeForAdds = (TextType) addTextTypeComboBox.getSelectedItem();
			}
		});
		addTextTypeHider = organizer.addLabelAndComponent(Translation.get("textTool.textType.label"), "", addTextTypeComboBox);

		addNameField = new JTextField();
		// The button is kept square at the name field's height so that it doesn't make the row taller.
		JButton generateNameButton = new JButton()
		{
			@Override
			public Dimension getPreferredSize()
			{
				int height = addNameField.getPreferredSize().height;
				return new Dimension(height, height);
			}

			@Override
			public Dimension getMinimumSize()
			{
				return getPreferredSize();
			}

			@Override
			public Dimension getMaximumSize()
			{
				return getPreferredSize();
			}
		};
		generateNameButton.setMargin(new Insets(0, 0, 0, 0));
		// A clockwise open circle arrow, enlarged because it draws much smaller than letters at the same font size.
		generateNameButton.setIcon(new CenteredSymbolIcon("↻", 1.5f));
		generateNameButton.setToolTipText(Translation.get("textTool.generateName.tooltip"));
		generateNameButton.addActionListener(e -> generateNameForAdds());
		addNameHider = organizer.addLabelAndComponentsHorizontal(Translation.get("textTool.name.label"), Translation.get("textTool.name.help"),
				Arrays.asList(addNameField, generateNameButton), 0, 4);
	}

	private static Tuple2<SliderWithDisplayedValue, RowHider> addCurvatureRow(GridBagOrganizer organizer, Consumer<Double> onChange)
	{
		JSlider slider = new JSlider();
		slider.setPaintLabels(false);
		slider.setMinimum(-curvatureSliderDivider);
		slider.setMaximum(curvatureSliderDivider);
		slider.setValue(0);
		SliderWithDisplayedValue sliderWithDisplay = new SliderWithDisplayedValue(slider, (value) -> String.format("%.2f", value / ((double) curvatureSliderDivider)),
				() -> onChange.accept(slider.getValue() / ((double) curvatureSliderDivider)), 34);
		JButton clearButton = new JButton("x");
		clearButton.setToolTipText(Translation.get("textTool.clearCurvature.tooltip"));
		SwingHelper.addListener(clearButton, () -> slider.setValue(0));
		RowHider row = sliderWithDisplay.addToOrganizer(organizer, Translation.get("textTool.curvature.label"), Translation.get("textTool.curvature.help"), clearButton, 0, 0);
		return new Tuple2<>(sliderWithDisplay, row);
	}

	private static Tuple2<SliderWithDisplayedValue, RowHider> addSpacingRow(GridBagOrganizer organizer, Consumer<Integer> onChange)
	{
		JSlider slider = new JSlider();
		slider.setPaintLabels(false);
		slider.setMinimum(-5);
		slider.setMaximum(30);
		slider.setValue(0);
		SliderWithDisplayedValue sliderWithDisplay = new SliderWithDisplayedValue(slider, null, () -> onChange.accept(slider.getValue()), 34);
		JButton clearButton = new JButton("x");
		clearButton.setToolTipText(Translation.get("textTool.clearSpacing.tooltip"));
		SwingHelper.addListener(clearButton, () -> slider.setValue(0));
		RowHider row = sliderWithDisplay.addToOrganizer(organizer, Translation.get("textTool.spacing.label"), Translation.get("textTool.spacing.help"), clearButton, 0, 0);
		return new Tuple2<>(sliderWithDisplay, row);
	}

	private static Tuple2<JComboBoxFixed<LineBreak>, RowHider> addLineBreakRow(GridBagOrganizer organizer, Consumer<LineBreak> onChange)
	{
		JComboBoxFixed<LineBreak> comboBox = new JComboBoxFixed<>();
		for (LineBreak type : LineBreak.values())
		{
			comboBox.addItem(type);
		}
		comboBox.addActionListener(e ->
		{
			LineBreak lineBreak = (LineBreak) comboBox.getSelectedItem();
			if (lineBreak != null)
			{
				onChange.accept(lineBreak);
			}
		});
		return new Tuple2<>(comboBox, organizer.addLabelAndComponent(Translation.get("textTool.lineBreak.label"), "", comboBox));
	}

	private void createEditModeControls(GridBagOrganizer organizer)
	{
		editTextField = new JTextField();
		editTextField.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent e)
			{
				// Save any edits, but don't move focus. Grabbing focus here would yank it back from whatever component the user is Tabbing
				// (or clicking) to, trapping keyboard focus.
				commitNameEdit();
			}
		});
		editTextField.addActionListener(e ->
		{
			commitNameEdit();
			modeWidget.grabFocusOnSelectedButton();
		});
		editTextField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				updateFontCoverageWarning();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				updateFontCoverageWarning();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				updateFontCoverageWarning();
			}
		});
		editTextFieldHider = organizer.addLeftAlignedComponent(editTextField);

		fontCoverageWarningArea = new JTextArea();
		fontCoverageWarningArea.setEditable(false);
		fontCoverageWarningArea.setFocusable(false);
		fontCoverageWarningArea.setLineWrap(true);
		fontCoverageWarningArea.setWrapStyleWord(true);
		fontCoverageWarningArea.setOpaque(false);
		fontCoverageWarningArea.setForeground(SwingHelper.warningMessageColor);
		fontCoverageWarningArea.setFont(UIManager.getFont("Label.font"));

		JPanel fontCoverageWarningPanel = new JPanel(new BorderLayout());
		fontCoverageWarningPanel.add(SwingHelper.createWarningIconLabel(), BorderLayout.WEST);
		fontCoverageWarningPanel.add(fontCoverageWarningArea, BorderLayout.CENTER);
		fontCoverageWarningHider = organizer.addLeftAlignedComponent(fontCoverageWarningPanel);
		fontCoverageWarningHider.setVisible(false);

		editTextTypeComboBox = new JComboBoxFixed<>();
		for (TextType type : TextType.values())
		{
			editTextTypeComboBox.addItem(type);
		}
		editTextTypeComboBox.addActionListener(e ->
		{
			TextType type = (TextType) editTextTypeComboBox.getSelectedItem();
			if (type != null && !isLoadingEditControls)
			{
				applyToSelectedTexts(text -> text.type = type);
			}
		});
		editTextTypeHider = organizer.addLabelAndComponent(Translation.get("textTool.textType.label"), "", editTextTypeComboBox);

		editStyleHeadingHider = organizer.addSectionHeading(Translation.get("textTool.section.style"));
		editStyleControls = new TextStyleControls(organizer, edit -> applyToSelectedTexts(text ->
		{
			TextStyle style = resolveStyle(text).copy();
			edit.apply(style);
			text.styleOverrides.set(edit.part(), style);
		}), this::getFontFamiliesUsedByThisMap, () -> selectedTexts.size() == 1 ? editTextField.getText() : "");

		copyStyleToButton = new JButton(Translation.get("textTool.copyStyleTo"));
		copyStyleToButton.setToolTipText(Translation.get("textTool.copyStyleTo.tooltip"));
		copyStyleToButton.addActionListener(e -> showCopyStyleDialog());
		resetStyleButton = new JButton();
		resetStyleButton.setToolTipText(Translation.get("textTool.resetStyle.tooltip"));
		resetStyleButton.addActionListener(e -> showResetStyleDialog());
		styleButtonsHider = organizer.addLeftAlignedComponents(Arrays.asList(copyStyleToButton, resetStyleButton));

		editLayoutRows = organizer.addSectionHeading(Translation.get("textTool.section.layout"));
		Tuple2<SliderWithDisplayedValue, RowHider> curvature = addCurvatureRow(organizer, value ->
		{
			if (!isLoadingEditControls)
			{
				applyToSelectedTexts(text -> text.curvature = value);
			}
		});
		curvatureSliderWithDisplay = curvature.getFirst();
		editLayoutRows.add(curvature.getSecond());

		Tuple2<SliderWithDisplayedValue, RowHider> spacing = addSpacingRow(organizer, value ->
		{
			if (!isLoadingEditControls)
			{
				applyToSelectedTexts(text -> text.spacing = value);
			}
		});
		spacingSliderWithDisplay = spacing.getFirst();
		editLayoutRows.add(spacing.getSecond());

		Tuple2<JComboBoxFixed<LineBreak>, RowHider> lineBreak = addLineBreakRow(organizer, value ->
		{
			if (!isLoadingEditControls)
			{
				applyToSelectedTexts(text -> text.lineBreak = value);
			}
		});
		lineBreakComboBox = lineBreak.getFirst();
		editLayoutRows.add(lineBreak.getSecond());

		JButton clearRotationButton = new JButton(Translation.get("textTool.rotateToHorizontal"));
		clearRotationButton.setToolTipText(Translation.get("textTool.rotateToHorizontal.tooltip"));
		clearRotationButton.addActionListener(ev -> rotateSelectedTextToHorizontal());
		editLayoutRows.add(organizer.addLeftAlignedComponents(Arrays.asList(clearRotationButton)));

		copyPasteDeleteButtonsSeparatorHider = organizer.addSeparator();

		JButton copyButton = new JButton(Translation.get("textTool.copy"));
		copyButton.setToolTipText(Translation.get("textTool.copy.tooltip", SwingHelper.getCommandKeyName()));
		SwingHelper.bindButtonShortcut(copyButton, KeyStroke.getKeyStroke(KeyEvent.VK_C, SwingHelper.getMenuShortcutKeyMask()), "textCopyAction");
		copyButton.addActionListener(ev -> copySelectedText());

		JButton pasteButton = new JButton(Translation.get("textTool.paste"));
		pasteButton.setToolTipText(Translation.get("textTool.paste.tooltip", SwingHelper.getCommandKeyName()));
		SwingHelper.bindButtonShortcut(pasteButton, KeyStroke.getKeyStroke(KeyEvent.VK_V, SwingHelper.getMenuShortcutKeyMask()), "textPasteAction");
		pasteButton.addActionListener(ev -> pasteText());

		JButton deleteButton = new JButton(Translation.get("textTool.delete"));
		deleteButton.setToolTipText(Translation.get("textTool.delete.tooltip"));
		// Bind Backspace as well as Delete: the key labeled "delete" sends Backspace on most Mac keyboards.
		SwingHelper.bindButtonShortcut(deleteButton, "textDeleteAction", KeyStroke.getKeyStroke("DELETE"), KeyStroke.getKeyStroke("BACK_SPACE"));
		deleteButton.addActionListener(ev -> deleteSelectedText());

		copyPasteDeleteButtonsHider = organizer.addLeftAlignedComponents(Arrays.asList(copyButton, pasteButton, deleteButton));
	}

	private List<String> getFontFamiliesUsedByThisMap()
	{
		MapSettings settings = mainWindow.getSettingsFromGUI(false);
		return settings == null ? new ArrayList<>() : MapFonts.getFamiliesUsed(settings);
	}

	/**
	 * Shows a warning under the text field when the font that will draw the selected text has no glyphs for what has been typed, naming a
	 * font that does. The map still draws the boxes; this only makes sure the user finds out at the moment they type rather than from
	 * someone else's screenshot.
	 *
	 * <p>
	 * No delay is needed to stop this flickering mid-word: adding characters can only add requirements, so a prefix a font cannot draw
	 * means the whole word fails, and the warning appears at the first undrawable character and stays.
	 */
	private void updateFontCoverageWarning()
	{
		if (selectedTexts.size() != 1 || !editTextFieldHider.isVisible())
		{
			fontCoverageWarningHider.setVisible(false);
			return;
		}

		String text = trimTrailingUnpairedSurrogate(editTextField.getText());
		Font font = resolveStyle(selectedTexts.get(0)).font;
		if (font == null || FontFinder.canDisplay(font.getName(), font.getStyle(), text))
		{
			fontCoverageWarningHider.setVisible(false);
			return;
		}

		String substitute = FontFinder.chooseSubstitute(font.getName(), font.getStyle(), text);
		fontCoverageWarningArea.setText(substitute == null ? Translation.get("textTool.fontCannotDisplay.noneAvailable")
				: Translation.get("textTool.fontCannotDisplay", substitute));
		fontCoverageWarningHider.setVisible(true);
	}

	/**
	 * A character outside the basic multilingual plane is two char values, and a string observed between them ends in an unpaired high
	 * surrogate, which reports as undrawable even when the completed pair is fine.
	 */
	private static String trimTrailingUnpairedSurrogate(String text)
	{
		if (!text.isEmpty() && Character.isHighSurrogate(text.charAt(text.length() - 1)))
		{
			return text.substring(0, text.length() - 1);
		}
		return text;
	}

	private void handleActionChanged()
	{
		if (!modeWidget.isEditMode())
		{
			setSelection(new ArrayList<>(), SelectionFocus.Leave);
		}

		boolean isAddMode = modeWidget.isDrawMode();
		boolean isEditMode = modeWidget.isEditMode();
		addTextTypeHider.setVisible(isAddMode);
		addNameHider.setVisible(isAddMode);
		booksHider.setVisible(isAddMode);
		brushSizeHider.setVisible(modeWidget.isEraseMode() || isEditMode);
		controlClickBehaviorHider.setVisible(isEditMode);
		copyPasteDeleteButtonsHider.setVisible(isEditMode);
		// With nothing selected, no edit controls sit between this separator and the one above.
		copyPasteDeleteButtonsSeparatorHider.setVisible(isEditMode && !selectedTexts.isEmpty());
		actionsSeparatorHider.setVisible(true);
		if (!isEditMode || selectedTexts.isEmpty())
		{
			hideTextEditComponents();
		}

		if (isAddMode)
		{
			if (addNameField.getText().trim().isEmpty())
			{
				generateNameForAdds();
			}
		}
		else
		{
			mapEditingPanel.clearTextHoverPreview();
		}

		// For some reason this is necessary to prevent the text editing field from flattening sometimes.
		if (getToolOptionsPanel() != null)
		{
			getToolOptionsPanel().revalidate();
			getToolOptionsPanel().repaint();
		}

		mapEditingPanel.clearHighlightedAreas();
		mapEditingPanel.clearSelectionCycleCandidateAreas();
		mapEditingPanel.repaint();
		mapEditingPanel.hideBrush();
	}

	@Override
	public String getToolbarName()
	{
		return Translation.get("textTool.name");
	}

	@Override
	public int getMnemonic()
	{
		return KeyEvent.VK_3;
	}

	@Override
	public nortantis.platform.Image getToolIcon()
	{
		nortantis.platform.Image icons = Assets.readImage(Paths.get(Assets.getAssetsPath(), "internal/Text tool.png").toString());
		try (nortantis.platform.Painter p = icons.createPainter(DrawQuality.High))
		{
			String text = Translation.get("textTool.toolIcon");
			p.setColor(Color.black);

			// The Text tool's icon has no picture other than the word itself, so it draws much larger than the other tool labels.
			final int textToolIconFontSize = 34;
			drawCenteredToolIconText(p, icons, textToolIconFontSize, text, 37);
		}
		return icons;
	}

	@Override
	protected void onAfterShowMap()
	{
		if ((isMoving || isRotating) && mousePressedLocation != null)
		{
			// A move or rotate is in progress. Reposition the preview from the current mouse location so it follows a zoom change made
			// mid-drag, which repaints the map without generating a mouse-move event.
			updateMoveOrRotatePreview(mapEditingPanel.getMousePosition());
		}
		else
		{
			updateHighlightsForMousePosition();
		}
	}

	@Override
	public void onSwitchingTo()
	{
		super.onSwitchingTo();
		updater.doWhenMapIsReadyForInteractions(() ->
		{
			if (isSelected())
			{
				updateHighlightsForMousePosition();
			}
		});
	}

	private void updateHighlightsForMousePosition()
	{
		if (!isMoving && !isRotating)
		{
			updateSelectionBoxes();
		}

		innerHandleMouseMovedOnMap(mapEditingPanel.getMousePosition());
		mapEditingPanel.repaint();
	}

	/*
	 * Selection
	 */

	/**
	 * Replaces the selection, saving any edit to the name of the text that was selected.
	 */
	private void setSelection(List<MapText> texts, SelectionFocus focusBehavior)
	{
		commitNameEdit();

		List<MapText> newSelection = new ArrayList<>();
		for (MapText text : texts)
		{
			if (text != null && !containsByIdentity(newSelection, text))
			{
				newSelection.add(text);
			}
		}
		selectedTexts = newSelection;
		groupBoxRI = null;

		mapEditingPanel.clearHighlightedAreas();
		if (selectedTexts.isEmpty())
		{
			triggerPurgeEmptyText();
			hideTextEditComponents();
			mapEditingPanel.clearTextBox();
		}
		else
		{
			showEditComponentsForSelection(focusBehavior);
			updateSelectionBoxes();
		}
		mapEditingPanel.repaint();
	}

	/**
	 * Whether a text box lies entirely off the map, where drawing the text deletes it.
	 */
	private boolean isEntirelyOffMap(RotatedRectangle box)
	{
		if (updater.mapParts == null || updater.mapParts.graph == null)
		{
			return false;
		}
		return TextDrawer.isEntirelyOffMap(box, updater.mapParts.graph);
	}

	/**
	 * Puts back into the edits any selected texts that a draw removed for being off the map, so that a change to them, such as moving them
	 * back onto the map, keeps them. A text still off the map after the change is removed again when it is drawn, so it is only gone for
	 * good once it is unselected there.
	 */
	private void restoreSelectedTextsRemovedFromEdits()
	{
		for (MapText text : selectedTexts)
		{
			if (!containsByIdentity(mainWindow.edits.text, text))
			{
				mainWindow.edits.text.add(text);
			}
		}
	}

	/**
	 * Shows the box with the move and rotate handles around the given text boxes, and, when there are several, a box around each of them.
	 * Boxes entirely off the map are drawn in red, since their texts will be deleted if unselected there.
	 */
	private void showTextBoxes(RotatedRectangle groupBox, List<RotatedRectangle> boxes)
	{
		List<RotatedRectangle> boxesOnMap = new ArrayList<>();
		List<RotatedRectangle> boxesOffMap = new ArrayList<>();
		for (RotatedRectangle box : boxes)
		{
			if (isEntirelyOffMap(box))
			{
				boxesOffMap.add(box);
			}
			else
			{
				boxesOnMap.add(box);
			}
		}
		boolean isGroupBoxInAValidPosition = !boxesOnMap.isEmpty() || boxes.isEmpty();
		if (boxes.size() == 1)
		{
			mapEditingPanel.setTextBoxesToDraw(groupBox, isGroupBoxInAValidPosition, new ArrayList<>(), new ArrayList<>());
		}
		else
		{
			mapEditingPanel.setTextBoxesToDraw(groupBox, isGroupBoxInAValidPosition, boxesOnMap, boxesOffMap);
		}
	}

	private static boolean containsByIdentity(List<MapText> texts, MapText text)
	{
		for (MapText candidate : texts)
		{
			if (candidate == text)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Saves an edit to the name of the selected text, if the name field differs from it.
	 */
	private void commitNameEdit()
	{
		if (selectedTexts.size() != 1 || !editTextFieldHider.isVisible())
		{
			return;
		}
		MapText text = selectedTexts.get(0);
		String value = editTextField.getText().trim();
		if (!value.equals(text.value))
		{
			restoreSelectedTextsRemovedFromEdits();
			MapText before = text.deepCopy();
			text.value = value;
			undoer.setUndoPoint(UpdateType.Incremental, this);
			updater.createAndShowMapIncrementalUsingText(Arrays.asList(before, text));
		}
	}

	private void showEditComponentsForSelection(SelectionFocus focusBehavior)
	{
		boolean isSingle = selectedTexts.size() == 1;
		isLoadingEditControls = true;
		try
		{
			editTextField.setText(isSingle ? selectedTexts.get(0).value : "");
			editTextFieldHider.setVisible(isSingle);

			editTextTypeComboBox.setSelectedItem(allTextsShare(text -> text.type) ? selectedTexts.get(0).type : null);
			editTextTypeHider.setVisible(true);

			editStyleHeadingHider.setVisible(true);
			editStyleControls.setVisible(true);
			editStyleControls.showStyles(getSelectedStyles());
			showOwnStyleParts();
			copyStyleToButton.setVisible(isSingle);
			styleButtonsHider.setVisible(true);

			editLayoutRows.setVisible(true);
			MapText first = selectedTexts.get(0);
			// Round rather than truncate. These values were stored as sliderValue / divider, and dividing then multiplying can land just
			// below the original integer, so truncating would drop the slider a step and the next save would persist that lower value.
			curvatureSliderWithDisplay.showValues(getValuesOfSelectedTexts(text -> (int) Math.round(text.curvature * curvatureSliderDivider)));
			spacingSliderWithDisplay.showValues(getValuesOfSelectedTexts(text -> text.spacing));
			lineBreakComboBox.setSelectedItem(allTextsShare(text -> text.lineBreak) ? first.lineBreak : null);

			copyPasteDeleteButtonsSeparatorHider.setVisible(true);
		}
		finally
		{
			isLoadingEditControls = false;
		}

		if (focusBehavior == SelectionFocus.EditField && isSingle && !editTextField.hasFocus())
		{
			editTextField.grabFocus();
		}
		else if (focusBehavior == SelectionFocus.ModeWidget)
		{
			// Don't auto-focus the edit field (would hijack Ctrl+C/V/Delete keyboard shortcuts). Place focus on the mode widget instead —
			// keeps it on a predictable component near the edit fields, and a single Tab press from here reaches the text edit field.
			modeWidget.grabFocusOnSelectedButton();
		}
		// SelectionFocus.Leave: don't move focus, so the user can Tab out of the edit field normally.

		updateFontCoverageWarning();
		if (getToolOptionsPanel() != null)
		{
			getToolOptionsPanel().revalidate();
			getToolOptionsPanel().repaint();
		}
	}

	private <T> boolean allTextsShare(java.util.function.Function<MapText, T> getValue)
	{
		T first = getValue.apply(selectedTexts.get(0));
		for (MapText text : selectedTexts)
		{
			if (!Objects.equals(first, getValue.apply(text)))
			{
				return false;
			}
		}
		return true;
	}

	private <T> List<T> getValuesOfSelectedTexts(java.util.function.Function<MapText, T> getValue)
	{
		List<T> values = new ArrayList<>(selectedTexts.size());
		for (MapText text : selectedTexts)
		{
			values.add(getValue.apply(text));
		}
		return values;
	}

	private List<TextStyle> getSelectedStyles()
	{
		List<TextStyle> styles = new ArrayList<>(selectedTexts.size());
		for (MapText text : selectedTexts)
		{
			styles.add(resolveStyle(text));
		}
		return styles;
	}

	/**
	 * The style of the given type of text, from the Fonts tab. It must not be modified.
	 */
	private TextStyle getTypeStyle(TextType type)
	{
		return mainWindow.themePanel.getTextStylesPanel().getStyle(type);
	}

	/**
	 * The style the given text is drawn with. It must not be modified.
	 */
	private TextStyle resolveStyle(MapText text)
	{
		return text.styleOverrides.resolve(getTypeStyle(text.type));
	}

	/**
	 * The parts of the style that any of the selected texts has its own values for.
	 */
	private Set<TextStylePart> getOwnPartsOfSelection()
	{
		Set<TextStylePart> parts = EnumSet.noneOf(TextStylePart.class);
		for (MapText text : selectedTexts)
		{
			parts.addAll(text.styleOverrides.getParts());
		}
		return parts;
	}

	/**
	 * Shows which parts of their style the selected texts have their own values for, on the reset button and by marking those parts'
	 * controls.
	 */
	private void showOwnStyleParts()
	{
		Set<TextStylePart> ownParts = getOwnPartsOfSelection();
		boolean isSameType = allTextsShare(text -> text.type);
		String typeName = selectedTexts.get(0).type.toString();

		resetStyleButton.setText(isSameType ? Translation.get("textTool.resetStyle", typeName) : Translation.get("textTool.resetStyle.mixedTypes"));
		resetStyleButton.setEnabled(!ownParts.isEmpty());

		Map<TextStylePart, String> tooltips = new EnumMap<>(TextStylePart.class);
		for (TextStylePart part : ownParts)
		{
			MapText owner = selectedTexts.stream().filter(text -> text.styleOverrides.has(part)).findFirst().get();
			tooltips.put(part, getOwnPartTooltip(part, owner.type));
		}
		editStyleControls.showOwnParts(ownParts, tooltips);
	}

	private String getOwnPartTooltip(TextStylePart part, TextType type)
	{
		TextStyle typeStyle = getTypeStyle(type);
		return switch (part)
		{
			case Font -> Translation.get("textTool.ownFont.tooltip", type.toString(), typeStyle.font.getName());
			case Size -> Translation.get("textTool.ownSize.tooltip", type.toString(), Math.round(typeStyle.font.getSize()));
			case Color -> Translation.get("textTool.ownColor.tooltip", type.toString());
			case Background -> Translation.get("textTool.ownBackground.tooltip", type.toString());
		};
	}

	private void hideTextEditComponents()
	{
		isLoadingEditControls = true;
		try
		{
			editTextField.setText("");
		}
		finally
		{
			isLoadingEditControls = false;
		}
		editTextFieldHider.setVisible(false);
		fontCoverageWarningHider.setVisible(false);
		editTextTypeHider.setVisible(false);
		editStyleHeadingHider.setVisible(false);
		editStyleControls.setVisible(false);
		styleButtonsHider.setVisible(false);
		editLayoutRows.setVisible(false);
		copyPasteDeleteButtonsSeparatorHider.setVisible(false);
	}

	/**
	 * Shows the box with the move and rotate handles around the selection, and a box around each selected text when there are several.
	 */
	private void updateSelectionBoxes()
	{
		if (selectedTexts.isEmpty() || !modeWidget.isEditMode())
		{
			mapEditingPanel.clearTextBox();
			return;
		}

		List<RotatedRectangle> boxes = new ArrayList<>();
		for (MapText text : selectedTexts)
		{
			RotatedRectangle box = getTextBox(text);
			if (box != null)
			{
				boxes.add(box);
			}
		}
		if (selectedTexts.size() == 1)
		{
			showTextBoxes(boxes.isEmpty() ? null : boxes.get(0), boxes);
			return;
		}
		showTextBoxes(getGroupBox(), boxes);
	}

	/**
	 * The box with the move and rotate handles around several selected texts, in graph coordinates, or null when none of them has been
	 * drawn. Its pivot is its center.
	 */
	private RotatedRectangle getGroupBox()
	{
		if (groupBoxRI != null)
		{
			return scaleBox(groupBoxRI, mainWindow.displayQualityScale);
		}
		Rectangle groupBounds = getGroupBounds();
		return groupBounds == null ? null : new RotatedRectangle(groupBounds);
	}

	private static RotatedRectangle scaleBox(RotatedRectangle box, double scale)
	{
		return new RotatedRectangle(box.x * scale, box.y * scale, box.width * scale, box.height * scale, box.angle, box.pivotX * scale, box.pivotY * scale);
	}

	private static RotatedRectangle getTextBox(MapText text)
	{
		if (text.line1Bounds == null)
		{
			return null;
		}
		return text.line1Bounds.addRotatedRectangleThatHasTheSameAngleAndPivot(text.line2Bounds);
	}

	/**
	 * The smallest rectangle that holds every selected text's box, in graph coordinates, or null when none of them has been drawn.
	 */
	private Rectangle getGroupBounds()
	{
		Rectangle result = null;
		for (MapText text : selectedTexts)
		{
			RotatedRectangle box = getTextBox(text);
			if (box != null)
			{
				result = box.getBounds().add(result);
			}
		}
		return result;
	}

	public void changeToEditModeAndSelectText(MapText selectedText, SelectionFocus focusBehavior)
	{
		// Only ModeWidget wants focus on the mode button; EditField overrides it below, and Leave keeps focus untouched.
		modeWidget.selectEditMode(focusBehavior == SelectionFocus.ModeWidget);
		setSelection(selectedText == null ? new ArrayList<>() : Arrays.asList(selectedText), focusBehavior);
	}

	/**
	 * The text being edited, when exactly one is selected.
	 */
	public MapText getTextBeingEdited()
	{
		if (modeWidget.isEditMode() && selectedTexts.size() == 1)
		{
			return selectedTexts.get(0);
		}
		return null;
	}

	/*
	 * Changes to selected texts
	 */

	/**
	 * Makes a change to every selected text, sets an undo point, and redraws them.
	 */
	private void applyToSelectedTexts(Consumer<MapText> change)
	{
		applyToSelectedTexts(change, true);
	}

	/**
	 * @param updateSelectionBoxesNow
	 *            Whether to redraw the selection boxes from the texts' bounds now. A move or rotation passes false to leave the boxes where
	 *            its preview put them, since the bounds aren't updated until the map finishes redrawing, which redraws the boxes.
	 */
	private void applyToSelectedTexts(Consumer<MapText> change, boolean updateSelectionBoxesNow)
	{
		if (selectedTexts.isEmpty())
		{
			return;
		}
		commitNameEdit();
		restoreSelectedTextsRemovedFromEdits();
		groupBoxRI = null;
		List<MapText> textsToRedraw = new ArrayList<>(selectedTexts.size() * 2);
		for (MapText text : selectedTexts)
		{
			textsToRedraw.add(text.deepCopy());
			change.accept(text);
			textsToRedraw.add(text);
		}
		undoer.setUndoPoint(UpdateType.Incremental, this);
		updater.createAndShowMapIncrementalUsingText(textsToRedraw);
		showEditComponentsForSelection(SelectionFocus.Leave);
		if (updateSelectionBoxesNow)
		{
			updateSelectionBoxes();
		}
		mapEditingPanel.repaint();
	}

	private void rotateSelectedTextToHorizontal()
	{
		applyToSelectedTexts(text -> text.angle = 0);
	}

	private void showResetStyleDialog()
	{
		Set<TextStylePart> ownParts = getOwnPartsOfSelection();
		if (selectedTexts.isEmpty() || ownParts.isEmpty())
		{
			return;
		}
		commitNameEdit();
		new ResetTextStyleDialog(mainWindow, ownParts, parts -> applyToSelectedTexts(text ->
		{
			for (TextStylePart part : parts)
			{
				text.styleOverrides.clear(part);
			}
		})).setVisible(true);
	}

	private void showCopyStyleDialog()
	{
		if (selectedTexts.size() != 1)
		{
			return;
		}
		commitNameEdit();
		MapText source = selectedTexts.get(0);
		TextStyle sourceStyle = resolveStyle(source).copy();
		new CopyTextStyleDialog(mainWindow, source.type, source.styleOverrides.getParts(), choice ->
		{
			restoreSelectedTextsRemovedFromEdits();
			mainWindow.themePanel.getTextStylesPanel().copyStyle(sourceStyle, choice);
		}).setVisible(true);
	}

	/*
	 * Add mode
	 */

	private void generateNameForAdds()
	{
		generateNameForAdds(null);
	}

	/**
	 * @param onGenerated
	 *            Called with the name once it is generated, or null.
	 */
	private void generateNameForAdds(Consumer<String> onGenerated)
	{
		updater.doWhenMapIsNotDrawing(() ->
		{
			if (updater.mapParts != null && updater.mapParts.nameCreator != null)
			{
				lastGeneratedNameForAdds = updater.mapParts.nameCreator.generateNameOfTypeForTextEditor(textTypeForAdds);
				addNameField.setText(lastGeneratedNameForAdds);
				if (onGenerated != null)
				{
					onGenerated.accept(lastGeneratedNameForAdds);
				}
			}
		});
	}

	/**
	 * A piece of text that was added, with the name to add before adding it and the name generated after, so that undoing the add brings back
	 * the name it added, and redoing it brings back the name generated after.
	 */
	private static final class NamesAroundAdd
	{
		/** A copy of the text added, which equals the text on the map while the add isn't undone. */
		final MapText addedText;
		final String before;
		final boolean wasBeforeGenerated;
		/** Null until the name is generated. */
		String after;

		NamesAroundAdd(MapText addedText, String before, boolean wasBeforeGenerated)
		{
			this.addedText = addedText;
			this.before = before;
			this.wasBeforeGenerated = wasBeforeGenerated;
		}
	}

	/**
	 * Shows the name to add that goes with whether the added text is on the map: the name generated after it if so, and the name it added if
	 * not. Only a name to add that shows the other of the two is changed, so a name the user typed since is left alone, and running this
	 * again changes nothing.
	 */
	private void showNameForAddsAround(NamesAroundAdd names)
	{
		if (names.after == null)
		{
			return;
		}
		boolean isAdded = mainWindow.edits.text.contains(names.addedText);
		String current = addNameField.getText();
		if (isAdded && current.equals(names.before))
		{
			lastGeneratedNameForAdds = names.after;
			addNameField.setText(names.after);
		}
		else if (!isAdded && current.equals(names.after))
		{
			lastGeneratedNameForAdds = names.wasBeforeGenerated ? names.before : null;
			addNameField.setText(names.before);
		}
	}

	/**
	 * Replaces the name to add with a newly generated one, unless the user typed it. The check happens when the name is generated, which
	 * waits for any draw in progress or queued, so that the name comes from the books of the map being drawn.
	 */
	private void regenerateNameForAddsUnlessTyped()
	{
		updater.doWhenMapIsNotDrawing(() ->
		{
			String name = addNameField.getText();
			if (name.trim().isEmpty() || name.equals(lastGeneratedNameForAdds))
			{
				generateNameForAdds();
			}
		});
	}

	/**
	 * Shows the name to add where it would be placed under the mouse.
	 */
	private void updateAddModeHoverPreview(java.awt.Point mouseLocation)
	{
		TextStyle style = getTypeStyle(textTypeForAdds);
		String name = addNameField.getText().trim();
		if (mouseLocation == null || style == null || name.isEmpty() || updater.mapParts == null || updater.mapParts.graph == null)
		{
			mapEditingPanel.clearTextHoverPreview();
			return;
		}

		double resolution = mainWindow.displayQualityScale;
		boolean drawRegionBoundaries = mainWindow.themePanel.isDrawRegionBoundariesSelected();
		if (hoverPreviewTextDrawer == null || hoverPreviewTextDrawerResolution != resolution || hoverPreviewTextDrawerDrawsRegionBoundaries != drawRegionBoundaries)
		{
			hoverPreviewSettings = new MapSettings();
			hoverPreviewSettings.resolution = resolution;
			// Whether the map draws region boundaries decides whether region text is split onto two lines where it would cross one.
			hoverPreviewSettings.drawRegionBoundaries = drawRegionBoundaries;
			hoverPreviewTextDrawer = new TextDrawer(hoverPreviewSettings);
			hoverPreviewTextDrawerResolution = resolution;
			hoverPreviewTextDrawerDrawsRegionBoundaries = drawRegionBoundaries;
		}
		hoverPreviewSettings.textStyleDefaults.put(textTypeForAdds, style);

		nortantis.geom.Point graphPoint = getPointOnGraph(mouseLocation);
		MapText text = TextDrawer.createMapText(name, graphPoint, 0.0, textTypeForAdds, resolution, backgroundSeedForNextAdd);
		Tuple2<Image, IntPoint> drawn = hoverPreviewTextDrawer.drawTextOntoNewImage(text, updater.mapParts.graph);
		if (drawn == null)
		{
			mapEditingPanel.clearTextHoverPreview();
			return;
		}
		try (Image image = drawn.getFirst())
		{
			mapEditingPanel.setTextHoverPreview(image, drawn.getSecond());
		}
	}

	private void addTextAt(java.awt.Point mouseLocation)
	{
		// This is deferred if the map is currently drawing so that we don't try to generate text while the text drawer is reprocessing books
		// after a book checkbox was checked.
		updater.doWhenMapIsNotDrawing(() ->
		{
			if (!modeWidget.isDrawMode())
			{
				return;
			}
			String name = addNameField.getText().trim();
			if (name.isEmpty())
			{
				return;
			}
			MapText addedText = TextDrawer.createMapText(name, getPointOnGraph(mouseLocation), 0.0, textTypeForAdds, mainWindow.displayQualityScale,
					backgroundSeedForNextAdd);
			mainWindow.edits.text.add(addedText);
			NamesAroundAdd names = new NamesAroundAdd(addedText.deepCopy(), addNameField.getText(), addNameField.getText().equals(lastGeneratedNameForAdds));
			undoer.setUndoPoint(UpdateType.Incremental, this, () -> showNameForAddsAround(names));
			changeToEditModeAndSelectText(addedText, SelectionFocus.EditField);
			updater.createAndShowMapIncrementalUsingText(Arrays.asList(addedText));
			backgroundSeedForNextAdd = new Random().nextLong();
			// The next name is generated right away so that adding again doesn't add the same name twice.
			generateNameForAdds(generatedName -> names.after = generatedName);
		});
	}

	/*
	 * Mouse handling
	 */

	@Override
	protected void handleMousePressedOnMap(MouseEvent e)
	{
		if (drawTextDisabledLabel.isVisible())
		{
			return;
		}

		isRotating = false;
		isMoving = false;
		isSelectingWithBrush = false;

		if (modeWidget.isEraseMode())
		{
			deleteTexts(e.getPoint());
		}
		else if (modeWidget.isDrawMode())
		{
			isAddModeHoverPreviewHidden = true;
			mapEditingPanel.clearTextHoverPreview();
			mapEditingPanel.repaint();
			addTextAt(e.getPoint());
		}
		else if (modeWidget.isEditMode())
		{
			if (!selectedTexts.isEmpty() && mapEditingPanel.isInRotateTool(e.getPoint()))
			{
				isRotating = true;
				mousePressedLocation = getPointOnGraph(e.getPoint());
				RotatedRectangle groupBox = getGroupBox();
				groupRotationCenter = groupBox == null ? null : groupBox.getPivot();
			}
			else if (!selectedTexts.isEmpty() && mapEditingPanel.isInMoveTool(e.getPoint()))
			{
				isMoving = true;
				mousePressedLocation = getPointOnGraph(e.getPoint());
			}
			else if (e.isAltDown())
			{
				SelectionCycling.recordAltClick();
				MapText next = SelectionCycling.chooseNext(getSelectionCycleCandidates(e.getPoint()), selectedTexts);
				setSelection(next == null ? new ArrayList<>() : Arrays.asList(next), SelectionFocus.ModeWidget);
				updateSelectionCycleCandidates(e.getPoint());
			}
			else
			{
				isSelectingWithBrush = true;
				selectWithBrush(e, true);
			}
		}
	}

	/**
	 * Selects the texts under the brush. With the command key held, adds them to or removes them from the selection, depending on the
	 * Ctrl+click setting. Without it, a press replaces the selection and a drag adds to it.
	 */
	private void selectWithBrush(MouseEvent e, boolean isPress)
	{
		List<MapText> underBrush = getMapTextsSelectedByCurrentBrushSizeAndShowBrush(e.getPoint());
		List<MapText> newSelection;
		if (SwingHelper.isCommandKeyDown(e))
		{
			newSelection = new ArrayList<>(selectedTexts);
			if (controlClickBehavior.isSelectMode())
			{
				newSelection.addAll(underBrush);
			}
			else
			{
				newSelection.removeIf(text -> containsByIdentity(underBrush, text));
			}
		}
		else if (isPress)
		{
			newSelection = underBrush;
		}
		else
		{
			newSelection = new ArrayList<>(selectedTexts);
			newSelection.addAll(underBrush);
		}

		if (!isSameSelection(newSelection))
		{
			// Don't grab focus on selection — selecting a text is a visual selection (like clicking an icon), not the same as starting to
			// edit it. This keeps ctrl+C / ctrl+V / DELETE shortcuts targeting the selected MapText objects instead of being swallowed by the
			// text-edit field. Users click into the text field to start typing.
			setSelection(newSelection, isPress ? SelectionFocus.ModeWidget : SelectionFocus.Leave);
		}
	}

	private boolean isSameSelection(List<MapText> texts)
	{
		List<MapText> distinct = new ArrayList<>();
		for (MapText text : texts)
		{
			if (!containsByIdentity(distinct, text))
			{
				distinct.add(text);
			}
		}
		if (distinct.size() != selectedTexts.size())
		{
			return false;
		}
		for (MapText text : distinct)
		{
			if (!containsByIdentity(selectedTexts, text))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * The texts under the given point, in the order Alt+click cycles through them: the one a plain click selects first.
	 */
	private List<MapText> getSelectionCycleCandidates(java.awt.Point mouseLocation)
	{
		if (mouseLocation == null || mainWindow.edits == null)
		{
			return new ArrayList<>();
		}
		List<MapText> candidates = new ArrayList<>(mainWindow.edits.findAllTextAtPoint(getPointOnGraph(mouseLocation)));
		// Lowest top first, matching MapEdits.findTextPicked. The sort is stable, so ties keep their draw order.
		candidates.sort((t1, t2) -> -Double.compare(t1.line1Bounds == null ? Double.POSITIVE_INFINITY : t1.line1Bounds.y,
				t2.line1Bounds == null ? Double.POSITIVE_INFINITY : t2.line1Bounds.y));
		return candidates;
	}

	/**
	 * Outlines what Alt+click can select under the mouse while Alt is held in Edit mode.
	 */
	private void updateSelectionCycleCandidates(java.awt.Point mouseLocation)
	{
		if (!SelectionCycling.isAltDown() || !modeWidget.isEditMode() || mouseLocation == null || drawTextDisabledLabel.isVisible())
		{
			mapEditingPanel.clearSelectionCycleCandidateAreas();
			return;
		}
		List<RotatedRectangle> boxes = new ArrayList<>();
		for (MapText text : getSelectionCycleCandidates(mouseLocation))
		{
			RotatedRectangle box = getTextBox(text);
			if (box != null)
			{
				boxes.add(box);
			}
		}
		mapEditingPanel.setSelectionCycleCandidateAreas(boxes);
	}

	private void deleteTexts(java.awt.Point mouseLocation)
	{
		List<MapText> mapTextsSelected = getMapTextsSelectedByCurrentBrushSizeAndShowBrush(mouseLocation);
		mapEditingPanel.setHighlightedAreasFromTexts(mapTextsSelected);
		mapEditingPanel.repaint();
		if (mapTextsSelected == null || mapTextsSelected.isEmpty())
		{
			return;
		}

		List<MapText> before = new ArrayList<>();
		for (MapText text : mapTextsSelected)
		{
			before.add(text.deepCopy());
			text.value = "";
		}
		List<MapText> textsToRedraw = new ArrayList<>(before);
		textsToRedraw.addAll(mapTextsSelected);
		updater.createAndShowMapIncrementalUsingText(textsToRedraw);
		triggerPurgeEmptyText();
	}

	private void copySelectedText()
	{
		if (!modeWidget.isEditMode() || selectedTexts.isEmpty())
		{
			return;
		}
		commitNameEdit();
		textClipboard = new ArrayList<>();
		for (MapText text : selectedTexts)
		{
			textClipboard.add(text.deepCopy());
		}
	}

	private void pasteText()
	{
		if (textClipboard == null || textClipboard.isEmpty())
		{
			return;
		}

		// Paste keeps the copied texts' positions relative to each other, centered at the current mouse location. MapText.location is
		// stored in resolution-invariant pixels (graph pixels / displayQualityScale — see TextDrawer.createMapText), so the graph-pixel
		// mouse location is divided by displayQualityScale here. When the mouse is off-map, each copy is placed a fixed offset from its
		// original so the new text is visible rather than landing exactly on top of the original.
		nortantis.geom.Point offset;
		java.awt.Point mouseOnPanel = mapEditingPanel.getMousePosition();
		if (mouseOnPanel != null)
		{
			nortantis.geom.Point mouseGraph = getPointOnGraph(mouseOnPanel);
			nortantis.geom.Point mouseRI = new nortantis.geom.Point(mouseGraph.x / mainWindow.displayQualityScale, mouseGraph.y / mainWindow.displayQualityScale);
			nortantis.geom.Point center = getCenterOfLocations(textClipboard);
			offset = mouseRI.subtract(center);
		}
		else
		{
			final double offsetDistance = 50.0;
			offset = new nortantis.geom.Point(offsetDistance, offsetDistance);
		}

		List<MapText> pasted = new ArrayList<>();
		for (MapText copied : textClipboard)
		{
			MapText text = copied.deepCopy();
			text.location = text.location.add(offset);
			text.line1Bounds = null;
			text.line2Bounds = null;
			mainWindow.edits.text.add(text);
			pasted.add(text);
		}

		undoer.setUndoPoint(UpdateType.Incremental, this);
		updater.createAndShowMapIncrementalUsingText(pasted);
		setSelection(pasted, SelectionFocus.ModeWidget);
	}

	private static nortantis.geom.Point getCenterOfLocations(List<MapText> texts)
	{
		double x = 0;
		double y = 0;
		for (MapText text : texts)
		{
			x += text.location.x;
			y += text.location.y;
		}
		return new nortantis.geom.Point(x / texts.size(), y / texts.size());
	}

	private void deleteSelectedText()
	{
		if (!modeWidget.isEditMode() || selectedTexts.isEmpty())
		{
			return;
		}
		List<MapText> textsToRedraw = new ArrayList<>();
		for (MapText text : selectedTexts)
		{
			textsToRedraw.add(text.deepCopy());
			// Match the erase-mode pattern: clear the value and let purgeEmptyText reap the entry once drawing is idle. Going through the same
			// code path means undo/redo handling stays uniform.
			text.value = "";
			textsToRedraw.add(text);
		}
		// Sync the edit field to the cleared value before clearing the selection, so that saving the field's contents doesn't undo the
		// delete.
		editTextField.setText("");
		setSelection(new ArrayList<>(), SelectionFocus.Leave);
		undoer.setUndoPoint(UpdateType.Incremental, this);
		triggerPurgeEmptyText();
		updater.createAndShowMapIncrementalUsingText(textsToRedraw);
	}

	@Override
	protected void handleMouseRightPressedOnMap(MouseEvent e)
	{
		if (!modeWidget.isEditMode())
		{
			return;
		}
		if (drawTextDisabledLabel.isVisible())
		{
			return;
		}

		// If right-click is on a text that isn't selected, select it first so the menu acts on what the user pointed at. Holding the
		// command key adds it to the selection instead. Don't grab focus — focus would route ctrl+C/V/DELETE to the text-edit field instead
		// of the MapText.
		//
		// Exception: if the right-click landed inside the box around the selection, keep that selection rather than switching to whatever
		// text happens to be under the cursor. Another text can sit under the same box, and swapping the selection on right-click would
		// surprise the user. (Matches the Icons tool's isPointInsideMultiIconSelectionBox behavior.)
		nortantis.geom.Point graphPoint = getPointOnGraph(e.getPoint());
		boolean insideSelectionBox = false;
		if (!SwingHelper.isCommandKeyDown(e))
		{
			if (selectedTexts.size() == 1)
			{
				RotatedRectangle box = getTextBox(selectedTexts.get(0));
				insideSelectionBox = box != null && box.contains(graphPoint);
			}
			else if (selectedTexts.size() > 1)
			{
				RotatedRectangle groupBox = getGroupBox();
				insideSelectionBox = groupBox != null && groupBox.contains(graphPoint);
			}
		}
		if (!insideSelectionBox)
		{
			MapText underCursor = mainWindow.edits.findTextPicked(graphPoint);
			if (underCursor != null && !containsByIdentity(selectedTexts, underCursor))
			{
				List<MapText> newSelection = SwingHelper.isCommandKeyDown(e) ? new ArrayList<>(selectedTexts) : new ArrayList<>();
				newSelection.add(underCursor);
				setSelection(newSelection, SelectionFocus.ModeWidget);
			}
		}

		boolean hasSelection = !selectedTexts.isEmpty();
		boolean hasClipboard = textClipboard != null && !textClipboard.isEmpty();
		if (!hasSelection && !hasClipboard)
		{
			return;
		}

		JPopupMenu menu = new JPopupMenu();

		JMenuItem copyItem = new JMenuItem(Translation.get("textTool.copy"));
		copyItem.setEnabled(hasSelection);
		copyItem.addActionListener(ev -> copySelectedText());
		menu.add(copyItem);

		JMenuItem pasteItem = new JMenuItem(Translation.get("textTool.paste"));
		pasteItem.setEnabled(hasClipboard);
		pasteItem.addActionListener(ev -> pasteText());
		menu.add(pasteItem);

		JMenuItem deleteItem = new JMenuItem(Translation.get("textTool.delete"));
		deleteItem.setEnabled(hasSelection);
		deleteItem.addActionListener(ev -> deleteSelectedText());
		menu.add(deleteItem);

		JMenuItem rotateItem = new JMenuItem(Translation.get("textTool.rotateToHorizontal"));
		rotateItem.setEnabled(hasSelection);
		rotateItem.addActionListener(ev -> rotateSelectedTextToHorizontal());
		menu.add(rotateItem);

		menu.show(e.getComponent(), e.getX(), e.getY());
	}

	@Override
	protected void handleMouseDraggedOnMap(MouseEvent e)
	{
		if (drawTextDisabledLabel.isVisible())
		{
			return;
		}

		if (modeWidget.isEditMode() && (isMoving || isRotating))
		{
			updateMoveOrRotatePreview(e.getPoint());
		}
		else if (modeWidget.isEditMode() && isSelectingWithBrush)
		{
			selectWithBrush(e, false);
		}
		else if (modeWidget.isEraseMode())
		{
			deleteTexts(e.getPoint());
		}
	}

	/**
	 * Recomputes the in-progress move/rotate preview boxes from the current mouse location and the graph-coordinate press point. Called on
	 * each drag event, and also from {@link #onAfterShowMap()} so the preview follows a zoom change made mid-drag without requiring a mouse
	 * move.
	 */
	private void updateMoveOrRotatePreview(java.awt.Point mouseLocation)
	{
		if (mouseLocation == null || mousePressedLocation == null || selectedTexts.isEmpty())
		{
			return;
		}

		List<RotatedRectangle> previewBoxes = new ArrayList<>();
		RotatedRectangle previewGroupBox = getGroupBox();
		if (isMoving)
		{
			nortantis.geom.Point graphPointMouseLocation = getPointOnGraph(mouseLocation);
			int deltaX = (int) (graphPointMouseLocation.x - mousePressedLocation.x);
			int deltaY = (int) (graphPointMouseLocation.y - mousePressedLocation.y);
			nortantis.geom.Point delta = new nortantis.geom.Point(deltaX, deltaY);
			for (MapText text : selectedTexts)
			{
				RotatedRectangle box = getTextBox(text);
				if (box != null)
				{
					previewBoxes.add(box.translate(delta));
				}
			}
			previewGroupBox = previewGroupBox == null ? null : previewGroupBox.translate(delta);
		}
		else if (isRotating)
		{
			if (selectedTexts.size() == 1)
			{
				MapText text = selectedTexts.get(0);
				double angle = calcRotationAngle(text, mouseLocation);
				RotatedRectangle box = getTextBox(text);
				if (box != null)
				{
					previewBoxes.add(box.rotateTo(angle));
				}
			}
			else if (groupRotationCenter != null)
			{
				double delta = calcGroupRotationAngle(mouseLocation);
				for (MapText text : selectedTexts)
				{
					RotatedRectangle box = getTextBox(text);
					if (box != null)
					{
						previewBoxes.add(rotateBoxAbout(box, groupRotationCenter, delta));
					}
				}
				previewGroupBox = previewGroupBox == null ? null : rotateBoxAbout(previewGroupBox, groupRotationCenter, delta);
			}
		}

		if (previewBoxes.isEmpty())
		{
			return;
		}
		if (previewBoxes.size() == 1)
		{
			showTextBoxes(previewBoxes.get(0), previewBoxes);
		}
		else
		{
			showTextBoxes(previewGroupBox, previewBoxes);
		}
		mapEditingPanel.repaint();
	}

	/**
	 * A text's box rotated with the text when the text rotates by delta about center.
	 */
	private static RotatedRectangle rotateBoxAbout(RotatedRectangle box, nortantis.geom.Point center, double delta)
	{
		nortantis.geom.Point pivot = box.getPivot();
		nortantis.geom.Point newPivot = pivot.rotate(center, delta);
		return box.translate(newPivot.subtract(pivot)).rotateTo(box.angle + delta);
	}

	/**
	 * The angle a single text is rotated to by dragging the rotation handle.
	 */
	private double calcRotationAngle(MapText text, java.awt.Point mouseLocation)
	{
		nortantis.geom.Point graphPointMouseLocation = getPointOnGraph(mouseLocation);
		nortantis.geom.Point graphPointMousePressedLocation = mousePressedLocation;

		// Find the bounding box currently displayed
		RotatedRectangle boundingBox = text.line1Bounds.addRotatedRectangleThatHasTheSameAngleAndPivot(text.line2Bounds);

		// Find the angle between the mouse-down point with respect to the bounding box.
		nortantis.geom.Point rotatedMouseDownPoint = graphPointMousePressedLocation.rotate(boundingBox.getPivot(), -boundingBox.angle);
		double yDiffFromPivot = (boundingBox.y + boundingBox.height / 2.0) - boundingBox.pivotY;
		double mouseDownAngleWithRespectToBounds = Math.atan2(rotatedMouseDownPoint.y - boundingBox.pivotY - yDiffFromPivot, rotatedMouseDownPoint.x - boundingBox.pivotX);

		// Find the angle between the edge of the bounding box where the rotation tool is and the edge of the bounding box where the
		// rotation tool would be if it were aligned with the pivot. These can be different when text is curved.
		// This y distance between the center of the rotation tool and the pivot when the text box is horizontal.
		double xDiffFromMouseDownToEdgeOfBoundsWithRespectToBounds = rotatedMouseDownPoint.x - (boundingBox.x + boundingBox.width);
		double angleToRotateTool = Math.atan2(yDiffFromPivot, (boundingBox.width / 2.0) + xDiffFromMouseDownToEdgeOfBoundsWithRespectToBounds);

		double centerX = text.location.x * mainWindow.displayQualityScale;
		double centerY = text.location.y * mainWindow.displayQualityScale;
		double angle = Math.atan2(graphPointMouseLocation.y - centerY, graphPointMouseLocation.x - centerX) - mouseDownAngleWithRespectToBounds - angleToRotateTool;
		return angle;
	}

	/**
	 * How far several texts rotate together, about the center of the box around them, from where the mouse was pressed to the given
	 * location.
	 */
	private double calcGroupRotationAngle(java.awt.Point mouseLocation)
	{
		nortantis.geom.Point graphPointMouseLocation = getPointOnGraph(mouseLocation);
		double pressedAngle = Math.atan2(mousePressedLocation.y - groupRotationCenter.y, mousePressedLocation.x - groupRotationCenter.x);
		double currentAngle = Math.atan2(graphPointMouseLocation.y - groupRotationCenter.y, graphPointMouseLocation.x - groupRotationCenter.x);
		return currentAngle - pressedAngle;
	}

	@Override
	protected void handleMouseReleasedOnMap(MouseEvent e)
	{
		if (drawTextDisabledLabel.isVisible())
		{
			return;
		}

		isSelectingWithBrush = false;
		if (!selectedTexts.isEmpty() && modeWidget.isEditMode())
		{
			if (isMoving)
			{
				nortantis.geom.Point graphPointMouseLocation = getPointOnGraph(e.getPoint());
				// Divide the translation by mainWindow.displayQualityScale because MapText locations are stored as if the map is generated at
				// 100% resolution.
				nortantis.geom.Point translation = new nortantis.geom.Point((int) ((graphPointMouseLocation.x - mousePressedLocation.x) / mainWindow.displayQualityScale),
						(int) ((graphPointMouseLocation.y - mousePressedLocation.y) / mainWindow.displayQualityScale));
				isMoving = false;
				RotatedRectangle movedGroupBoxRI = groupBoxRI == null ? null : groupBoxRI.translate(translation);
				applyToSelectedTexts(text -> text.location = new nortantis.geom.Point(text.location.x + translation.x, text.location.y + translation.y), false);
				groupBoxRI = movedGroupBoxRI;
			}
			else if (isRotating)
			{
				isRotating = false;
				if (selectedTexts.size() == 1)
				{
					double angle = calcRotationAngle(selectedTexts.get(0), e.getPoint());
					applyToSelectedTexts(text -> text.angle = angle, false);
				}
				else if (groupRotationCenter != null)
				{
					double delta = calcGroupRotationAngle(e.getPoint());
					double scale = mainWindow.displayQualityScale;
					nortantis.geom.Point centerRI = new nortantis.geom.Point(groupRotationCenter.x / scale, groupRotationCenter.y / scale);
					RotatedRectangle groupBox = getGroupBox();
					RotatedRectangle rotatedGroupBoxRI = groupBox == null ? null : scaleBox(rotateBoxAbout(groupBox, groupRotationCenter, delta), 1.0 / scale);
					applyToSelectedTexts(text ->
					{
						text.location = text.location.rotate(centerRI, delta);
						text.angle += delta;
					}, false);
					groupBoxRI = rotatedGroupBoxRI;
				}
			}
		}

		if (modeWidget.isEraseMode())
		{
			mapEditingPanel.clearHighlightedAreas();
			mapEditingPanel.repaint();

			// This won't actually set an undo point unless text was deleted because Undoer is smart enough to discard undo points that didn't
			// change anything.
			undoer.setUndoPoint(UpdateType.Incremental, this);
		}
	}

	private void triggerPurgeEmptyText()
	{
		if (updater != null)
		{
			updater.doWhenMapIsNotDrawing(() ->
			{
				if (mainWindow.edits != null && mainWindow.edits.isInitialized())
				{
					mainWindow.edits.purgeEmptyText();
				}
			});
		}
	}

	@Override
	public void onSwitchingAway()
	{
		// Keep any text edits being done and clear the selected texts.
		if (modeWidget.isEditMode())
		{
			setSelection(new ArrayList<>(), SelectionFocus.Leave);
		}

		mapEditingPanel.hideBrush();
		mapEditingPanel.clearHighlightedAreas();
		mapEditingPanel.clearTextBox();
		mapEditingPanel.clearProcessingAreas();
		mapEditingPanel.clearSelectionCycleCandidateAreas();
		mapEditingPanel.clearTextHoverPreview();
		mapEditingPanel.repaint();
	}

	@Override
	protected void onBeforeUndoRedo()
	{
		// Create an undo point for any current changes.
		commitNameEdit();
	}

	@Override
	protected void onAfterUndoRedo()
	{
		// The undo replaced the texts with copies, so the selected ones no longer exist.
		selectedTexts = new ArrayList<>();
		groupBoxRI = null;
		hideTextEditComponents();
		mapEditingPanel.clearTextBox();
	}

	@Override
	protected void handleMouseMovedOnMap(MouseEvent e)
	{
		isAddModeHoverPreviewHidden = false;
		innerHandleMouseMovedOnMap(e.getPoint());
	}

	private void innerHandleMouseMovedOnMap(java.awt.Point mouseLocation)
	{
		if (mouseLocation == null)
		{
			return;
		}

		if (drawTextDisabledLabel.isVisible())
		{
			return;
		}

		if (modeWidget.isEraseMode())
		{
			List<MapText> mapTextsSelected = getMapTextsSelectedByCurrentBrushSizeAndShowBrush(mouseLocation);
			mapEditingPanel.setHighlightedAreasFromTexts(mapTextsSelected);
		}
		else if (modeWidget.isEditMode())
		{
			if (mapEditingPanel.isInMoveTool(mouseLocation) || mapEditingPanel.isInRotateTool(mouseLocation))
			{
				mapEditingPanel.hideBrush();
				mapEditingPanel.clearHighlightedAreas();
			}
			else
			{
				List<MapText> mapTextsSelected = getMapTextsSelectedByCurrentBrushSizeAndShowBrush(mouseLocation);
				mapEditingPanel.setHighlightedAreasFromTexts(mapTextsSelected);
			}
			updateSelectionCycleCandidates(mouseLocation);
		}
		else if (modeWidget.isDrawMode())
		{
			mapEditingPanel.hideBrush();
			if (isAddModeHoverPreviewHidden)
			{
				mapEditingPanel.clearTextHoverPreview();
			}
			else
			{
				updateAddModeHoverPreview(mouseLocation);
			}
		}
		mapEditingPanel.repaint();
	}

	private int getBrushDiameter()
	{
		return brushSizes.get(brushSizeComboBox.getSelectedIndex());
	}

	private List<MapText> getMapTextsSelectedByCurrentBrushSizeAndShowBrush(java.awt.Point mouseLocation)
	{
		List<MapText> mapTextsSelected = null;
		int brushDiameter = getBrushDiameter();
		if (brushDiameter > 1)
		{
			mapEditingPanel.showBrush(mouseLocation, brushDiameter);
			mapTextsSelected = mainWindow.edits.findTextSelectedByBrush(getPointOnGraph(mouseLocation), panelDistanceToGraphPixels(brushDiameter));
		}
		else
		{
			mapEditingPanel.hideBrush();
			MapText selected = mainWindow.edits.findTextPicked(getPointOnGraph(mouseLocation));
			if (selected != null)
			{
				mapTextsSelected = Collections.singletonList(selected);
			}
		}

		mapEditingPanel.repaint();
		return mapTextsSelected == null ? new ArrayList<>() : mapTextsSelected;
	}

	@Override
	protected void handleMouseExitedMap(MouseEvent e)
	{
		mapEditingPanel.hideBrush();
		mapEditingPanel.clearHighlightedAreas();
		mapEditingPanel.clearSelectionCycleCandidateAreas();
		mapEditingPanel.clearTextHoverPreview();
		mapEditingPanel.repaint();
	}

	@Override
	public void loadSettingsIntoGUI(MapSettings settings, boolean isUndoRedoOrAutomaticChange, boolean refreshImagePreviews)
	{
		// I'm excluding this when isUndoRedoOrAutomaticChange=false because I don't think undue redo should change the book selection,
		// since changing the book selection doesn't change the map.
		if (!isUndoRedoOrAutomaticChange)
		{
			booksWidget.checkSelectedBooks(settings.books);
		}

		// A change to the type styles changes the styles of the selected texts.
		if (!Objects.equals(settings.textStyleDefaults, typeStylesShown))
		{
			typeStylesShown = settings.copyTextStyleDefaults();
			if (modeWidget.isEditMode() && !selectedTexts.isEmpty())
			{
				showEditComponentsForSelection(SelectionFocus.Leave);
			}
		}

		handleEnablingAndDisabling(settings);
		drawTextDisabledLabelHider.setVisible(!settings.drawText);
		if (!settings.drawText)
		{
			if (modeWidget.isEditMode())
			{
				setSelection(new ArrayList<>(), SelectionFocus.Leave);
			}

			mapEditingPanel.clearAllToolSpecificSelectionsAndHighlights();
			mapEditingPanel.repaint();
		}
	}

	@Override
	public void getSettingsFromGUI(MapSettings settings)
	{
		settings.books = booksWidget.getSelectedBooks();
	}

	@Override
	public void handleEnablingAndDisabling(MapSettings settings)
	{
		SwingHelper.setEnabled(getToolOptionsPanel(), settings.drawText);
	}

	@Override
	public void onBeforeLoadingNewMap()
	{
		selectedTexts = new ArrayList<>();
		groupBoxRI = null;
		textClipboard = null;
		hideTextEditComponents();
	}

	@Override
	public void onAfterLoadingNewMap()
	{
		regenerateNameForAddsUnlessTyped();
	}

	/**
	 * Where keyboard focus should go after a text is selected for editing.
	 */
	enum SelectionFocus
	{
		/** Move focus into the text-edit field so the user can start typing immediately. */
		EditField,
		/** Move focus to the selected mode button, keeping the Tab order predictable and the user one Tab from the edit fields. */
		ModeWidget,
		/** Leave focus wherever it is. Used when saving because focus is already moving elsewhere (e.g. the edit field is losing focus). */
		Leave
	}
}
