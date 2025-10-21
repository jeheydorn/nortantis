package nortantis.swing;

import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JTextField;

import nortantis.LineBreak;
import nortantis.MapSettings;
import nortantis.MapText;
import nortantis.TextType;
import nortantis.editor.MapUpdater;
import nortantis.geom.RotatedRectangle;
import nortantis.platform.Color;
import nortantis.platform.awt.AwtFactory;
import nortantis.util.Assets;
import nortantis.util.Tuple2;

public class TextTool extends EditorTool
{
	private JTextField editTextField;
	private MapText lastSelected;
	private Point mousePressedLocation;
	private JRadioButton editButton;
	private JRadioButton addButton;
	private JRadioButton eraseButton;
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
	private RowHider curvatureSliderHider;
	private RowHider editToolsSeparatorHider;
	private final int curvatureSliderDivider = 100;
	private JSlider spacingSlider;
	private RowHider spacingSliderHider;
	private RowHider actionsSeperatorHider;

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

		drawTextDisabledLabel = new JLabel("<html>This tool is disabled because drawing text is disabled in the Fonts tab.</html>");
		drawTextDisabledLabelHider = organizer.addLeftAlignedComponent(drawTextDisabledLabel);
		drawTextDisabledLabelHider.setVisible(false);

		{
			ButtonGroup group = new ButtonGroup();
			List<JRadioButton> radioButtons = new ArrayList<>();

			ActionListener listener = new ActionListener()
			{

				@Override
				public void actionPerformed(ActionEvent e)
				{
					handleActionChanged();
				}
			};

			editButton = new JRadioButton("<HTML>Edi<U>t</U></HTML>");
			group.add(editButton);
			radioButtons.add(editButton);
			editButton.addActionListener(listener);
			editButton.setMnemonic(KeyEvent.VK_T);
			editButton.setToolTipText("Edit text (Alt+T)");

			addButton = new JRadioButton("<HTML><U>A</U>dd</HTML>");
			group.add(addButton);
			radioButtons.add(addButton);
			addButton.addActionListener(listener);
			addButton.setMnemonic(KeyEvent.VK_A);
			addButton.setToolTipText("Add new text of the selected text type (Alt+A)");

			eraseButton = new JRadioButton("<HTML><U>E</U>rase</HTML>");
			group.add(eraseButton);
			radioButtons.add(eraseButton);
			eraseButton.addActionListener(listener);
			eraseButton.setMnemonic(KeyEvent.VK_E);
			eraseButton.setToolTipText("Erase text (Alt+E)");

			organizer.addLabelAndComponentsVertical("Mode:", "", radioButtons);
		}
		
		actionsSeperatorHider = organizer.addSeperator();

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
				if (addButton.isSelected())
				{
					textTypeForAdds = (TextType) textTypeComboBox.getSelectedItem();
				}

