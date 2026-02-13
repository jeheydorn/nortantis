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
import nortantis.platform.FontStyle;
import nortantis.platform.awt.AwtBridge;
import nortantis.swing.translation.TranslatedEnumRenderer;
import nortantis.swing.translation.Translation;
import nortantis.util.Assets;
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
	private Point mousePressedLocation;
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
		modeWidget.configureDrawButton(Translation.get("textTool.add"), Translation.get("textTool.addMode"), KeyEvent.VK_A, Translation.get("textTool.add.shortcut"));
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
					handleSelectingTextToEdit(lastSelected, false);
				}
			}
		});
		editTextField.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				handleSelectingTextToEdit(lastSelected, false);
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
		textTypeComboBox.setRenderer(new TranslatedEnumRenderer());
		textTypeHider = organizer.addLabelAndComponent(Translation.get("textTool.textType.label"), "", textTypeComboBox);
		textTypeForAdds = TextType.City;

		for (TextType type : TextType.values())
		{
			textTypeComboBox.addItem(type);
		}

		lineBreakComboBox = new JComboBoxFixed<>();
		lineBreakComboBox.setRenderer(new TranslatedEnumRenderer());
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
		clearRotationButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (lastSelected != null)
				{
					MapText before = lastSelected.deepCopy();
					lastSelected.angle = 0;
					undoer.setUndoPoint(UpdateType.Incremental, TextTool.this);
					mapEditingPanel.setTextBoxToDraw(lastSelected.line1Bounds, lastSelected.line2Bounds);
					updater.createAndShowMapIncrementalUsingText(Arrays.asList(before, lastSelected));
				}
			}
		});
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
			handleSelectingTextToEdit(null, false);
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
		if (modeWidget.isEditMode() && lastSelected != null)
		{
			editTextField.setText(lastSelected.value);
			editTextField.requestFocus();
		}
		actionsSeparatorHider.setVisible((modeWidget.isEditMode() && lastSelected != null) || modeWidget.isDrawMode() || modeWidget.isEraseMode());

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
		return "(Alt+C)";
	}

	@Override
	public nortantis.platform.Image getToolIcon()
	{
		nortantis.platform.Image icons = nortantis.platform.Image.read(Paths.get(Assets.getAssetsPath(), "internal/Text tool.png").toString());
		try (nortantis.platform.Painter p = icons.createPainter(DrawQuality.High))
		{
			String text = Translation.get("textTool.toolIcon");
			p.setColor(Color.black);
			p.setFont(createToolIconFont(34, text));
			p.drawString(text, 3 + getXOffSetBasedOnLanguage(), 37);
		}
		return icons;
	}

	private int getXOffSetBasedOnLanguage()
	{
		return switch (Translation.getEffectiveLocale().getLanguage())
		{
			case "zh" -> -4;
			case "fr" -> -2;
			case "pt" -> -1;
			case "ru" -> -1;
			default -> 0;
		};
	}

	@Override
	protected void onAfterShowMap()
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
			updater.dowWhenMapIsNotDrawing(() ->
			{
				if (modeWidget.isDrawMode())
				{
					MapText addedText = updater.mapParts.nameCreator.createUserAddedText((TextType) textTypeComboBox.getSelectedItem(), getPointOnGraph(e.getPoint()), mainWindow.displayQualityScale);
					mainWindow.edits.text.add(addedText);

					undoer.setUndoPoint(UpdateType.Incremental, this);

					changeToEditModeAndSelectText(addedText, true);

					updater.createAndShowMapIncrementalUsingText(Arrays.asList(addedText));
				}
			});
		}
		else if (modeWidget.isEditMode())
		{
			if (lastSelected != null && mapEditingPanel.isInRotateTool(e.getPoint()))
			{
				isRotating = true;
				mousePressedLocation = e.getPoint();
			}
			else if (lastSelected != null && mapEditingPanel.isInMoveTool(e.getPoint()))
			{
				isMoving = true;
				mousePressedLocation = e.getPoint();
			}
			else
			{
				MapText selectedText = mainWindow.edits.findTextPicked(getPointOnGraph(e.getPoint()));
				handleSelectingTextToEdit(selectedText, true);
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
			updater.createAndShowMapIncrementalUsingText(before, () ->
			{
				mapEditingPanel.removeProcessingAreas(boundsToRemove);
				mapEditingPanel.repaint();
			});
		}
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
			if (isMoving)
			{
				// The user is dragging a text box.
				nortantis.geom.Point graphPointMouseLocation = getPointOnGraph(e.getPoint());
				nortantis.geom.Point graphPointMousePressedLocation = getPointOnGraph(mousePressedLocation);

				int deltaX = (int) (graphPointMouseLocation.x - graphPointMousePressedLocation.x);
				int deltaY = (int) (graphPointMouseLocation.y - graphPointMousePressedLocation.y);

				RotatedRectangle line1 = lastSelected.line1Bounds.translate(new nortantis.geom.Point(deltaX, deltaY));
				RotatedRectangle line2 = lastSelected.line2Bounds == null ? null : lastSelected.line2Bounds.translate(new nortantis.geom.Point(deltaX, deltaY));
				mapEditingPanel.setTextBoxToDraw(line1, line2);
				mapEditingPanel.repaint();
			}
			else if (isRotating)
			{
				double angle = calcRotationAngle(e);
				RotatedRectangle line1 = lastSelected.line1Bounds.rotateTo(angle);
				RotatedRectangle line2 = lastSelected.line2Bounds == null ? null : lastSelected.line2Bounds.rotateTo(angle);
				mapEditingPanel.setTextBoxToDraw(line1, line2);
				mapEditingPanel.repaint();
			}
		}
		else if (modeWidget.isEraseMode())
		{
			deleteTexts(e.getPoint());
		}
	}

	private double calcRotationAngle(MouseEvent e)
	{
		nortantis.geom.Point graphPointMouseLocation = getPointOnGraph(e.getPoint());
		nortantis.geom.Point graphPointMousePressedLocation = getPointOnGraph(mousePressedLocation);

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
				nortantis.geom.Point graphPointMousePressedLocation = getPointOnGraph(mousePressedLocation);

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
				double angle = calcRotationAngle(e);
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

	@Override
	protected void handleMouseClickOnMap(MouseEvent e)
	{
	}

	public void changeToEditModeAndSelectText(MapText selectedText, boolean grabFocus)
	{
		modeWidget.selectEditMode();
		handleSelectingTextToEdit(selectedText, grabFocus);
	}

	private void handleSelectingTextToEdit(MapText selectedText, boolean grabFocus)
	{
		mapEditingPanel.clearHighlightedAreas();

		if (lastSelected != null && !(editTextField.getText().trim().equals(lastSelected.value) && textTypeComboBox.getSelectedItem().equals(lastSelected.type)
				&& lastSelected.lineBreak.equals(lineBreakComboBox.getSelectedItem()) && Objects.equals(lastSelected.colorOverride, AwtBridge.fromAwtColor(colorOverrideDisplay.getBackground()))
				&& Objects.equals(lastSelected.boldBackgroundColorOverride, AwtBridge.fromAwtColor(boldBackgroundColorOverrideDisplay.getBackground()))))
		{
			MapText before = lastSelected.deepCopy();
			// The user changed the last selected text. Need to save the change.
			lastSelected.value = editTextField.getText().trim();
			lastSelected.type = (TextType) textTypeComboBox.getSelectedItem();
			lastSelected.lineBreak = (LineBreak) lineBreakComboBox.getSelectedItem();
			lastSelected.colorOverride = colorOverrideHider.isVisible() ? AwtBridge.fromAwtColor(colorOverrideDisplay.getBackground()) : null;
			lastSelected.boldBackgroundColorOverride = boldBackgroundColorOverrideHider.isVisible() ? AwtBridge.fromAwtColor(boldBackgroundColorOverrideDisplay.getBackground()) : null;
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
			if (!editTextField.hasFocus() && grabFocus)
			{
				editTextField.grabFocus();
			}
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
			curvatureSlider.setValue((int) (selectedText.curvature * curvatureSliderDivider));
			spacingSlider.setValue(selectedText.spacing);
			backgroundFadeSlider.setValue((int) (selectedText.backgroundFade * backgroundFadeDivider));
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
			updater.dowWhenMapIsNotDrawing(() ->
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
			handleSelectingTextToEdit(null, false);
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
		handleSelectingTextToEdit(lastSelected, false);
	}

	@Override
	protected void onAfterUndoRedo()
	{
		lastSelected = null;
		handleSelectingTextToEdit(null, false);
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
			handleSelectingTextToEdit(lastSelected, false);
		}

		handleEnablingAndDisabling(settings);
		drawTextDisabledLabelHider.setVisible(!settings.drawText);
		if (!settings.drawText)
		{
			if (modeWidget.isEditMode())
			{
				handleSelectingTextToEdit(null, false);
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

}
