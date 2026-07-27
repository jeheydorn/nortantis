package nortantis.swing;

import nortantis.LineBreak;
import nortantis.MapSettings;
import nortantis.MapText;
import nortantis.TextType;
import nortantis.editor.MapUpdater;
import nortantis.geom.RotatedRectangle;
import nortantis.platform.Color;
import nortantis.platform.DrawQuality;
import nortantis.platform.Font;
import nortantis.platform.awt.AwtBridge;
import nortantis.swing.translation.Translation;
import nortantis.util.Assets;
import nortantis.util.OSHelper;
import nortantis.util.Tuple2;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class TextTool extends EditorTool
{
	private JTextField editTextField;
	private MapText lastSelected;
	/**
	 * The location where the mouse was pressed to begin moving or rotating text, stored in graph coordinates rather than panel pixels so it
	 * stays correct if the zoom changes mid-drag. A panel-pixel press point would map to a different graph location under the new zoom,
	 * corrupting the move delta and the rotation reference angle.
	 */
	private nortantis.geom.Point mousePressedLocation;
	private DrawModeWidget modeWidget;
	private RowHider textTypeHider;
	private JComboBox<TextType> textTypeComboBox;
	private TextType textTypeForAdds;
	private RowHider editTextFieldHider;
	private RowHider booksHider;
	private BooksWidget booksWidget;
	private JLabel drawTextDisabledLabel;
	private RowHider drawTextDisabledLabelHider;
	private boolean isRotating;
	private boolean isMoving;
	private JComboBox<ImageIcon> brushSizeComboBox;
	private RowHider brushSizeHider;
	private RowHider clearRotationButtonHider;
	private JComboBoxFixed<LineBreak> lineBreakComboBox;
	private RowHider lineBreakHider;
	private JCheckBox useDefaultColorCheckbox;
	private JPanel colorOverrideDisplay;
	private RowHider useDefaultColorCheckboxHider;
	private RowHider colorOverrideHider;
	private Color defaultTextColor;
	private Color defaultBoldBackgroundColor;
	private JPanel boldBackgroundColorOverrideDisplay;
	private RowHider boldBackgroundColorOverrideHider;
	private boolean areBoldBackgroundsVisible;
	private JSlider curvatureSlider;
	private RowHider editSlidersHider;
	private RowHider editToolsSeparatorHider;
	private final int curvatureSliderDivider = 100;
	private JSlider spacingSlider;
	private RowHider actionsSeparatorHider;
	private JCheckBox useDefaultFontCheckbox;
	private RowHider useDefaultFontCheckboxHider;
	private RowHider fontHider;
	private FontChooser fontChooser;
	private final int backgroundFadeDivider = 10;
	private JSlider backgroundFadeSlider;
	/**
	 * In-memory clipboard for a single MapText. Mirrors the {@code copied} pattern in IconsTool — not the OS clipboard, since pasting text
	 * from other apps doesn't translate to a MapText with its full set of properties (type, color, font, rotation, etc.).
	 */
	private MapText textClipboard;
	private RowHider copyPasteDeleteButtonsHider;
	private RowHider copyPasteDeleteButtonsSeparatorHider;

	public TextTool(MainWindow parent, ToolsPanel toolsPanel, MapUpdater mapUpdater)
	{
		super(parent, toolsPanel, mapUpdater);
	}

	@Override
	protected JPanel createToolOptionsPanel()
	{
		GridBagOrganizer organizer = new GridBagOrganizer();

		JPanel toolOptionsPanel = organizer.panel;
		toolOptionsPanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

		drawTextDisabledLabel = new JLabel("<html>" + Translation.get("textTool.disabled") + "</html>");
		drawTextDisabledLabelHider = organizer.addLeftAlignedComponent(drawTextDisabledLabel);
		drawTextDisabledLabelHider.setVisible(false);

		modeWidget = new DrawModeWidget(Translation.get("textTool.addMode"), Translation.get("textTool.eraseMode"), false, "", true, Translation.get("textTool.editMode"), () -> handleActionChanged());
		modeWidget.configureDrawButton(Translation.get("textTool.add"), Translation.get("textTool.addMode"), KeyEvent.VK_A, Translation.get("textTool.add.shortcut", SwingHelper.getAltKeyName()));
		modeWidget.addToOrganizer(organizer, "");

		actionsSeparatorHider = organizer.addSeparator();

		editTextField = new JTextField();
		editTextField.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent e)
			{
				if (lastSelected != null)
				{
					// Save any edits, but don't move focus. Grabbing focus here would yank it back from
					// whatever component the user is Tabbing (or clicking) to, trapping keyboard focus.
					handleSelectingTextToEdit(lastSelected, SelectionFocus.Leave);
				}
			}
		});
		editTextField.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				handleSelectingTextToEdit(lastSelected, SelectionFocus.ModeWidget);
			}
		});
		editTextFieldHider = organizer.addLeftAlignedComponent(editTextField);

		textTypeComboBox = new JComboBoxFixed<>();
		textTypeComboBox.setSelectedItem(TextType.Other_mountains);
		textTypeComboBox.addActionListener(new ActionListener()
		{

			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (modeWidget.isDrawMode())
				{
					textTypeForAdds = (TextType) textTypeComboBox.getSelectedItem();
				}

				if (modeWidget.isEditMode() && lastSelected != null)
				{
					MapText before = lastSelected.deepCopy();
					lastSelected.type = (TextType) textTypeComboBox.getSelectedItem();
					showOrHideBoldBackgroundFields(lastSelected);
					updater.createAndShowMapIncrementalUsingText(Arrays.asList(before, lastSelected));
				}
			}
		});
		textTypeHider = organizer.addLabelAndComponent(Translation.get("textTool.textType.label"), "", textTypeComboBox);
		textTypeForAdds = TextType.City;

		for (TextType type : TextType.values())
		{
			textTypeComboBox.addItem(type);
		}

		lineBreakComboBox = new JComboBoxFixed<>();
		lineBreakHider = organizer.addLabelAndComponent(Translation.get("textTool.lineBreak.label"), "", lineBreakComboBox);
		for (LineBreak type : LineBreak.values())
		{
			lineBreakComboBox.addItem(type);
		}
		lineBreakComboBox.addActionListener(new ActionListener()
		{

			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (modeWidget.isEditMode() && lastSelected != null)
				{
					MapText before = lastSelected.deepCopy();
					lastSelected.lineBreak = (LineBreak) lineBreakComboBox.getSelectedItem();
					undoer.setUndoPoint(UpdateType.Incremental, TextTool.this);
					updater.createAndShowMapIncrementalUsingText(Arrays.asList(before, lastSelected));
				}
			}
		});

		editToolsSeparatorHider = organizer.addSeparator();

		JButton clearRotationButton = new JButton(Translation.get("textTool.rotateToHorizontal"));
		clearRotationButton.setToolTipText(Translation.get("textTool.rotateToHorizontal.tooltip"));
		clearRotationButton.addActionListener(ev -> rotateSelectedTextToHorizontal());
		clearRotationButtonHider = organizer.addLeftAlignedComponents(Arrays.asList(clearRotationButton));

		editToolsSeparatorHider.add(organizer.addSeparator());

		{
			useDefaultFontCheckbox = new JCheckBox(Translation.get("textTool.useDefaultFont"));
			useDefaultFontCheckbox.setToolTipText(Translation.get("textTool.useDefaultFont.tooltip"));
			useDefaultFontCheckbox.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					fontHider.setVisible(!useDefaultFontCheckbox.isSelected());
					if (useDefaultFontCheckbox.isSelected())
					{
						MapText old = lastSelected.deepCopy();
						lastSelected.fontOverride = null;
						undoer.setUndoPoint(UpdateType.Incremental, TextTool.this);
						updater.createAndShowMapIncrementalUsingText(Arrays.asList(old, lastSelected));
					}
					else
					{
						// I'm not setting an undo point here because, although this is a change to the map settings, it doesn't change the
						// appearance of the map, so I think it could be confusing to then hit the undo button and nothing seems to change
						// unless you know which text to look at to see this checkbox flip.

						MapText old = lastSelected.deepCopy();
						lastSelected.fontOverride = getFontForType(lastSelected.type);
						fontChooser.setFont(AwtBridge.toAwtFont(lastSelected.fontOverride));
						updater.createAndShowMapIncrementalUsingText(Arrays.asList(old, lastSelected));
					}

				}
			});
			useDefaultFontCheckboxHider = organizer.addLeftAlignedComponent(useDefaultFontCheckbox);

			fontChooser = new FontChooser(Translation.get("textTool.font.label"), 30, 40, () ->
			{
				if (lastSelected != null)
				{
					MapText old = lastSelected.deepCopy();
					lastSelected.fontOverride = AwtBridge.fromAwtFont(fontChooser.getFont());
					undoer.setUndoPoint(UpdateType.Incremental, TextTool.this);
					updater.createAndShowMapIncrementalUsingText(Arrays.asList(old, lastSelected));
				}
			});
			fontHider = fontChooser.addToOrganizer(organizer);
		}

		editToolsSeparatorHider.add(organizer.addSeparator());

		useDefaultColorCheckbox = new JCheckBox(Translation.get("textTool.useDefaultColor"));
		useDefaultColorCheckbox.setToolTipText(Translation.get("textTool.useDefaultColor.tooltip"));
		useDefaultColorCheckbox.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				colorOverrideHider.setVisible(!useDefaultColorCheckbox.isSelected());
				showOrHideBoldBackgroundFields(lastSelected);
				if (useDefaultColorCheckbox.isSelected())
				{
					lastSelected.colorOverride = null;
					lastSelected.boldBackgroundColorOverride = null;
					undoer.setUndoPoint(UpdateType.Incremental, TextTool.this);
					updater.createAndShowMapIncrementalUsingText(Arrays.asList(lastSelected));
				}
				else
				{
					// I'm not setting an undo point here because, although this is a change to the map settings, it doesn't change the
					// appearance of the map, so I think it could be confusing to then hit the undo button and nothing seems to change
					// unless you know which text to look at to see this checkbox flip.

					lastSelected.colorOverride = defaultTextColor;
					colorOverrideDisplay.setBackground(AwtBridge.toAwtColor(defaultTextColor));
					lastSelected.boldBackgroundColorOverride = defaultBoldBackgroundColor;
					boldBackgroundColorOverrideDisplay.setBackground(AwtBridge.toAwtColor(defaultBoldBackgroundColor));
				}

			}
		});
		useDefaultColorCheckboxHider = organizer.addLeftAlignedComponent(useDefaultColorCheckbox);

		colorOverrideDisplay = SwingHelper.createColorPickerPreviewPanel();
		JButton buttonChooseColorOverride = new JButton(Translation.get("common.choose"));
		buttonChooseColorOverride.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				SwingHelper.showColorPicker(organizer.panel, colorOverrideDisplay, Translation.get("textTool.color.title"), () ->
				{
					if (lastSelected != null)
					{
						lastSelected.colorOverride = AwtBridge.fromAwtColor(colorOverrideDisplay.getBackground());
						undoer.setUndoPoint(UpdateType.Incremental, TextTool.this);
						updater.createAndShowMapIncrementalUsingText(Arrays.asList(lastSelected));
					}
				});
			}
		});
		colorOverrideHider = organizer.addLabelAndComponentsHorizontal(Translation.get("textTool.color.label"), Translation.get("textTool.color.help"),
				Arrays.asList(colorOverrideDisplay, buttonChooseColorOverride), SwingHelper.colorPickerLeftPadding);

		boldBackgroundColorOverrideDisplay = SwingHelper.createColorPickerPreviewPanel();
		JButton buttonChooseBoldBackgroundColorOverride = new JButton(Translation.get("common.choose"));
		buttonChooseBoldBackgroundColorOverride.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				SwingHelper.showColorPicker(organizer.panel, boldBackgroundColorOverrideDisplay, Translation.get("textTool.boldBackgroundColor.title"), () ->
				{
					if (lastSelected != null)
					{
						lastSelected.boldBackgroundColorOverride = AwtBridge.fromAwtColor(boldBackgroundColorOverrideDisplay.getBackground());
						undoer.setUndoPoint(UpdateType.Incremental, TextTool.this);
						updater.createAndShowMapIncrementalUsingText(Arrays.asList(lastSelected));
					}
				});
			}
		});
		boldBackgroundColorOverrideHider = organizer.addLabelAndComponentsHorizontal(Translation.get("textTool.boldBackgroundColor.label"), Translation.get("textTool.boldBackgroundColor.help"),
				Arrays.asList(boldBackgroundColorOverrideDisplay, buttonChooseBoldBackgroundColorOverride), SwingHelper.colorPickerLeftPadding);

		editToolsSeparatorHider.add(organizer.addSeparator());

		{
			curvatureSlider = new JSlider();
			curvatureSlider.setPaintLabels(false);
			curvatureSlider.setMinimum(-curvatureSliderDivider);
			curvatureSlider.setMaximum(curvatureSliderDivider);
			curvatureSlider.setValue(0);
			SliderWithDisplayedValue sliderWithDisplay = new SliderWithDisplayedValue(curvatureSlider, (value) -> String.format("%.2f", value / ((double) curvatureSliderDivider)), () ->
			{
				if (lastSelected != null)
				{
					MapText before = lastSelected.deepCopy();
					lastSelected.curvature = curvatureSlider.getValue() / ((double) curvatureSliderDivider);
					undoer.setUndoPoint(UpdateType.Incremental, TextTool.this);
					mapEditingPanel.setTextBoxToDraw(lastSelected.line1Bounds, lastSelected.line2Bounds);
					updater.createAndShowMapIncrementalUsingText(Arrays.asList(before, lastSelected));
				}
			}, 34);
			JButton clearCurvatureButton = new JButton("x");
			clearCurvatureButton.setToolTipText(Translation.get("textTool.clearCurvature.tooltip"));
			SwingHelper.addListener(clearCurvatureButton, () ->
			{
				curvatureSlider.setValue(0);
			});

			editSlidersHider = sliderWithDisplay.addToOrganizer(organizer, Translation.get("textTool.curvature.label"), Translation.get("textTool.curvature.help"), clearCurvatureButton, 0, 0);
		}

		{
			spacingSlider = new JSlider();
			spacingSlider.setPaintLabels(false);
			spacingSlider.setMinimum(-5);
			spacingSlider.setMaximum(30);
			spacingSlider.setValue(0);
			SliderWithDisplayedValue sliderWithDisplay = new SliderWithDisplayedValue(spacingSlider, null, () ->
			{
				if (lastSelected != null)
				{
					MapText before = lastSelected.deepCopy();
					lastSelected.spacing = spacingSlider.getValue();
					undoer.setUndoPoint(UpdateType.Incremental, TextTool.this);
					mapEditingPanel.setTextBoxToDraw(lastSelected.line1Bounds, lastSelected.line2Bounds);
					updater.createAndShowMapIncrementalUsingText(Arrays.asList(before, lastSelected));
				}
			}, 34);
			JButton clearSpacingButton = new JButton("x");
			clearSpacingButton.setToolTipText(Translation.get("textTool.clearSpacing.tooltip"));
			SwingHelper.addListener(clearSpacingButton, () ->
			{
				spacingSlider.setValue(0);
			});

			editSlidersHider.add(sliderWithDisplay.addToOrganizer(organizer, Translation.get("textTool.spacing.label"), Translation.get("textTool.spacing.help"), clearSpacingButton, 0, 0));
		}

		{
			backgroundFadeSlider = new JSlider();
			backgroundFadeSlider.setPaintLabels(false);
			backgroundFadeSlider.setMinimum(0);
			backgroundFadeSlider.setMaximum(backgroundFadeDivider);
			SliderWithDisplayedValue sliderWithDisplay = new SliderWithDisplayedValue(backgroundFadeSlider, (value) -> String.format("%.1f", value / ((double) backgroundFadeDivider)), () ->
			{
				if (lastSelected != null)
				{
					MapText before = lastSelected.deepCopy();
					lastSelected.backgroundFade = backgroundFadeSlider.getValue() / ((double) backgroundFadeDivider);
					undoer.setUndoPoint(UpdateType.Incremental, TextTool.this);
					mapEditingPanel.setTextBoxToDraw(lastSelected.line1Bounds, lastSelected.line2Bounds);
					updater.createAndShowMapIncrementalUsingText(Arrays.asList(before, lastSelected));
				}
			}, 34);
			JButton clearBackgroundFadeButton = new JButton("x");
			clearBackgroundFadeButton.setToolTipText(Translation.get("textTool.clearBackgroundFade.tooltip"));
			SwingHelper.addListener(clearBackgroundFadeButton, () ->
			{
				backgroundFadeSlider.setValue(0);
			});

			editSlidersHider.add(
					sliderWithDisplay.addToOrganizer(organizer, Translation.get("textTool.backgroundFade.label"), Translation.get("textTool.backgroundFade.help"), clearBackgroundFadeButton, 0, 0));
		}


		{
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

		Tuple2<JComboBox<ImageIcon>, RowHider> brushSizeTuple = organizer.addBrushSizeComboBox(brushSizes);
		brushSizeComboBox = brushSizeTuple.getFirst();
		brushSizeHider = brushSizeTuple.getSecond();

		booksWidget = new BooksWidget(false, () ->
		{
			updater.reprocessBooks();
		});
		booksHider = organizer.addLeftAlignedComponentWithStackedLabel(Translation.get("textTool.booksForText.label"), Translation.get("textTool.booksForText.help"), booksWidget.getContentPanel());

		modeWidget.selectEditMode();

		organizer.addHorizontalSpacerRowToHelpComponentAlignment(0.64);
		organizer.addVerticalFillerRow();
		return toolOptionsPanel;
	}

	private Font getFontForType(TextType type)
	{
		return switch (type)
		{
			case Title -> mainWindow.themePanel.getTitleFont();
			case Region -> mainWindow.themePanel.getRegionFont();
			case Mountain_range -> mainWindow.themePanel.getMountainRangeFont();
			case Other_mountains -> mainWindow.themePanel.getOtherMountainsFont();
			case City -> mainWindow.themePanel.getCitiesFont();
			// Lakes don't have their own font.
			case Lake -> mainWindow.themePanel.getRiverFont();
			case River -> mainWindow.themePanel.getRiverFont();
		};
	}

	protected void showOrHideBoldBackgroundFields(MapText selectedText)
	{
		boldBackgroundColorOverrideHider.setVisible(modeWidget.isEditMode() && selectedText != null && !useDefaultColorCheckbox.isSelected() && areBoldBackgroundsVisible
				&& (selectedText.type == TextType.Title || selectedText.type == TextType.Region));
	}

	private void handleActionChanged()
	{
		if (editTextFieldHider.isVisible())
		{
			// Keep any text edits that were being done, and hide the edit
			// fields.
			handleSelectingTextToEdit(null, SelectionFocus.Leave);
		}

		if (modeWidget.isDrawMode() || modeWidget.isEraseMode())
		{
			lastSelected = null;
		}

		if (modeWidget.isDrawMode())
		{
			textTypeComboBox.setSelectedItem(textTypeForAdds);
			lineBreakComboBox.setSelectedItem(LineBreak.Auto);
		}

		textTypeHider.setVisible(modeWidget.isDrawMode());
		booksHider.setVisible(modeWidget.isDrawMode());
		editTextFieldHider.setVisible(false);
		lineBreakHider.setVisible(false);
		useDefaultColorCheckboxHider.setVisible(false);
		useDefaultFontCheckboxHider.setVisible(false);
		editSlidersHider.setVisible(false);
		curvatureSlider.setValue(0);
		backgroundFadeSlider.setValue(0);
		spacingSlider.setValue(0);
		editToolsSeparatorHider.setVisible(false);
		colorOverrideHider.setVisible(false);
		boldBackgroundColorOverrideHider.setVisible(false);
		fontHider.setVisible(false);
		clearRotationButtonHider.setVisible(false);
		copyPasteDeleteButtonsHider.setVisible(modeWidget.isEditMode());
		actionsSeparatorHider.setVisible((modeWidget.isEditMode() && lastSelected != null) || modeWidget.isDrawMode() || modeWidget.isEraseMode());
		copyPasteDeleteButtonsSeparatorHider.setVisible((modeWidget.isEditMode()));

		// For some reason this is necessary to prevent the text editing field
		// from flattening sometimes.
		if (getToolOptionsPanel() != null)
		{
			getToolOptionsPanel().revalidate();
			getToolOptionsPanel().repaint();
		}

		brushSizeHider.setVisible(modeWidget.isEraseMode());
		mapEditingPanel.clearHighlightedAreas();
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
		return KeyEvent.VK_C;
	}

	@Override
	public String getKeyboardShortcutText()
	{
		return "(" + SwingHelper.getAltKeyName() + "+C)";
	}

	@Override
	public nortantis.platform.Image getToolIcon()
	{
		nortantis.platform.Image icons = Assets.readImage(Paths.get(Assets.getAssetsPath(), "internal/Text tool.png").toString());
		try (nortantis.platform.Painter p = icons.createPainter(DrawQuality.High))
		{
			String text = Translation.get("textTool.toolIcon");
			p.setColor(Color.black);

			p.setFont(createToolIconFont((int) (34 * getBaseFontScale()), text));
			p.drawString(text, 3 + getXOffSetBasedOnLanguage(), 37);
		}
		return icons;
	}

	private double getBaseFontScale()
	{
		String language = Translation.getEffectiveLocale().getLanguage();
		double baseFontScale;
		if (OSHelper.isMac())
		{
			baseFontScale = switch (language)
			{
				case "es" -> 0.85;
				case "fr" -> 0.9;
				case "pt" -> 0.85;
				default -> 1.0;
			};
		}
		else
		{
			baseFontScale = 1.0;
		}
		return baseFontScale;
	}

	private int getXOffSetBasedOnLanguage()
	{
		return switch (Translation.getEffectiveLocale().getLanguage())
		{
			case "en" -> OSHelper.isMac() ? -1 : 0;
			case "zh" -> -4;
			case "fr" -> OSHelper.isLinux() ? -1 : -2;
			case "pt" -> -1;
			case "ru" -> -1;
			default -> 0;
		};
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
		if (lastSelected == null)
		{
			mapEditingPanel.clearTextBox();
		}
		else if (!isMoving && !isRotating)
		{
			mapEditingPanel.setTextBoxToDraw(lastSelected);
		}

		innerHandleMouseMovedOnMap(mapEditingPanel.getMousePosition());
		mapEditingPanel.repaint();
	}

	@Override
	protected void handleMousePressedOnMap(MouseEvent e)
	{
		if (drawTextDisabledLabel.isVisible())
		{
			return;
		}

		isRotating = false;
		isMoving = false;

		if (modeWidget.isEraseMode())
		{
			deleteTexts(e.getPoint());
		}
		else if (modeWidget.isDrawMode())
		{
			// This is differed if the map is currently drawing so that we don't try to generate text while the text drawer is reprocessing
			// books after a book checkbox was checked.
			updater.doWhenMapIsNotDrawing(() ->
			{
				if (modeWidget.isDrawMode())
				{
					MapText addedText = updater.mapParts.nameCreator.createUserAddedText((TextType) textTypeComboBox.getSelectedItem(), getPointOnGraph(e.getPoint()), mainWindow.displayQualityScale);
					mainWindow.edits.text.add(addedText);

					undoer.setUndoPoint(UpdateType.Incremental, this);

					changeToEditModeAndSelectText(addedText, SelectionFocus.EditField);

					updater.createAndShowMapIncrementalUsingText(Arrays.asList(addedText));
				}
			});
		}
		else if (modeWidget.isEditMode())
		{
			if (lastSelected != null && mapEditingPanel.isInRotateTool(e.getPoint()))
			{
				isRotating = true;
				mousePressedLocation = getPointOnGraph(e.getPoint());
			}
			else if (lastSelected != null && mapEditingPanel.isInMoveTool(e.getPoint()))
			{
				isMoving = true;
				mousePressedLocation = getPointOnGraph(e.getPoint());
			}
			else
			{
				MapText selectedText = mainWindow.edits.findTextPicked(getPointOnGraph(e.getPoint()));
				// Don't grab focus on selection — selecting a text is a visual selection (like clicking an
				// icon), not the same as starting to edit it. This keeps ctrl+C / ctrl+V / DELETE shortcuts
				// targeting the selected MapText object instead of being swallowed by the text-edit field.
				// Users click into the text field to start typing.
				handleSelectingTextToEdit(selectedText, SelectionFocus.ModeWidget);
			}
		}
	}

	private void deleteTexts(Point mouseLocation)
	{
		List<MapText> mapTextsSelected = getMapTextsSelectedByCurrentBrushSizeAndShowBrush(mouseLocation);
		mapEditingPanel.addProcessingAreasFromTexts(mapTextsSelected);
		List<MapText> before = mapTextsSelected.stream().map(text -> text.deepCopy()).collect(Collectors.toList());
		for (MapText text : mapTextsSelected)
		{
			text.value = "";
		}
		mapEditingPanel.clearHighlightedAreas();
		mapEditingPanel.repaint();
		if (mapTextsSelected.size() > 0)
		{
			Set<RotatedRectangle> boundsToRemove = new HashSet<>();
			for (MapText text : mapTextsSelected)
			{
				if (text.line1Bounds != null)
				{
					boundsToRemove.add(text.line1Bounds);
				}

				if (text.line2Bounds != null)
				{
					boundsToRemove.add(text.line2Bounds);
				}
			}

			triggerPurgeEmptyText();
			updater.createAndShowMapIncrementalUsingText(before, MapUpdater.afterMapDisplayed(() ->
			{
				mapEditingPanel.removeProcessingAreas(boundsToRemove);
				mapEditingPanel.repaint();
			}));
		}
	}

	private void copySelectedText()
	{
		if (!modeWidget.isEditMode() || lastSelected == null)
		{
			return;
		}
		textClipboard = lastSelected.deepCopy();
	}

	private void pasteText()
	{
		if (textClipboard == null)
		{
			return;
		}

		// Position the paste at the current mouse location. MapText.location is stored in resolution-
		// invariant pixels (graph pixels / displayQualityScale — see TextDrawer.createMapText), so we
		// must divide the graph-pixel mouse location by displayQualityScale here. Without this, paste
		// only lands at the cursor when displayQualityScale is 1.0 (Medium quality). When the mouse
		// is off-map, fall back to a fixed offset from the source location so the new text is visible
		// rather than landing exactly on top of the original.
		nortantis.geom.Point pasteLoc;
		java.awt.Point mouseOnPanel = mapEditingPanel.getMousePosition();
		if (mouseOnPanel != null)
		{
			nortantis.geom.Point mouseGraph = getPointOnGraph(mouseOnPanel);
			pasteLoc = new nortantis.geom.Point(mouseGraph.x / mainWindow.displayQualityScale, mouseGraph.y / mainWindow.displayQualityScale);
		}
		else
		{
			final double offset = 50.0;
			pasteLoc = new nortantis.geom.Point(textClipboard.location.x + offset, textClipboard.location.y + offset);
		}

		MapText pasted = textClipboard.deepCopy();
		pasted.location = pasteLoc;
		pasted.line1Bounds = null;
		pasted.line2Bounds = null;
		mainWindow.edits.text.add(pasted);

		undoer.setUndoPoint(UpdateType.Incremental, this);
		updater.createAndShowMapIncrementalUsingText(Arrays.asList(pasted));
		handleSelectingTextToEdit(pasted, SelectionFocus.ModeWidget);
	}

	private void deleteSelectedText()
	{
		if (!modeWidget.isEditMode() || lastSelected == null)
		{
			return;
		}
		MapText toDelete = lastSelected;
		MapText before = toDelete.deepCopy();
		// Match the erase-mode pattern: clear the value and let purgeEmptyText reap the entry once
		// drawing is idle. Going through the same code path means undo/redo handling stays uniform.
		toDelete.value = "";
		// Sync the edit field to the cleared value BEFORE clearing the selection. Otherwise
		// handleSelectingTextToEdit sees a "modification" (field still holds the original text vs.
		// the now-empty MapText.value) and writes the field's contents back into MapText.value,
		// undoing the delete.
		editTextField.setText("");
		handleSelectingTextToEdit(null, SelectionFocus.Leave);
		undoer.setUndoPoint(UpdateType.Incremental, this);
		triggerPurgeEmptyText();
		updater.createAndShowMapIncrementalUsingText(Arrays.asList(before, toDelete));
	}

	private void rotateSelectedTextToHorizontal()
	{
		if (lastSelected == null)
		{
			return;
		}
		MapText before = lastSelected.deepCopy();
		lastSelected.angle = 0;
		undoer.setUndoPoint(UpdateType.Incremental, this);
		mapEditingPanel.setTextBoxToDraw(lastSelected.line1Bounds, lastSelected.line2Bounds);
		updater.createAndShowMapIncrementalUsingText(Arrays.asList(before, lastSelected));
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

		// If right-click is on a text that isn't currently the selection, select it first so the menu
		// acts on what the user pointed at. Don't grab focus — see Option A in selection-vs-editing
		// design (focus would route ctrl+C/V/DELETE to the text-edit field instead of the MapText).
		//
		// Exception: if the right-click landed inside the currently-selected text's selection box, keep that
		// selection rather than switching to whatever text happens to be under the cursor. Another text can sit
		// under the same box, and swapping the selection on right-click would surprise the user. (Matches the
		// Icons tool's isPointInsideMultiIconSelectionBox behavior.)
		nortantis.geom.Point graphPoint = getPointOnGraph(e.getPoint());
		boolean insideSelectionBox = lastSelected != null
				&& ((lastSelected.line1Bounds != null && lastSelected.line1Bounds.contains(graphPoint))
						|| (lastSelected.line2Bounds != null && lastSelected.line2Bounds.contains(graphPoint)));
		if (!insideSelectionBox)
		{
			MapText underCursor = mainWindow.edits.findTextPicked(graphPoint);
			if (underCursor != null && underCursor != lastSelected)
			{
				handleSelectingTextToEdit(underCursor, SelectionFocus.ModeWidget);
			}
		}

		boolean hasSelection = lastSelected != null;
		boolean hasClipboard = textClipboard != null;
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

		if (lastSelected != null)
		{
			updateMoveOrRotatePreview(e.getPoint());
		}
		else if (modeWidget.isEraseMode())
		{
			deleteTexts(e.getPoint());
		}
	}

	/**
	 * Recomputes the in-progress move/rotate preview box from the current mouse location and the graph-coordinate press point. Called on each
	 * drag event, and also from {@link #onAfterShowMap()} so the preview follows a zoom change made mid-drag without requiring a mouse move.
	 */
	private void updateMoveOrRotatePreview(java.awt.Point mouseLocation)
	{
		if (mouseLocation == null || mousePressedLocation == null || lastSelected == null)
		{
			return;
		}

		if (isMoving)
		{
			// The user is dragging a text box.
			nortantis.geom.Point graphPointMouseLocation = getPointOnGraph(mouseLocation);
			nortantis.geom.Point graphPointMousePressedLocation = mousePressedLocation;

			int deltaX = (int) (graphPointMouseLocation.x - graphPointMousePressedLocation.x);
			int deltaY = (int) (graphPointMouseLocation.y - graphPointMousePressedLocation.y);

			RotatedRectangle line1 = lastSelected.line1Bounds.translate(new nortantis.geom.Point(deltaX, deltaY));
			RotatedRectangle line2 = lastSelected.line2Bounds == null ? null : lastSelected.line2Bounds.translate(new nortantis.geom.Point(deltaX, deltaY));
			mapEditingPanel.setTextBoxToDraw(line1, line2);
			mapEditingPanel.repaint();
		}
		else if (isRotating)
		{
			double angle = calcRotationAngle(mouseLocation);
			RotatedRectangle line1 = lastSelected.line1Bounds.rotateTo(angle);
			RotatedRectangle line2 = lastSelected.line2Bounds == null ? null : lastSelected.line2Bounds.rotateTo(angle);
			mapEditingPanel.setTextBoxToDraw(line1, line2);
			mapEditingPanel.repaint();
		}
	}

	private double calcRotationAngle(java.awt.Point mouseLocation)
	{
		nortantis.geom.Point graphPointMouseLocation = getPointOnGraph(mouseLocation);
		nortantis.geom.Point graphPointMousePressedLocation = mousePressedLocation;

		// Find the bounding box currently displayed
		RotatedRectangle boundingBox = lastSelected.line1Bounds.addRotatedRectangleThatHasTheSameAngleAndPivot(lastSelected.line2Bounds);

		// Find the angle between the mouse-down point with respect to the bounding box.
		nortantis.geom.Point rotatedMouseDownPoint = graphPointMousePressedLocation.rotate(boundingBox.getPivot(), -boundingBox.angle);
		double yDiffFromPivot = (boundingBox.y + boundingBox.height / 2.0) - boundingBox.pivotY;
		double mouseDownAngleWithRespectToBounds = Math.atan2(rotatedMouseDownPoint.y - boundingBox.pivotY - yDiffFromPivot, rotatedMouseDownPoint.x - boundingBox.pivotX);

		// Find the angle between the edge of the bounding box where the rotation tool is and the edge of the bounding box where the
		// rotation tool would be if it were aligned with the pivot. These can be different when text is curved.
		// This y distance between the center of the rotation tool and the pivot when the text box is horizontal.
		double xDiffFromMouseDownToEdgeOfBoundsWithRespectToBounds = rotatedMouseDownPoint.x - (boundingBox.x + boundingBox.width);
		double angleToRotateTool = Math.atan2(yDiffFromPivot, (boundingBox.width / 2.0) + xDiffFromMouseDownToEdgeOfBoundsWithRespectToBounds);

		double centerX = lastSelected.location.x * mainWindow.displayQualityScale;
		double centerY = lastSelected.location.y * mainWindow.displayQualityScale;
		double angle = Math.atan2(graphPointMouseLocation.y - centerY, graphPointMouseLocation.x - centerX) - mouseDownAngleWithRespectToBounds - angleToRotateTool;
		return angle;
	}

	@Override
	protected void handleMouseReleasedOnMap(MouseEvent e)
	{
		if (drawTextDisabledLabel.isVisible())
		{
			return;
		}

		if (lastSelected != null)
		{
			if (isMoving)
			{
				MapText before = lastSelected.deepCopy();
				nortantis.geom.Point graphPointMouseLocation = getPointOnGraph(e.getPoint());
				nortantis.geom.Point graphPointMousePressedLocation = mousePressedLocation;

				// The user dragged and dropped text.
				// Divide the translation by mainWindow.displayQualityScale because MapText locations are stored as if
				// the map is generated at 100% resolution.
				Point translation = new Point((int) ((graphPointMouseLocation.x - graphPointMousePressedLocation.x) / mainWindow.displayQualityScale),
						(int) ((graphPointMouseLocation.y - graphPointMousePressedLocation.y) / mainWindow.displayQualityScale));
				lastSelected.location = new nortantis.geom.Point(lastSelected.location.x + translation.x, lastSelected.location.y + translation.y);
				undoer.setUndoPoint(UpdateType.Incremental, this);
				updater.createAndShowMapIncrementalUsingText(Arrays.asList(before, lastSelected));
				isMoving = false;
			}
			else if (isRotating)
			{
				double angle = calcRotationAngle(e.getPoint());
				MapText before = lastSelected.deepCopy();

				lastSelected.angle = angle;
				undoer.setUndoPoint(UpdateType.Incremental, this);
				updater.createAndShowMapIncrementalUsingText(Arrays.asList(before, lastSelected));
				isRotating = false;
			}
		}

		if (modeWidget.isEraseMode())
		{
			mapEditingPanel.clearHighlightedAreas();
			mapEditingPanel.repaint();

			// This won't actually set an undo point unless text was deleted because Undoer is smart enough to discard undo points
			// that didn't change anything.
			undoer.setUndoPoint(UpdateType.Incremental, this);
		}
	}

	public void changeToEditModeAndSelectText(MapText selectedText, SelectionFocus focusBehavior)
	{
		// Only ModeWidget wants focus on the mode button; EditField overrides it below, and Leave keeps focus untouched.
		modeWidget.selectEditMode(focusBehavior == SelectionFocus.ModeWidget);
		handleSelectingTextToEdit(selectedText, focusBehavior);
	}

	private void handleSelectingTextToEdit(MapText selectedText, SelectionFocus focusBehavior)
	{
		mapEditingPanel.clearHighlightedAreas();

		// A hidden color override row means the text uses the default color, which is stored as null. Both the comparison below and the
		// assignments that follow it must apply that rule, or a text with no override never compares equal to what the GUI shows and every
		// selection looks modified.
		Color colorOverrideFromGui = colorOverrideHider.isVisible() ? AwtBridge.fromAwtColor(colorOverrideDisplay.getBackground()) : null;
		Color boldBackgroundColorOverrideFromGui = boldBackgroundColorOverrideHider.isVisible() ? AwtBridge.fromAwtColor(boldBackgroundColorOverrideDisplay.getBackground())
				: null;

		if (lastSelected != null && !(editTextField.getText().trim().equals(lastSelected.value) && textTypeComboBox.getSelectedItem().equals(lastSelected.type)
				&& lastSelected.lineBreak.equals(lineBreakComboBox.getSelectedItem()) && Objects.equals(lastSelected.colorOverride, colorOverrideFromGui)
				&& Objects.equals(lastSelected.boldBackgroundColorOverride, boldBackgroundColorOverrideFromGui)))
		{
			MapText before = lastSelected.deepCopy();
			// The user changed the last selected text. Need to save the change.
			lastSelected.value = editTextField.getText().trim();
			lastSelected.type = (TextType) textTypeComboBox.getSelectedItem();
			lastSelected.lineBreak = (LineBreak) lineBreakComboBox.getSelectedItem();
			lastSelected.colorOverride = colorOverrideFromGui;
			lastSelected.boldBackgroundColorOverride = boldBackgroundColorOverrideFromGui;
			lastSelected.fontOverride = fontHider.isVisible() ? AwtBridge.fromAwtFont(fontChooser.getFont()) : null;
			lastSelected.curvature = curvatureSlider.getValue() / ((double) curvatureSliderDivider);
			lastSelected.spacing = spacingSlider.getValue();
			lastSelected.backgroundFade = backgroundFadeSlider.getValue() / (double) backgroundFadeDivider;

			undoer.setUndoPoint(UpdateType.Incremental, this);
			if (!Objects.equals(before, selectedText))
			{
				updater.createAndShowMapIncrementalUsingText(Arrays.asList(before, lastSelected));
			}
		}

		if (selectedText == null)
		{
			triggerPurgeEmptyText();
			hideTextEditComponents();
		}
		else
		{
			mapEditingPanel.setTextBoxToDraw(selectedText);
			editTextField.setText(selectedText.value);
			editTextFieldHider.setVisible(true);
			clearRotationButtonHider.setVisible(true);
			if (focusBehavior == SelectionFocus.EditField && !editTextField.hasFocus())
			{
				editTextField.grabFocus();
			}
			else if (focusBehavior == SelectionFocus.ModeWidget)
			{
				// Don't auto-focus the edit field (would hijack Ctrl+C/V/Delete keyboard shortcuts).
				// Place focus on the mode widget instead — keeps it on a predictable component near the
				// edit fields, and a single Tab press from here reaches the text edit field.
				modeWidget.grabFocusOnSelectedButton();
			}
			// SelectionFocus.Leave: don't move focus, so the user can Tab out of the edit field normally.
			// Prevent textTypeComboBox's action listener from doing anything on
			// the next line.
			lastSelected = null;

			textTypeComboBox.setSelectedItem(selectedText.type);
			textTypeHider.setVisible(true);
			lineBreakComboBox.setSelectedItem(selectedText.lineBreak);
			lineBreakHider.setVisible(true);
			useDefaultColorCheckboxHider.setVisible(true);
			useDefaultFontCheckboxHider.setVisible(true);
			editSlidersHider.setVisible(true);
			editToolsSeparatorHider.setVisible(true);
			useDefaultColorCheckbox.setSelected(selectedText.colorOverride == null);
			colorOverrideHider.setVisible(selectedText.colorOverride != null);
			if (selectedText.colorOverride != null && selectedText.boldBackgroundColorOverride == null)
			{
				selectedText.boldBackgroundColorOverride = defaultBoldBackgroundColor;
			}
			showOrHideBoldBackgroundFields(selectedText);
			if (selectedText.colorOverride != null)
			{
				colorOverrideDisplay.setBackground(AwtBridge.toAwtColor(selectedText.colorOverride));
			}
			if (selectedText.boldBackgroundColorOverride != null)
			{
				boldBackgroundColorOverrideDisplay.setBackground(AwtBridge.toAwtColor(selectedText.boldBackgroundColorOverride));
			}
			fontHider.setVisible(selectedText.fontOverride != null);
			useDefaultFontCheckbox.setSelected(selectedText.fontOverride == null);
			if (selectedText.fontOverride != null)
			{
				fontChooser.setFont(AwtBridge.toAwtFont(selectedText.fontOverride));
			}
			// Round rather than truncate. These values were stored as sliderValue / divider, and dividing then multiplying can land just
			// below the original integer, so truncating would drop the slider a step and the next save would persist that lower value.
			curvatureSlider.setValue((int) Math.round(selectedText.curvature * curvatureSliderDivider));
			spacingSlider.setValue(selectedText.spacing);
			backgroundFadeSlider.setValue((int) Math.round(selectedText.backgroundFade * backgroundFadeDivider));
		}
		actionsSeparatorHider.setVisible((modeWidget.isEditMode() && selectedText != null) || modeWidget.isDrawMode() || modeWidget.isEraseMode());
		mapEditingPanel.repaint();

		lastSelected = selectedText;
	}

	private void hideTextEditComponents()
	{
		mapEditingPanel.clearTextBox();
		editTextField.setText("");
		editTextFieldHider.setVisible(false);
		clearRotationButtonHider.setVisible(false);
		textTypeHider.setVisible(false);
		lineBreakHider.setVisible(false);
		useDefaultColorCheckboxHider.setVisible(false);
		useDefaultFontCheckboxHider.setVisible(false);
		editSlidersHider.setVisible(false);
		editToolsSeparatorHider.setVisible(false);
		colorOverrideHider.setVisible(false);
		boldBackgroundColorOverrideHider.setVisible(false);
		fontHider.setVisible(false);
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

	public MapText getTextBeingEdited()
	{
		if (modeWidget.isEditMode() && lastSelected != null)
		{
			return lastSelected;
		}
		return null;
	}

	@Override
	public void onSwitchingAway()
	{
		// Keep any text edits being done and clear the selected text.
		if (modeWidget.isEditMode())
		{
			handleSelectingTextToEdit(null, SelectionFocus.Leave);
		}

		mapEditingPanel.hideBrush();
		mapEditingPanel.clearHighlightedAreas();
		mapEditingPanel.clearTextBox();
		mapEditingPanel.clearProcessingAreas();
		mapEditingPanel.repaint();
	}

	@Override
	protected void onBeforeUndoRedo()
	{
		// Create an undo point for any current changes.
		handleSelectingTextToEdit(lastSelected, SelectionFocus.ModeWidget);
	}

	@Override
	protected void onAfterUndoRedo()
	{
		lastSelected = null;
		handleSelectingTextToEdit(null, SelectionFocus.Leave);
		editTextField.setText("");

		lastSelected = null;
	}

	@Override
	protected void handleMouseMovedOnMap(MouseEvent e)
	{
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
		else if (modeWidget.isEditMode() && lastSelected == null)
		{
			List<MapText> mapTextsSelected = getMapTextsSelectedByCurrentBrushSizeAndShowBrush(mouseLocation);
			mapEditingPanel.setHighlightedAreasFromTexts(mapTextsSelected);
		}
		else
		{
			mapEditingPanel.hideBrush();
			mapEditingPanel.repaint();
		}
	}

	private List<MapText> getMapTextsSelectedByCurrentBrushSizeAndShowBrush(java.awt.Point mouseLocation)
	{
		List<MapText> mapTextsSelected = null;
		int brushDiameter = modeWidget.isEditMode() ? 1 : brushSizes.get(brushSizeComboBox.getSelectedIndex());
		if (brushDiameter > 1)
		{
			mapEditingPanel.showBrush(mouseLocation, brushDiameter);
			mapTextsSelected = mainWindow.edits.findTextSelectedByBrush(getPointOnGraph(mouseLocation), (brushDiameter / mainWindow.zoom) * mapEditingPanel.osScale);
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

		defaultTextColor = settings.textColor;
		defaultBoldBackgroundColor = settings.boldBackgroundColor;
		boolean boldBackgroundVisibleChanged = areBoldBackgroundsVisible != settings.drawBoldBackground;
		areBoldBackgroundsVisible = settings.drawBoldBackground;
		if (modeWidget.isEditMode() && lastSelected != null && boldBackgroundVisibleChanged)
		{
			handleSelectingTextToEdit(lastSelected, SelectionFocus.ModeWidget);
		}

		handleEnablingAndDisabling(settings);
		drawTextDisabledLabelHider.setVisible(!settings.drawText);
		if (!settings.drawText)
		{
			if (modeWidget.isEditMode())
			{
				handleSelectingTextToEdit(null, SelectionFocus.Leave);
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
		if (modeWidget.isEditMode() && lastSelected != null)
		{
			lastSelected = null;
			editTextField.setText("");
			hideTextEditComponents();
		}
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