				if (editButton.isSelected() && lastSelected != null)
				{
					MapText before = lastSelected.deepCopy();
					lastSelected.type = (TextType) textTypeComboBox.getSelectedItem();
					showOrHideBoldBackgroundFields(lastSelected);
					updater.createAndShowMapIncrementalUsingText(Arrays.asList(before, lastSelected));
				}
			}
		});
		textTypeHider = organizer.addLabelAndComponent("Text type:", "", textTypeComboBox);
		textTypeForAdds = TextType.City;

		for (TextType type : TextType.values())
		{
			textTypeComboBox.addItem(type);
		}

		lineBreakComboBox = new JComboBoxFixed<>();
		lineBreakHider = organizer.addLabelAndComponent("Number of lines:", "", lineBreakComboBox);
		for (LineBreak type : LineBreak.values())
		{
			lineBreakComboBox.addItem(type);
		}
		lineBreakComboBox.addActionListener(new ActionListener()
		{

			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (editButton.isSelected() && lastSelected != null)
				{
					MapText before = lastSelected.deepCopy();
					lastSelected.lineBreak = (LineBreak) lineBreakComboBox.getSelectedItem();
					undoer.setUndoPoint(UpdateType.Incremental, TextTool.this);
					updater.createAndShowMapIncrementalUsingText(Arrays.asList(before, lastSelected));
				}
			}
		});

		editToolsSeparatorHider = organizer.addSeperator();

		JButton clearRotationButton = new JButton("Rotate to Horizontal");
		clearRotationButton.setToolTipText("Set the rotation angle of the selected text to 0 degrees.");
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

		editToolsSeparatorHider.add(organizer.addSeperator());

		useDefaultColorCheckbox = new JCheckBox("Use default color");
		useDefaultColorCheckbox.setToolTipText("When checked, this text uses the text color in the Fonts tab.");
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
					colorOverrideDisplay.setBackground(AwtFactory.unwrap(defaultTextColor));
					lastSelected.boldBackgroundColorOverride = defaultBoldBackgroundColor;
					boldBackgroundColorOverrideDisplay.setBackground(AwtFactory.unwrap(defaultBoldBackgroundColor));
				}

			}
		});
		useDefaultColorCheckboxHider = organizer.addLeftAlignedComponent(useDefaultColorCheckbox);

		colorOverrideDisplay = SwingHelper.createColorPickerPreviewPanel();
		JButton buttonChooseColorOverride = new JButton("Choose");
		buttonChooseColorOverride.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				SwingHelper.showColorPicker(organizer.panel, colorOverrideDisplay, "Text Color", () ->
				{
					if (lastSelected != null)
					{
						lastSelected.colorOverride = AwtFactory.wrap(colorOverrideDisplay.getBackground());
						undoer.setUndoPoint(UpdateType.Incremental, TextTool.this);
						updater.createAndShowMapIncrementalUsingText(Arrays.asList(lastSelected));
					}
				});
			}
		});
		colorOverrideHider = organizer.addLabelAndComponentsHorizontal("Color:", "Change the color of this text",
				Arrays.asList(colorOverrideDisplay, buttonChooseColorOverride), SwingHelper.colorPickerLeftPadding);

		boldBackgroundColorOverrideDisplay = SwingHelper.createColorPickerPreviewPanel();
		JButton buttonChooseBoldBackgroundColorOverride = new JButton("Choose");
		buttonChooseBoldBackgroundColorOverride.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				SwingHelper.showColorPicker(organizer.panel, boldBackgroundColorOverrideDisplay, "Text Bold Background Color", () ->
				{
					if (lastSelected != null)
					{
						lastSelected.boldBackgroundColorOverride = AwtFactory.wrap(boldBackgroundColorOverrideDisplay.getBackground());
						undoer.setUndoPoint(UpdateType.Incremental, TextTool.this);
						updater.createAndShowMapIncrementalUsingText(Arrays.asList(lastSelected));
					}
				});
			}
		});
		boldBackgroundColorOverrideHider = organizer.addLabelAndComponentsHorizontal("Bold background color:",
				"Change the color of the bold background of this text",
				Arrays.asList(boldBackgroundColorOverrideDisplay, buttonChooseBoldBackgroundColorOverride),
				SwingHelper.colorPickerLeftPadding);

		editToolsSeparatorHider.add(organizer.addSeperator());

		{
			curvatureSlider = new JSlider();
			curvatureSlider.setPaintLabels(false);
			curvatureSlider.setMinimum(-curvatureSliderDivider);
			curvatureSlider.setMaximum(curvatureSliderDivider);
			curvatureSlider.setValue(0);
			SwingHelper.setSliderWidthForSidePanel(curvatureSlider);
			SliderWithDisplayedValue sliderWithDisplay = new SliderWithDisplayedValue(curvatureSlider,
					(value) -> String.format("%.2f", value / ((double) curvatureSliderDivider)), () ->
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
			clearCurvatureButton.setToolTipText("Clear curvature");
			SwingHelper.addListener(clearCurvatureButton, () ->
			{
				curvatureSlider.setValue(0);
			});

			curvatureSliderHider = sliderWithDisplay.addToOrganizer(organizer, "Curvature:", "How much to curve the text",
					clearCurvatureButton, 0, 0);
		}
		
		{
			spacingSlider = new JSlider();
			spacingSlider.setPaintLabels(false);
			spacingSlider.setMinimum(-5);
			spacingSlider.setMaximum(30);
			spacingSlider.setValue(0);
			SwingHelper.setSliderWidthForSidePanel(spacingSlider);
			SliderWithDisplayedValue sliderWithDisplay = new SliderWithDisplayedValue(spacingSlider,
					null, () ->
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
			clearSpacingButton.setToolTipText("Clear spacing");
			SwingHelper.addListener(clearSpacingButton, () ->
			{
				spacingSlider.setValue(0);
			});

			spacingSliderHider = sliderWithDisplay.addToOrganizer(organizer, "Spacing:", "How much space to add between letters",
					clearSpacingButton, 0, 0);
		}


		Tuple2<JComboBox<ImageIcon>, RowHider> brushSizeTuple = organizer.addBrushSizeComboBox(brushSizes);
		brushSizeComboBox = brushSizeTuple.getFirst();
		brushSizeHider = brushSizeTuple.getSecond();

		booksWidget = new BooksWidget(false, () ->
		{
			updater.reprocessBooks();
		});
		booksHider = organizer.addLeftAlignedComponentWithStackedLabel("Books for generating text:",
				"Selected books will be used to generate new names.", booksWidget.getContentPanel());

		editButton.doClick();

		organizer.addHorizontalSpacerRowToHelpComponentAlignment(0.64);
		organizer.addVerticalFillerRow();
		return toolOptionsPanel;
	}

	protected void showOrHideBoldBackgroundFields(MapText selectedText)
	{
		boldBackgroundColorOverrideHider.setVisible(editButton.isSelected() && selectedText != null && !useDefaultColorCheckbox.isSelected()
				&& areBoldBackgroundsVisible && (selectedText.type == TextType.Title || selectedText.type == TextType.Region));
	}

	private void handleActionChanged()
	{
		if (editTextFieldHider.isVisible())
		{
			// Keep any text edits that were being done, and hide the edit
			// fields.
			handleSelectingTextToEdit(null, false);
		}

		if (addButton.isSelected() || eraseButton.isSelected())
		{
			lastSelected = null;
		}

		if (addButton.isSelected())
		{
			textTypeComboBox.setSelectedItem(textTypeForAdds);
			lineBreakComboBox.setSelectedItem(LineBreak.Auto);
		}

		textTypeHider.setVisible(addButton.isSelected());
		booksHider.setVisible(addButton.isSelected());
		editTextFieldHider.setVisible(false);
		lineBreakHider.setVisible(false);
		useDefaultColorCheckboxHider.setVisible(false);
		curvatureSliderHider.setVisible(false);
		curvatureSlider.setValue(0);
		spacingSliderHider.setVisible(false);
		spacingSlider.setValue(0);
		editToolsSeparatorHider.setVisible(false);
		colorOverrideHider.setVisible(false);
		boldBackgroundColorOverrideHider.setVisible(false);
		clearRotationButtonHider.setVisible(false);
		if (editButton.isSelected() && lastSelected != null)
		{
			editTextField.setText(lastSelected.value);
			editTextField.requestFocus();
		}
		actionsSeperatorHider.setVisible((editButton.isSelected() && lastSelected != null) || addButton.isSelected() || eraseButton.isSelected());

		// For some reason this is necessary to prevent the text editing field
		// from flattening sometimes.
		if (getToolOptionsPanel() != null)
		{
			getToolOptionsPanel().revalidate();
			getToolOptionsPanel().repaint();
		}

		brushSizeHider.setVisible(eraseButton.isSelected());
		mapEditingPanel.clearHighlightedAreas();
		mapEditingPanel.repaint();
		mapEditingPanel.hideBrush();
	}

	@Override
	public String getToolbarName()
	{
		return "Text";
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
	public String getImageIconFilePath()
	{
		return Paths.get(Assets.getAssetsPath(), "internal/Text tool.png").toString();
	}

	@Override
	public void onBeforeSaving()
	{
		handleSelectingTextToEdit(lastSelected, false);
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

		if (eraseButton.isSelected())
		{
			deleteTexts(e.getPoint());
		}
		else if (addButton.isSelected())
		{
			// This is differed if the map is currently drawing so that we don't try to generate text while the text drawer is reprocessing
			// books after a book checkbox was checked.
			updater.dowWhenMapIsNotDrawing(() ->
			{
				if (addButton.isSelected())
				{
					MapText addedText = updater.mapParts.nameCreator.createUserAddedText((TextType) textTypeComboBox.getSelectedItem(),
							getPointOnGraph(e.getPoint()), mainWindow.displayQualityScale);
					mainWindow.edits.text.add(addedText);

					undoer.setUndoPoint(UpdateType.Incremental, this);

					lastSelected = addedText;
					changeToEditModeAndSelectText(addedText, true);

					updater.createAndShowMapIncrementalUsingText(Arrays.asList(addedText));
				}
			});
		}
		else if (editButton.isSelected())
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
				RotatedRectangle line2 = lastSelected.line2Bounds == null ? null
						: lastSelected.line2Bounds.translate(new nortantis.geom.Point(deltaX, deltaY));
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
		else if (eraseButton.isSelected())
		{
			deleteTexts(e.getPoint());
		}
	}
	
	private double calcRotationAngle(MouseEvent e)
	{
		nortantis.geom.Point graphPointMouseLocation = getPointOnGraph(e.getPoint());
		nortantis.geom.Point graphPointMousePressedLocation = getPointOnGraph(mousePressedLocation);

		// Find the bounding box currently displayed
		RotatedRectangle boundingBox = lastSelected.line1Bounds
				.addRotatedRectangleThatHasTheSameAngleAndPivot(lastSelected.line2Bounds);

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
				Point translation = new Point(
						(int) ((graphPointMouseLocation.x - graphPointMousePressedLocation.x) / mainWindow.displayQualityScale),
						(int) ((graphPointMouseLocation.y - graphPointMousePressedLocation.y) / mainWindow.displayQualityScale));
				lastSelected.location = new nortantis.geom.Point(lastSelected.location.x + translation.x,
						+lastSelected.location.y + translation.y);
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

		if (eraseButton.isSelected())
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
		editButton.setSelected(true);
		handleActionChanged();
		handleSelectingTextToEdit(selectedText, grabFocus);
	}

	private void handleSelectingTextToEdit(MapText selectedText, boolean grabFocus)
	{
		mapEditingPanel.clearHighlightedAreas();

		if (lastSelected != null && !(editTextField.getText().trim().equals(lastSelected.value)
				&& textTypeComboBox.getSelectedItem().equals(lastSelected.type)
				&& lastSelected.lineBreak.equals(lineBreakComboBox.getSelectedItem())
				&& Objects.equals(lastSelected.colorOverride, AwtFactory.wrap(colorOverrideDisplay.getBackground())) && Objects.equals(
						lastSelected.boldBackgroundColorOverride, AwtFactory.wrap(boldBackgroundColorOverrideDisplay.getBackground()))))
		{
			MapText before = lastSelected.deepCopy();
			// The user changed the last selected text. Need to save the change.
			lastSelected.value = editTextField.getText().trim();
			lastSelected.type = (TextType) textTypeComboBox.getSelectedItem();
			lastSelected.lineBreak = (LineBreak) lineBreakComboBox.getSelectedItem();
			lastSelected.colorOverride = colorOverrideHider.isVisible() ? AwtFactory.wrap(colorOverrideDisplay.getBackground()) : null;
			lastSelected.boldBackgroundColorOverride = boldBackgroundColorOverrideHider.isVisible()
					? AwtFactory.wrap(boldBackgroundColorOverrideDisplay.getBackground())
					: null;
			lastSelected.curvature = curvatureSlider.getValue() / ((double) curvatureSliderDivider);
			lastSelected.spacing = spacingSlider.getValue();

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
			curvatureSliderHider.setVisible(true);
			spacingSliderHider.setVisible(true);
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
				colorOverrideDisplay.setBackground(AwtFactory.unwrap(selectedText.colorOverride));
			}
			if (selectedText.boldBackgroundColorOverride != null)
			{
				boldBackgroundColorOverrideDisplay.setBackground(AwtFactory.unwrap(selectedText.boldBackgroundColorOverride));
			}
			curvatureSlider.setValue((int) (selectedText.curvature * curvatureSliderDivider));
			spacingSlider.setValue(selectedText.spacing);
		}
		actionsSeperatorHider.setVisible((editButton.isSelected() && selectedText != null) || addButton.isSelected() || eraseButton.isSelected());
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
		curvatureSliderHider.setVisible(false);
		spacingSliderHider.setVisible(false);
		editToolsSeparatorHider.setVisible(false);
		colorOverrideHider.setVisible(false);
		boldBackgroundColorOverrideHider.setVisible(false);
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
		if (editButton.isSelected() && lastSelected != null)
		{
			return lastSelected;
		}
		return null;
	}

	@Override
	public void onSwitchingAway()
	{
		// Keep any text edits being done and clear the selected text.
		if (editButton.isSelected())
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

		if (eraseButton.isSelected())
		{
			List<MapText> mapTextsSelected = getMapTextsSelectedByCurrentBrushSizeAndShowBrush(mouseLocation);
			mapEditingPanel.setHighlightedAreasFromTexts(mapTextsSelected);
		}
		else if (editButton.isSelected() && lastSelected == null)
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
		int brushDiameter = editButton.isSelected() ? 1 : brushSizes.get(brushSizeComboBox.getSelectedIndex());
		if (brushDiameter > 1)
		{
			mapEditingPanel.showBrush(mouseLocation, brushDiameter);
			mapTextsSelected = mainWindow.edits.findTextSelectedByBrush(getPointOnGraph(mouseLocation),
					(brushDiameter / mainWindow.zoom) * mapEditingPanel.osScale);
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
	public void loadSettingsIntoGUI(MapSettings settings, boolean isUndoRedoOrAutomaticChange, boolean changeEffectsBackgroundImages,
			boolean willDoImagesRefresh)
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
		if (editButton.isSelected() && lastSelected != null && boldBackgroundVisibleChanged)
		{
			handleSelectingTextToEdit(lastSelected, false);
		}

		handleEnablingAndDisabling(settings);
		drawTextDisabledLabelHider.setVisible(!settings.drawText);
		if (!settings.drawText)
		{
			if (editButton.isSelected())
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
		if (editButton.isSelected() && lastSelected != null)
		{
			lastSelected = null;
			editTextField.setText("");
			hideTextEditComponents();
		}
	}

}
