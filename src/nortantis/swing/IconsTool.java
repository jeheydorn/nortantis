package nortantis.swing;

import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;

import org.apache.commons.lang3.StringUtils;
import org.imgscalr.Scalr.Method;

import nortantis.DebugFlags;
import nortantis.IconDrawTask;
import nortantis.IconDrawer;
import nortantis.IconType;
import nortantis.ImageAndMasks;
import nortantis.ImageCache;
import nortantis.MapSettings;
import nortantis.editor.CenterEdit;
import nortantis.editor.CenterIcon;
import nortantis.editor.CenterIconType;
import nortantis.editor.CenterTrees;
import nortantis.editor.FreeIcon;
import nortantis.editor.MapUpdater;
import nortantis.geom.IntDimension;
import nortantis.geom.Point;
import nortantis.geom.Rectangle;
import nortantis.geom.RotatedRectangle;
import nortantis.graph.voronoi.Center;
import nortantis.platform.Color;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.Painter;
import nortantis.platform.awt.AwtFactory;
import nortantis.util.Assets;
import nortantis.util.ConcurrentHashMapF;
import nortantis.util.Counter;
import nortantis.util.HashCounter;
import nortantis.util.ImageHelper;
import nortantis.util.Logger;
import nortantis.util.Range;
import nortantis.util.Tuple2;
import nortantis.util.Tuple3;
import nortantis.util.Tuple4;

public class IconsTool extends EditorTool
{
	private JRadioButton mountainsButton;
	private JRadioButton treesButton;
	private JComboBox<ImageIcon> brushSizeComboBox;
	private RowHider brushSizeHider;
	private JRadioButton hillsButton;
	private JRadioButton dunesButton;
	private IconTypeButtons mountainTypes;
	private IconTypeButtons hillTypes;
	private IconTypeButtons duneTypes;
	private IconTypeButtons treeTypes;
	private NamedIconSelector cityButtons;
	private NamedIconSelector decorationButtons;
	private JSlider densitySlider;
	private Random rand;
	private RowHider densityHider;
	private JRadioButton citiesButton;
	private JRadioButton decorationsButton;
	private DrawModeWidget modeWidget;
	private Set<FreeIcon> iconsToEdit;
	private java.awt.Point editStart;
	private boolean isMoving;
	private boolean isScaling;
	private JComboBox<String> artPackComboBox;
	private RowHider iconTypeButtonsHider;
	private JCheckBox mountainsCheckbox;
	private JCheckBox hillsCheckbox;
	private JCheckBox dunesCheckbox;
	private JCheckBox treesCheckbox;
	private JCheckBox citiesCheckbox;
	private JCheckBox decorationsCheckbox;
	private RowHider iconTypeCheckboxesHider;
	private RowHider colorPickerHider;
	private JPanel colorDisplay;
	private Map<IconType, Color> iconColorsByType;
	private RowHider artPackComboBoxHider;
	private boolean disableImageRefreshes;
	private RowHider modeOptionsAndBrushSeperatorHider;
	private RowHider deleteCopyPasteIconButtonsHider;
	private RowHider editOptionsSeperatorHider;
	private JLabel groupLabel;
	private JLabel nameLabel;
	private RowHider iconMetadataHider;
	private JLabel artPackLabel;
	private JLabel typeLabel;
	private ControlClickBehaviorWidget controlClickBehavior;
	private RowHider controlClickBehaviorHider;
	private Set<FreeIcon> copied;


	public IconsTool(MainWindow parent, ToolsPanel toolsPanel, MapUpdater mapUpdater)
	{
		super(parent, toolsPanel, mapUpdater);
		rand = new Random();
		namedIconPreviewCache = new ConcurrentHashMapF<>();
		groupPreviewCache = new ConcurrentHashMapF<>();
		iconColorsByType = new TreeMap<>();
		iconsToEdit = new HashSet<>();
		copied = new HashSet<>();
	}

	@Override
	public String getToolbarName()
	{
		return "Icons";
	}

	@Override
	public int getMnemonic()
	{
		return KeyEvent.VK_X;
	}

	@Override
	public String getKeyboardShortcutText()
	{
		return "(Alt+X)";
	}

	@Override
	public String getImageIconFilePath()
	{
		return Paths.get(Assets.getAssetsPath(), "internal/Icon tool.png").toString();
	}

	@Override
	public void onBeforeSaving()
	{
	}

	@Override
	public void onSwitchingAway()
	{
		mapEditingPanel.clearAllToolSpecificSelectionsAndHighlights();
		unselectAnyIconsBeingEdited();
		mapEditingPanel.repaint();
		updateTypePanels();
	}

	@Override
	protected JPanel createToolOptionsPanel()
	{
		GridBagOrganizer organizer = new GridBagOrganizer();

		JPanel toolOptionsPanel = organizer.panel;
		toolOptionsPanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

		modeWidget = new DrawModeWidget("Draw using the selected brush", "Erase using the selected brush", true,
				"Use the selected brush to replace existing icons of the same type", true, "Move or scale individual icons",
				() -> handleModeChanged());
		modeWidget.addToOrganizer(organizer, "");

		// Icon type radio buttons
		{
			ButtonGroup group = new ButtonGroup();
			List<JComponent> radioButtons = new ArrayList<>();

			mountainsButton = new JRadioButton("Mountains");
			group.add(mountainsButton);
			radioButtons.add(mountainsButton);
			mountainsButton.setSelected(true);
			mountainsButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent event)
				{
					updateTypePanels();
				}
			});

			hillsButton = new JRadioButton("Hills");
			group.add(hillsButton);
			radioButtons.add(hillsButton);
			hillsButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent event)
				{
					updateTypePanels();
				}
			});

			dunesButton = new JRadioButton("Dunes");
			group.add(dunesButton);
			radioButtons.add(dunesButton);
			dunesButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent event)
				{
					updateTypePanels();
				}
			});

			treesButton = new JRadioButton("Trees");
			group.add(treesButton);
			radioButtons.add(treesButton);
			treesButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent event)
				{
					updateTypePanels();
				}
			});

			citiesButton = new JRadioButton("Cities");
			group.add(citiesButton);
			radioButtons.add(citiesButton);
			citiesButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent event)
				{
					updateTypePanels();
				}
			});

			decorationsButton = new JRadioButton("Decorations");
			group.add(decorationsButton);
			radioButtons.add(decorationsButton);
			decorationsButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent event)
				{
					updateTypePanels();
				}
			});

			iconTypeButtonsHider = organizer.addLabelAndComponentsVertical("Type:", "The type of icon to add/replace.", radioButtons);
		}

		// Icon type checkboxes
		{
			List<JCheckBox> checkBoxes = new ArrayList<>();

			mountainsCheckbox = new JCheckBox("Mountains");
			checkBoxes.add(mountainsCheckbox);

			hillsCheckbox = new JCheckBox("Hills");
			checkBoxes.add(hillsCheckbox);

			dunesCheckbox = new JCheckBox("Dunes");
			checkBoxes.add(dunesCheckbox);

			treesCheckbox = new JCheckBox("Trees");
			checkBoxes.add(treesCheckbox);

			citiesCheckbox = new JCheckBox("Cities");
			checkBoxes.add(citiesCheckbox);

			decorationsCheckbox = new JCheckBox("Decorations");
			checkBoxes.add(decorationsCheckbox);

			iconTypeCheckboxesHider = organizer.addLabelAndComponentsVertical("Types:", "Filters the type of icons to select.", checkBoxes);

			JButton checkAll = new JButton("Check All");
			checkAll.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					checkBoxes.stream().forEach(button -> button.setSelected(true));
				}
			});

			JButton uncheckAll = new JButton("Uncheck All");
			uncheckAll.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					checkBoxes.stream().forEach(button -> button.setSelected(false));
				}
			});
			checkBoxes.stream().forEach(button -> button.setSelected(true));
			iconTypeCheckboxesHider.add(organizer.addLabelAndComponentsHorizontal("", "", Arrays.asList(checkAll, uncheckAll)));
			iconTypeCheckboxesHider.setVisible(false);
		}


		Tuple2<JComboBox<ImageIcon>, RowHider> brushSizeTuple = organizer.addBrushSizeComboBox(brushSizes);
		brushSizeComboBox = brushSizeTuple.getFirst();
		brushSizeHider = brushSizeTuple.getSecond();


		{
			controlClickBehavior = new ControlClickBehaviorWidget();
			controlClickBehaviorHider = controlClickBehavior.addToOrganizer(organizer);
		}


		modeOptionsAndBrushSeperatorHider = organizer.addSeperator();

		{
			densitySlider = new JSlider(1, 50);
			densitySlider.setValue(7);
			SwingHelper.setSliderWidthForSidePanel(densitySlider);
			SliderWithDisplayedValue sliderWithDisplay = new SliderWithDisplayedValue(densitySlider);
			densityHider = sliderWithDisplay.addToOrganizer(organizer, "Density:", "");
		}

		{
			typeLabel = new JLabel();
			iconMetadataHider = organizer.addLabelAndComponent("Type: ", "The type of this icon.", typeLabel);
			artPackLabel = new JLabel();
			iconMetadataHider.add(organizer.addLabelAndComponent("Art pack: ", "The art pack this icon is from.", artPackLabel, 0));
			groupLabel = new JLabel();
			iconMetadataHider.add(organizer.addLabelAndComponent("Group: ", "The icon group folder this icon is from.", groupLabel, 0));
			nameLabel = new JLabel();
			iconMetadataHider.add(organizer.addLabelAndComponent("Name: ",
					"The icon's file name, not including modifiers or the extension.", nameLabel, 0));
		}

		{
			colorDisplay = SwingHelper.createColorPickerPreviewPanel();
			JButton chooseColorButton = new JButton("Choose");
			chooseColorButton.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					SwingHelper.showColorPicker(organizer.panel, colorDisplay, "Icon Color", () ->
					{
						handleColorChange();
					});
				}
			});
			JButton clearColorButton = new JButton("x");
			clearColorButton.setToolTipText("Clear icon color");
			SwingHelper.addListener(clearColorButton, () ->
			{
				colorDisplay.setBackground(AwtFactory.unwrap(MapSettings.defaultIconColor));
				colorDisplay.repaint();
				organizer.panel.repaint();
				handleColorChange();
			});
			colorPickerHider = organizer.addLabelAndComponentsHorizontal("Color:", "Color to fill transparent pixels in the icon with",
					Arrays.asList(colorDisplay, chooseColorButton, clearColorButton));

		}

		editOptionsSeperatorHider = organizer.addSeperator();

		{
			JButton deleteButton;
			{
				deleteButton = new JButton("Delete");
				deleteButton.setToolTipText("Delete the selected icons (DELETE key)");

				// Define the action to perform
				Action deleteAction = new AbstractAction()
				{
					@Override
					public void actionPerformed(ActionEvent e)
					{
						deleteButton.doClick();
					}
				};

				// Bind DELETE key to the button
				InputMap inputMap = deleteButton.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
				ActionMap actionMap = deleteButton.getActionMap();
				inputMap.put(KeyStroke.getKeyStroke("DELETE"), "deleteAction");
				actionMap.put("deleteAction", deleteAction);

				deleteButton.addActionListener(new ActionListener()
				{
					@Override
					public void actionPerformed(ActionEvent e)
					{
						deleteSelectedIcons();
					}
				});

			}

			JButton copyButton;
			{
				copyButton = new JButton("Copy");
				copyButton.setToolTipText("Copy the selected icons (Ctrl+C)");
				// Define the action to perform
				Action copyAction = new AbstractAction("Copy")
				{
					@Override
					public void actionPerformed(ActionEvent e)
					{
						copyButton.doClick();
					}
				};

				// Register the shortcut in the input map
				InputMap inputMap = copyButton.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
				ActionMap actionMap = copyButton.getActionMap();

				KeyStroke ctrlC = KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK);
				inputMap.put(ctrlC, "copyAction");
				actionMap.put("copyAction", copyAction);

				copyButton.addActionListener(new ActionListener()
				{
					@Override
					public void actionPerformed(ActionEvent e)
					{
						copySelectedIcons();
					}
				});
			}

			JButton pasteButton;
			{
				pasteButton = new JButton("Paste");
				pasteButton.setToolTipText("Paste the selected icons (Ctrl+V)");
				// Define the action to perform
				Action pasteAction = new AbstractAction("Paste")
				{
					@Override
					public void actionPerformed(ActionEvent e)
					{
						pasteButton.doClick();
					}
				};

				// Register the shortcut in the input map
				InputMap inputMap = pasteButton.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
				ActionMap actionMap = pasteButton.getActionMap();

				KeyStroke ctrlV = KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK);
				inputMap.put(ctrlV, "pasteAction");
				actionMap.put("pasteAction", pasteAction);

				pasteButton.addActionListener(new ActionListener()
				{
					@Override
					public void actionPerformed(ActionEvent e)
					{
						pasteSelectedIcons();
					}
				});
			}

			JButton clearScaleButton;
			{
				clearScaleButton = new JButton("Reset Scale");
				clearScaleButton
						.setToolTipText("Set the icon-specific scaling for the selected icons back to the scale they were created at");
				clearScaleButton.addActionListener(new ActionListener()
				{

					@Override
					public void actionPerformed(ActionEvent e)
					{
						clearScaleOnSelectedIcons();
					}
				});
			}

			deleteCopyPasteIconButtonsHider = organizer
					.addLeftAlignedComponents(Arrays.asList(copyButton, pasteButton, deleteButton, clearScaleButton));
		}

		{
			artPackComboBox = new JComboBox<String>();
			updateArtPackOptions(Assets.installedArtPack, null);
			artPackComboBox.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					refreshImagesWithoutClearingCache(mainWindow.getSettingsFromGUI(false));
				}
			});
			artPackComboBoxHider = organizer.addLabelAndComponent("Art pack:", "For filtering the icons shown in this tool. '"
					+ Assets.installedArtPack + "' selects art that comes with Nortantis. '" + Assets.customArtPack
					+ "' selects images from this map's custom images folder, if it has one. Other options are art packs installed on this machine.",
					artPackComboBox);
		}

		mountainTypes = createOrUpdateRadioButtonsForIconType(organizer, IconType.mountains, mountainTypes, Assets.installedArtPack, null);
		hillTypes = createOrUpdateRadioButtonsForIconType(organizer, IconType.hills, hillTypes, Assets.installedArtPack, null);
		duneTypes = createOrUpdateRadioButtonsForIconType(organizer, IconType.sand, duneTypes, Assets.installedArtPack, null);
		treeTypes = createOrUpdateRadioButtonsForIconType(organizer, IconType.trees, treeTypes, Assets.installedArtPack, null);
		selectDefaultTreesButtion(treeTypes);

		createOrUpdateButtonsForCities(organizer, Assets.installedArtPack, null);

		createOrUpdateDecorationButtons(organizer, Assets.installedArtPack, null);

		mountainsButton.doClick();

		organizer.addHorizontalSpacerRowToHelpComponentAlignment(0.666);
		organizer.addVerticalFillerRow();

		// I'm using a KeyEventDispatcher instead of toolsPanel's input map because I need to capture control release events when the focus
		// on is not on the main window. For example, when pressing ctrl+f to search text, the control release action happens when the focus
		// is on the text search box.
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher()
		{

			@Override
			public boolean dispatchKeyEvent(KeyEvent e)
			{
				if (isSelected() && modeWidget.isEditMode() && !isMoving && !isScaling && e.getKeyCode() == KeyEvent.VK_CONTROL)
				{
					if (e.getID() == KeyEvent.KEY_PRESSED)
					{
						addOrRemoveIconHoverHighlightSelection(true);
						mapEditingPanel.hideIconEditTools();
						mapEditingPanel.repaint();
					}
					else if (e.getID() == KeyEvent.KEY_RELEASED)
					{
						addOrRemoveIconHoverHighlightSelection(false);
						boolean isValidPosition = iconsToEdit.stream().anyMatch(icon -> icon.type == IconType.decorations
								|| !updater.mapParts.iconDrawer.isContentBottomTouchingWater(icon));
						mapEditingPanel.showIconEditToolsAt(iconsToEdit, isValidPosition);
						mapEditingPanel.repaint();
					}
				}

				// Return false to allow other KeyEventDispatchers to process the event
				return false;
			}
		});

		return toolOptionsPanel;
	}

	private void deleteSelectedIcons()
	{
		if (modeWidget.isEditMode() && iconsToEdit != null && !iconsToEdit.isEmpty())
		{
			for (FreeIcon icon : iconsToEdit)
			{
				mainWindow.edits.freeIcons.remove(icon);
			}
			undoer.setUndoPoint(UpdateType.Incremental, this);
			updater.createAndShowMapIncrementalUsingIcons(new ArrayList<>(iconsToEdit));
			unselectAnyIconsBeingEdited();
		}
	}

	private void copySelectedIcons()
	{
		copied.clear();
		copied.addAll(iconsToEdit);
	}

	private void pasteSelectedIcons()
	{
		if (copied.isEmpty())
		{
			return;
		}

		List<FreeIcon> pasted = new ArrayList<>();
		double resolutionScale = mainWindow.displayQualityScale;
		Point temp = getPointOnGraph(mapEditingPanel.getMousePosition());
		Point graphPointMouseLocation = temp == null ? null : temp.mult(1.0 / resolutionScale);
		if (graphPointMouseLocation != null)
		{
			Rectangle bounds = mapEditingPanel.getIconEditBounds(copied);
			if (bounds == null)
			{
				return;
			}
			Point center = bounds.getCenter().mult(1.0 / resolutionScale);


			for (FreeIcon icon : copied)
			{
				Point newLocation = new Point(icon.locationResolutionInvariant.x - center.x + graphPointMouseLocation.x,
						icon.locationResolutionInvariant.y - center.y + graphPointMouseLocation.y);
				FreeIcon copied = icon.copyUnanchored().copyWithLocationResolutionInvariant(newLocation);
				mainWindow.edits.freeIcons.addOrReplace(copied);
				pasted.add(copied);
			}

		}
		else
		{
			for (FreeIcon icon : copied)
			{
				final double offset = resolutionScale * 50;
				Point newLocation = new Point(icon.locationResolutionInvariant.x + offset, icon.locationResolutionInvariant.y + offset);
				FreeIcon copied = icon.copyUnanchored().copyWithLocationResolutionInvariant(newLocation);
				mainWindow.edits.freeIcons.addOrReplace(copied);
				pasted.add(copied);

			}
		}

		undoer.setUndoPoint(UpdateType.Incremental, this);
		updater.createAndShowMapIncrementalUsingIcons(pasted);
		iconsToEdit.clear();
		iconsToEdit.addAll(pasted);
		handleIconSelectionChange(true, true);
		mapEditingPanel.repaint();
	}

	private void clearScaleOnSelectedIcons()
	{
		if (iconsToEdit == null || iconsToEdit.isEmpty())
		{
			return;
		}

		List<FreeIcon> updated = new ArrayList<>();
		Set<FreeIcon> unscaled = new HashSet<>();
		for (FreeIcon icon : iconsToEdit)
		{
			FreeIcon withoutScale = icon.copyWithScale(icon.originalScale);
			mainWindow.edits.freeIcons.remove(icon);
			mainWindow.edits.freeIcons.addOrReplace(withoutScale);
			updated.add(icon);
			updated.add(withoutScale);
			unscaled.add(withoutScale);
		}
		iconsToEdit.clear();
		iconsToEdit.addAll(unscaled);
		undoer.setUndoPoint(UpdateType.Incremental, this);
		updater.createAndShowMapIncrementalUsingIcons(updated);
		handleIconSelectionChange(true, true);
	}

	private void handleIconSelectionChange(boolean showEditTools, boolean showIconDetails)
	{
		mapEditingPanel.clearHighlightedAreas();

		if (!iconsToEdit.isEmpty())
		{
			if (iconsToEdit.size() == 1)
			{
				FreeIcon iconToEdit = iconsToEdit.iterator().next();
				if (showEditTools)
				{
					boolean isValidPosition;
					isValidPosition = iconsToEdit.stream().anyMatch(
							icon -> icon.type == IconType.decorations || !updater.mapParts.iconDrawer.isContentBottomTouchingWater(icon));
					mapEditingPanel.showIconEditToolsAt(iconsToEdit, isValidPosition);
				}

				typeLabel.setText(iconToEdit.type.getSingularNameForGUILowerCase());
				artPackLabel.setText(iconToEdit.artPack);
				groupLabel.setText(iconToEdit.groupId);
				if (!StringUtils.isEmpty(iconToEdit.iconName))
				{
					nameLabel.setText(iconToEdit.iconName);
				}
				else
				{
					List<String> fileNames = ImageCache.getInstance(iconToEdit.artPack, mainWindow.customImagesPath)
							.getIconGroupFileNamesWithoutWidthOrExtensionAsList(iconToEdit.type, iconToEdit.groupId);
					String iconName = fileNames.get(iconToEdit.iconIndex % fileNames.size());
					nameLabel.setText(iconName);
				}
				colorDisplay.setBackground(AwtFactory.unwrap(iconToEdit.color));
			}
			else
			{
				if (showEditTools)
				{
					boolean isValidPosition;
					isValidPosition = iconsToEdit.stream().anyMatch(
							icon -> icon.type == IconType.decorations || !updater.mapParts.iconDrawer.isContentBottomTouchingWater(icon));
					mapEditingPanel.showIconEditToolsAt(iconsToEdit, isValidPosition);
				}
				typeLabel.setText(null);
				artPackLabel.setText(null);
				groupLabel.setText(null);
				nameLabel.setText(null);

				Counter<Color> counter = new HashCounter<Color>();
				iconsToEdit.stream().forEach(iconToEdit -> counter.incrementCount(iconToEdit.color));
				colorDisplay.setBackground(AwtFactory.unwrap(counter.argmax()));
			}

			if (DebugFlags.printIconsBeingEdited())
			{
				for (FreeIcon iconToEdit : iconsToEdit)
				{
					System.out.println("Icon being edited: " + iconToEdit);
				}
			}
			colorDisplay.repaint();
			getToolOptionsPane().repaint();

			if (!showEditTools)
			{
				mapEditingPanel.setHighlightedAreasFromIcons(new ArrayList<>(iconsToEdit), updater.mapParts.iconDrawer, false);
			}

			mapEditingPanel.repaint();
		}
		else
		{
			editStart = null;

			typeLabel.setText(null);
			artPackLabel.setText(null);
			groupLabel.setText(null);
			nameLabel.setText(null);

			mapEditingPanel.hideIconEditTools();
			isMoving = false;
			isScaling = false;
			colorDisplay.setBackground(AwtFactory.unwrap(MapSettings.defaultIconColor));
			colorDisplay.repaint();
			mapEditingPanel.repaint();
		}

		showOrHideEditComponents(showIconDetails);
	}

	private void handleColorChange()
	{
		if (modeWidget.isDrawMode() || modeWidget.isReplaceMode())
		{
			IconType selectedType = getSelectedIconType();
			iconColorsByType.put(selectedType, AwtFactory.wrap(colorDisplay.getBackground()));
			ImageCache.clearColoredAndScaledImageCaches();
			updateIconTypeButtonPreviewImages(mainWindow.getSettingsFromGUI(false));
			undoer.setUndoPoint(UpdateType.NoDraw, IconsTool.this, () ->
			{
				updateIconTypeButtonPreviewImages(mainWindow.getSettingsFromGUI(false));
			});
		}
		else if (modeWidget.isEditMode() && iconsToEdit != null && !iconsToEdit.isEmpty())
		{
			List<FreeIcon> updated = new ArrayList<>();
			for (FreeIcon iconToEdit : iconsToEdit)
			{
				FreeIcon updatedIcon = iconToEdit.copyWithColor(AwtFactory.wrap(colorDisplay.getBackground()));
				mainWindow.edits.freeIcons.replace(iconToEdit, updatedIcon);
				updated.add(updatedIcon);

			}
			iconsToEdit.clear();
			iconsToEdit.addAll(updated);
			undoer.setUndoPoint(UpdateType.Incremental, this);
			updater.createAndShowMapIncrementalUsingIcons(new ArrayList<>(iconsToEdit),
					() -> ImageCache.clearColoredAndScaledImageCaches());
		}

	}

	private void setColorPickerColorForSelectedType()
	{
		if (iconColorsByType == null)
		{
			return;
		}

		if (modeWidget.isDrawMode() || modeWidget.isReplaceMode())
		{
			if (mountainsButton.isSelected())
			{
				colorDisplay.setBackground(AwtFactory.unwrap(iconColorsByType.get(IconType.mountains)));
			}
			else if (hillsButton.isSelected())
			{
				colorDisplay.setBackground(AwtFactory.unwrap(iconColorsByType.get(IconType.hills)));
			}
			else if (dunesButton.isSelected())
			{
				colorDisplay.setBackground(AwtFactory.unwrap(iconColorsByType.get(IconType.sand)));
			}
			else if (treesButton.isSelected())
			{
				colorDisplay.setBackground(AwtFactory.unwrap(iconColorsByType.get(IconType.trees)));
			}
			else if (citiesButton.isSelected())
			{
				colorDisplay.setBackground(AwtFactory.unwrap(iconColorsByType.get(IconType.cities)));
			}
			else if (decorationsButton.isSelected())
			{
				colorDisplay.setBackground(AwtFactory.unwrap(iconColorsByType.get(IconType.decorations)));
			}
			colorDisplay.repaint();
		}
	}

	/**
	 * Prevents cacti from being the default tree brush
	 */
	private void selectDefaultTreesButtion(IconTypeButtons typeButtons)
	{
		if (typeButtons.buttons.size() > 1 && typeButtons.buttons.get(0).getText().equals("cacti"))
		{
			typeButtons.buttons.get(1).getRadioButton().setSelected(true);
		}
		else if (typeButtons.buttons.size() > 0)
		{
			typeButtons.buttons.get(0).getRadioButton().setSelected(true);
		}
	}

	private void handleModeChanged()
	{
		unselectAnyIconsBeingEdited();
		mapEditingPanel.repaint();
		updateTypePanels();
	}

	private void showOrHideEditComponents(boolean showIconMetadata)
	{
		modeOptionsAndBrushSeperatorHider.setVisible((modeWidget.isEditMode() && iconsToEdit != null && !iconsToEdit.isEmpty())
				|| modeWidget.isDrawMode() || modeWidget.isReplaceMode());
		editOptionsSeperatorHider.setVisible(modeWidget.isEditMode());
		iconMetadataHider.setVisible(modeWidget.isEditMode() && iconsToEdit != null && iconsToEdit.size() == 1 && showIconMetadata);
		colorPickerHider
				.setVisible(modeWidget.isDrawMode() || modeWidget.isReplaceMode() || modeWidget.isEditMode() && !iconsToEdit.isEmpty());
		deleteCopyPasteIconButtonsHider.setVisible(modeWidget.isEditMode());
	}

	private void showOrHideBrush(java.awt.Point mouseLocation)
	{
		int brushDiameter = getBrushDiameter();
		if (modeWidget.isDrawMode() || brushDiameter <= 1 || (modeWidget.isEditMode()
				&& (mapEditingPanel.isInMoveTool(mouseLocation) || mapEditingPanel.isInScaleTool(mouseLocation))))
		{
			mapEditingPanel.hideBrush();
		}
		else
		{
			mapEditingPanel.showBrush(mouseLocation, brushDiameter);
			mapEditingPanel.repaint();
		}

	}

	private void updateTypePanels()
	{
		mountainTypes.hider.setVisible(mountainsButton.isSelected() && (modeWidget.isDrawMode() || modeWidget.isReplaceMode()));
		hillTypes.hider.setVisible(hillsButton.isSelected() && (modeWidget.isDrawMode() || modeWidget.isReplaceMode()));
		duneTypes.hider.setVisible(dunesButton.isSelected() && (modeWidget.isDrawMode() || modeWidget.isReplaceMode()));
		treeTypes.hider.setVisible(treesButton.isSelected() && (modeWidget.isDrawMode() || modeWidget.isReplaceMode()));
		cityButtons.hider.setVisible(citiesButton.isSelected() && (modeWidget.isDrawMode() || modeWidget.isReplaceMode()));
		decorationButtons.hider.setVisible(decorationsButton.isSelected() && (modeWidget.isDrawMode() || modeWidget.isReplaceMode()));
		densityHider.setVisible(treesButton.isSelected() && (modeWidget.isDrawMode()));
		brushSizeHider.setVisible((modeWidget.isDrawMode() && !citiesButton.isSelected() && !decorationsButton.isSelected())
				|| modeWidget.isReplaceMode() || modeWidget.isEraseMode() || modeWidget.isEditMode());
		setColorPickerColorForSelectedType();
		artPackComboBoxHider.setVisible(modeWidget.isDrawMode() || modeWidget.isReplaceMode());
		iconTypeButtonsHider.setVisible(modeWidget.isDrawMode() || modeWidget.isReplaceMode());
		iconTypeCheckboxesHider.setVisible(modeWidget.isEditMode() || modeWidget.isEraseMode());
		controlClickBehaviorHider.setVisible(modeWidget.isEditMode());
		showOrHideEditComponents(false);

		toolsPanel.revalidate();
		toolsPanel.repaint();
	}

	private IconTypeButtons createOrUpdateRadioButtonsForIconType(GridBagOrganizer organizer, IconType iconType, IconTypeButtons existing,
			String artPack, String customImagesPath)
	{
		String prevSelection = existing != null ? existing.getSelectedOption() : null;

		ButtonGroup group = new ButtonGroup();
		List<RadioButtonWithImage> radioButtons = new ArrayList<>();
		List<String> groupNames = new ArrayList<>(ImageCache.getInstance(artPack, customImagesPath).getIconGroupNames(iconType));
		for (String groupName : groupNames)
		{
			RadioButtonWithImage button = new RadioButtonWithImage(groupName, null);
			group.add(button.getRadioButton());
			radioButtons.add(button);
		}

		List<? extends Component> listToUse = radioButtons.size() > 0 ? radioButtons
				: Arrays.asList(
						new JLabel("<html>The art pack '" + artPack + "' has no " + iconType.toString().toLowerCase() + ".</html>"));
		IconTypeButtons result;
		if (existing == null)
		{
			JPanel buttonsPanel = new JPanel();
			result = new IconTypeButtons(
					organizer.addLabelAndComponentsVerticalWithComponentPanel(iconType.getNameForGUI() + ":", "", listToUse, buttonsPanel),
					radioButtons, buttonsPanel);
		}
		else
		{
			result = existing;
			existing.buttons = radioButtons;
			GridBagOrganizer.updateComponentsPanelVertical(listToUse, existing.buttonsPanel);
		}

		if (prevSelection == null || !result.selectButtonIfPresent(prevSelection))
		{
			if (radioButtons.size() > 0)
			{
				if (iconType == IconType.trees)
				{
					selectDefaultTreesButtion(result);
				}
				else
				{
					radioButtons.get(0).getRadioButton().setSelected(true);
				}
			}
		}

		return result;
	}

	@Override
	public void handleImagesRefresh(MapSettings settings)
	{
		groupPreviewCache.clear();
		namedIconPreviewCache.clear();

		refreshImagesWithoutClearingCache(settings);
	}

	private void refreshImagesWithoutClearingCache(MapSettings settings)
	{
		if (disableImageRefreshes)
		{
			return;
		}

		String customImagesPath = settings == null ? null : settings.customImagesPath;
		String artPack = settings == null ? Assets.installedArtPack : settings.artPack;
		mountainTypes = createOrUpdateRadioButtonsForIconType(null, IconType.mountains, mountainTypes, artPack, customImagesPath);
		hillTypes = createOrUpdateRadioButtonsForIconType(null, IconType.hills, hillTypes, artPack, customImagesPath);
		duneTypes = createOrUpdateRadioButtonsForIconType(null, IconType.sand, duneTypes, artPack, customImagesPath);
		treeTypes = createOrUpdateRadioButtonsForIconType(null, IconType.trees, treeTypes, artPack, customImagesPath);

		createOrUpdateButtonsForCities(null, artPack, customImagesPath);
		createOrUpdateDecorationButtons(null, artPack, customImagesPath);

		// Trigger re-creation of image previews
		loadSettingsIntoGUI(settings, false, true, false);
		unselectAnyIconsBeingEdited();
	}

	private synchronized void updateIconTypeButtonPreviewImages(MapSettings settings)
	{
		if (settings == null)
		{
			return;
		}
		updateOneIconTypeButtonPreviewImages(settings, IconType.mountains, mountainTypes, settings.customImagesPath);
		updateOneIconTypeButtonPreviewImages(settings, IconType.hills, hillTypes, settings.customImagesPath);
		updateOneIconTypeButtonPreviewImages(settings, IconType.sand, duneTypes, settings.customImagesPath);
		updateOneIconTypeButtonPreviewImages(settings, IconType.trees, treeTypes, settings.customImagesPath);

		updateNamedIconButtonPreviewImages(settings, cityButtons);
		updateNamedIconButtonPreviewImages(settings, decorationButtons);
	}

	private void updateOneIconTypeButtonPreviewImages(MapSettings settings, IconType iconType, IconTypeButtons buttons,
			String customImagesPath)
	{
		for (RadioButtonWithImage button : buttons.buttons)
		{
			final String buttonText = button.getText();
			SwingWorker<Image, Void> worker = new SwingWorker<>()
			{
				@Override
				protected Image doInBackground() throws Exception
				{
					return createIconPreviewForGroup(settings, iconType, buttonText, customImagesPath);
				}

				@Override
				public void done()
				{
					Image previewImage;
					try
					{
						previewImage = get();
					}
					catch (InterruptedException | ExecutionException e)
					{
						String message = "Error while creating preview images for buttons: " + e.getMessage();
						Logger.printError(message, e);
						e.printStackTrace();
						JOptionPane.showMessageDialog(IconsTool.this.mainWindow, message, "Error", JOptionPane.ERROR_MESSAGE);
						return;
					}

					button.setImage(AwtFactory.unwrap(previewImage));
				}
			};

			worker.execute();
		}
	}

	private ConcurrentHashMapF<Tuple3<String, IconType, String>, Image> namedIconPreviewCache;

	private void updateNamedIconButtonPreviewImages(MapSettings settings, NamedIconSelector selector)
	{
		if (settings == null)
		{
			return;
		}
		for (String groupId : ImageCache.getInstance(settings.artPack, settings.customImagesPath).getIconGroupNames(selector.type))
		{
			final List<Tuple2<String, UnscaledImageToggleButton>> namesAndButtons = selector.getIconNamesAndButtons(groupId);

			if (namesAndButtons != null)
			{
				SwingWorker<List<Image>, Void> worker = new SwingWorker<>()
				{
					@Override
					protected List<Image> doInBackground() throws Exception
					{
						List<Image> previewImages = new ArrayList<>();
						Map<String, ImageAndMasks> iconsInGroup = ImageCache.getInstance(settings.artPack, settings.customImagesPath)
								.getIconsByNameForGroup(selector.type, groupId);

						for (Tuple2<String, UnscaledImageToggleButton> nameAndButton : namesAndButtons)
						{
							String iconNameWithoutWidthOrExtension = nameAndButton.getFirst();
							if (!iconsInGroup.containsKey(iconNameWithoutWidthOrExtension))
							{
								throw new IllegalArgumentException(
										"No '" + selector.type + "' icon exists for the button '" + iconNameWithoutWidthOrExtension + "'");
							}
							ImageAndMasks imageAndMasks = iconsInGroup.get(iconNameWithoutWidthOrExtension);
							Image preview = namedIconPreviewCache.getOrCreateWithLock(
									new Tuple3<>(settings.artPack, selector.type, iconNameWithoutWidthOrExtension), () ->
									{
										return createIconPreview(settings, Collections.singletonList(imageAndMasks), 45, 0, selector.type);
									});

							previewImages.add(preview);
						}

						return previewImages;
					}

					@Override
					public void done()
					{
						List<Image> previewImages;
						try
						{
							previewImages = get();
						}
						catch (InterruptedException | ExecutionException e)
						{
							String message = "Error while creating preview images for buttons: " + e.getMessage();
							Logger.printError(message, e);
							e.printStackTrace();
							JOptionPane.showMessageDialog(IconsTool.this.mainWindow, message, "Error", JOptionPane.ERROR_MESSAGE);
							return;
						}

						for (int i : new Range(previewImages.size()))
						{
							try
							{
								selector.getIconNamesAndButtons(groupId).get(i).getSecond()
										.setIcon(new ImageIcon(AwtFactory.unwrap(previewImages.get(i))));
							}
							catch (NullPointerException ex)
							{
								Logger.println("While updating icon preview images, the image selectors did not contain group ID: "
										+ groupId + ". If icon previews don't update correctly, try refreshing the map (ctrl+R).");
							}
							catch (IndexOutOfBoundsException ex)
							{
								Logger.println(
										"While updating icon preview images, the image selectors did not have the correct expected of buttons for: "
												+ groupId + ". If icon previews don't update correctly, try refreshing the map (ctrl+R).");
							}
						}
					}
				};

				worker.execute();
			}
		}
	}

	private void updateArtPackOptions(String selectedArtPack, String customImagesPath)
	{
		SwingHelper.initializeComboBoxItems(artPackComboBox, Assets.listArtPacks(!StringUtils.isEmpty(customImagesPath)), selectedArtPack,
				false);
	}

	private void createOrUpdateButtonsForCities(GridBagOrganizer organizer, String artPack, String customImagesPath)
	{
		if (cityButtons == null)
		{
			// This is the first time to create the city buttons.
			cityButtons = new NamedIconSelector(IconType.cities);
			cityButtons.addtoOrganizer(organizer, "Cities: ");
		}

		cityButtons.updateButtonList(artPack, customImagesPath);
	}

	private void createOrUpdateDecorationButtons(GridBagOrganizer organizer, String artPack, String customImagesPath)
	{
		if (decorationButtons == null)
		{
			// This is the first time to create the city buttons.
			decorationButtons = new NamedIconSelector(IconType.decorations);
			decorationButtons.addtoOrganizer(organizer, "Decorations: ");
		}

		decorationButtons.updateButtonList(artPack, customImagesPath);
	}

	private ConcurrentHashMapF<Tuple3<String, IconType, String>, Image> groupPreviewCache;

	private Image createIconPreviewForGroup(MapSettings settings, IconType iconType, String groupName, String customImagesPath)
	{
		return groupPreviewCache.getOrCreateWithLock(new Tuple3<>(settings.artPack, iconType, groupName), () ->
		{
			List<ImageAndMasks> images = ImageCache.getInstance(settings.artPack, customImagesPath).getIconsInGroup(iconType, groupName);
			return createIconPreview(settings, images, 30, 9, iconType);
		});
	}

	private Image createIconPreview(MapSettings settings, List<ImageAndMasks> imagesAndMasks, int scaledHeight, int padding,
			IconType iconType)
	{
		final double osScaling = SwingHelper.getOSScale();
		final int maxRowWidth = (int) (168 * osScaling);
		final int horizontalPaddingBetweenImages = (int) (2 * osScaling);

		padding = (int) (padding * osScaling);
		scaledHeight = (int) (scaledHeight * osScaling);

		// Find the size needed for the preview
		int rowCount = 1;
		int largestRowWidth = 0;
		{
			int rowWidth = 0;
			for (int i : new Range(imagesAndMasks.size()))
			{
				ImageAndMasks imageAndMasks = imagesAndMasks.get(i);
				Image cropped = imageAndMasks.cropToContent();
				int scaledWidth = Math.min(maxRowWidth, ImageHelper.getWidthWhenScaledByHeight(cropped, scaledHeight));
				if (rowWidth + scaledWidth > maxRowWidth)
				{
					rowCount++;
					rowWidth = scaledWidth;
				}
				else
				{
					rowWidth += scaledWidth;
					if (i < imagesAndMasks.size() - 1)
					{
						rowWidth += horizontalPaddingBetweenImages;
					}
				}

				largestRowWidth = Math.max(largestRowWidth, rowWidth);
			}
		}

		// Create the background image for the preview
		final int fadeWidth = Math.max(padding - 2, 0);
		// Multiply the width padding by 2.2 instead of 2 to compensate for the
		// image library I'm using not always scaling to the size I
		// give.
		IntDimension size = new IntDimension(Math.min(maxRowWidth, largestRowWidth) + ((int) (padding * 2.2)),
				(rowCount * scaledHeight) + (padding * 2));

		Image previewImage;

		Path backgroundImagePath = settings.getBackgroundImagePath().getFirst();
		Tuple4<Image, ImageHelper.ColorifyAlgorithm, Image, ImageHelper.ColorifyAlgorithm> tuple = ThemePanel
				.createBackgroundImageDisplaysImages(size, settings.backgroundRandomSeed, settings.colorizeOcean, settings.colorizeLand,
						settings.generateBackground, settings.generateBackgroundFromTexture, settings.solidColorBackground,
						backgroundImagePath == null ? null : backgroundImagePath.toString());
		if (iconType == IconType.decorations)
		{
			previewImage = tuple.getFirst();
			previewImage = ImageHelper.colorify(previewImage, settings.oceanColor, tuple.getSecond());
		}
		else
		{
			previewImage = tuple.getThird();
			previewImage = ImageHelper.colorify(previewImage, settings.landColor, tuple.getFourth());
		}

		previewImage = fadeEdges(previewImage, fadeWidth);

		Painter p = previewImage.createPainter();

		int x = padding;
		int y = padding;
		for (int i : new Range(imagesAndMasks.size()))
		{
			ImageAndMasks imageAndMasks = imagesAndMasks.get(i);
			Image image = ImageCache.getInstance(settings.artPack, settings.customImagesPath).getColoredImage(imageAndMasks,
					iconColorsByType.get(iconType));
			image = imageAndMasks.cropToContent(image);
			int widthForHeight = ImageHelper.getWidthWhenScaledByHeight(image, scaledHeight);
			int scaledWidth = Math.min(widthForHeight, maxRowWidth);
			int yExtraForCentering = 0;
			if (scaledHeight > ImageHelper.getHeightWhenScaledByWidth(image, scaledWidth))
			{
				yExtraForCentering = (scaledHeight - ImageHelper.getHeightWhenScaledByWidth(image, scaledWidth)) / 2;
			}
			Image scaled = ImageHelper.scaleByWidth(image, scaledWidth, Method.ULTRA_QUALITY);
			if (x - padding + scaled.getWidth() > maxRowWidth)
			{
				x = padding;
				y += scaledHeight;
			}

			p.drawImage(scaled, x, y + yExtraForCentering);

			x += scaled.getWidth();
			if (i < imagesAndMasks.size() - 1)
			{
				x += horizontalPaddingBetweenImages;
			}
		}

		return previewImage;
	}

	private Image fadeEdges(Image image, int fadeWidth)
	{
		Image box = Image.create(image.getWidth(), image.getHeight(), ImageType.Grayscale8Bit);
		Painter p = box.createPainter();
		p.setColor(Color.white);
		p.fillRect(fadeWidth, fadeWidth, image.getWidth() - fadeWidth * 2, image.getHeight() - fadeWidth * 2);
		p.dispose();

		// Use convolution to make a hazy background for the text.
		Image hazyBox = ImageHelper.convolveGrayscale(box, ImageHelper.createGaussianKernel(fadeWidth), true, false);

		return ImageHelper.setAlphaFromMask(image, hazyBox, false);
	}

	@Override
	protected void handleMouseClickOnMap(MouseEvent e)
	{
	}

	private void handleMousePressOrDrag(MouseEvent e, boolean isPress)
	{
		showOrHideBrush(e.getPoint());
		if (modeWidget.isDrawMode())
		{
			handleDrawIcons(e, isPress);
		}
		else if (modeWidget.isReplaceMode())
		{
			handleReplaceIcons(e);
		}
		else if (modeWidget.isEraseMode())
		{
			handleEraseIcons(e);
		}
		else if (modeWidget.isEditMode())
		{
			handleEditIcons(e, isPress);
		}
	}

	private void handleDrawIcons(MouseEvent e, boolean isPress)
	{
		if (treesButton.isSelected())
		{
			eraseTreesThatFailedToDrawDueToLowDensity(e);
		}

		if (mountainsButton.isSelected())
		{
			Set<Center> selected = getSelectedLandCenters(e.getPoint());
			String groupId = mountainTypes.getSelectedOption();
			if (!StringUtils.isEmpty(groupId))
			{
				for (Center center : selected)
				{
					CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
					CenterIcon newIcon = new CenterIcon(CenterIconType.Mountain, (String) artPackComboBox.getSelectedItem(), groupId,
							Math.abs(rand.nextInt()));
					mainWindow.edits.centerEdits.put(center.index, cEdit.copyWithIcon(newIcon));
				}
				updater.createAndShowMapIncrementalUsingCenters(selected);
			}
		}
		else if (hillsButton.isSelected())
		{
			Set<Center> groupId = getSelectedLandCenters(e.getPoint());
			String rangeId = hillTypes.getSelectedOption();
			if (!StringUtils.isEmpty(rangeId))
			{
				for (Center center : groupId)
				{
					CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
					CenterIcon newIcon = new CenterIcon(CenterIconType.Hill, (String) artPackComboBox.getSelectedItem(), rangeId,
							Math.abs(rand.nextInt()));
					mainWindow.edits.centerEdits.put(center.index, cEdit.copyWithIcon(newIcon));
				}
				updater.createAndShowMapIncrementalUsingCenters(groupId);
			}
		}
		else if (dunesButton.isSelected())
		{
			Set<Center> selected = getSelectedLandCenters(e.getPoint());
			String groupId = duneTypes.getSelectedOption();
			if (!StringUtils.isEmpty(groupId))
			{
				for (Center center : selected)
				{
					CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
					CenterIcon newIcon = new CenterIcon(CenterIconType.Dune, (String) artPackComboBox.getSelectedItem(), groupId,
							Math.abs(rand.nextInt()));
					mainWindow.edits.centerEdits.put(center.index, cEdit.copyWithIcon(newIcon));
				}
				updater.createAndShowMapIncrementalUsingCenters(selected);
			}
		}
		else if (treesButton.isSelected())
		{
			Set<Center> selected = getSelectedLandCenters(e.getPoint());
			String treeType = treeTypes.getSelectedOption();
			if (!StringUtils.isEmpty(treeType))
			{
				for (Center center : selected)
				{
					CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
					CenterTrees newTrees = new CenterTrees((String) artPackComboBox.getSelectedItem(), treeType,
							densitySlider.getValue() / 10.0, Math.abs(rand.nextLong()));
					mainWindow.edits.centerEdits.put(center.index, cEdit.copyWithTrees(newTrees));
				}
				updater.createAndShowMapIncrementalUsingCenters(selected);
			}
		}
		else if (citiesButton.isSelected())
		{
			Set<Center> selected = getSelectedLandCenters(e.getPoint());
			Tuple2<String, String> selectedCity = cityButtons.getSelectedButton();
			if (selectedCity == null)
			{
				return;
			}

			String cityType = selectedCity.getFirst();
			String cityName = selectedCity.getSecond();
			for (Center center : selected)
			{
				CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
				CenterIcon cityIcon = new CenterIcon(CenterIconType.City, (String) artPackComboBox.getSelectedItem(), cityType, cityName);
				mainWindow.edits.centerEdits.put(center.index, cEdit.copyWithIcon(cityIcon));
			}
			updater.createAndShowMapIncrementalUsingCenters(selected);
		}
		else if (decorationsButton.isSelected())
		{
			if (isPress)
			{
				Tuple2<String, String> selectedButton = decorationButtons.getSelectedButton();
				if (selectedButton == null)
				{
					return;
				}

				String groupId = selectedButton.getFirst();
				String iconName = selectedButton.getSecond();
				nortantis.geom.Point point = getPointOnGraph(e.getPoint());
				FreeIcon icon = new FreeIcon(mainWindow.displayQualityScale, point, 1.0, IconType.decorations,
						(String) artPackComboBox.getSelectedItem(), groupId, iconName, null, getSelectedIconTypeColor());
				mainWindow.edits.freeIcons.addOrReplace(icon);
				updater.createAndShowMapIncrementalUsingIcons(Arrays.asList(icon));
			}
		}
	}

	private Color getSelectedIconTypeColor()
	{
		IconType selectedType = getSelectedIconType();
		return iconColorsByType.get(selectedType);
	}

	private IconType getSelectedIconType()
	{
		if (mountainsButton.isSelected())
		{
			return IconType.mountains;
		}
		else if (hillsButton.isSelected())
		{
			return IconType.hills;
		}
		else if (dunesButton.isSelected())
		{
			return IconType.sand;
		}
		else if (treesButton.isSelected())
		{
			return IconType.trees;
		}
		else if (citiesButton.isSelected())
		{
			return IconType.cities;
		}
		else if (decorationsButton.isSelected())
		{
			return IconType.decorations;
		}
		else
		{
			throw new UnsupportedOperationException("Unrecognized icon type button.");
		}
	}

	private void handleReplaceIcons(MouseEvent e)
	{
		if (treesButton.isSelected())
		{
			replaceTreesThatFailedToDrawDueToLowDensity(e);
		}
		List<FreeIcon> iconsSelectedAfter = new ArrayList<>();

		List<FreeIcon> iconsBeforeAndAfterOuter = mainWindow.edits.freeIcons.doWithLockAndReturnResult(() ->
		{
			List<FreeIcon> icons = getSelectedIcons(e.getPoint());
			if (icons.isEmpty())
			{
				return icons;
			}

			List<FreeIcon> iconsBeforeAndAfter = new ArrayList<>();

			for (FreeIcon before : icons)
			{
				iconsBeforeAndAfter.add(before);

				FreeIcon after = null;
				if (mountainsButton.isSelected())
				{
					String groupId = mountainTypes.getSelectedOption();
					if (!StringUtils.isEmpty(groupId))
					{
						after = before.copyWith((String) artPackComboBox.getSelectedItem(), groupId, Math.abs(rand.nextInt()),
								getSelectedIconTypeColor());
					}
				}
				else if (hillsButton.isSelected())
				{
					String groupId = hillTypes.getSelectedOption();
					if (!StringUtils.isEmpty(groupId))
					{
						after = before.copyWith((String) artPackComboBox.getSelectedItem(), groupId, Math.abs(rand.nextInt()),
								getSelectedIconTypeColor());
					}
				}
				else if (dunesButton.isSelected())
				{
					String groupId = duneTypes.getSelectedOption();
					if (!StringUtils.isEmpty(groupId))
					{
						after = before.copyWith((String) artPackComboBox.getSelectedItem(), groupId, Math.abs(rand.nextInt()),
								getSelectedIconTypeColor());
					}
				}
				else if (treesButton.isSelected())
				{
					String treeType = treeTypes.getSelectedOption();
					if (!StringUtils.isEmpty(treeType))
					{
						after = before.copyWith((String) artPackComboBox.getSelectedItem(), treeType, Math.abs(rand.nextInt()),
								getSelectedIconTypeColor());
					}
				}
				else if (citiesButton.isSelected())
				{
					Tuple2<String, String> selectedCity = cityButtons.getSelectedButton();
					if (selectedCity == null)
					{
						continue;
					}

					String cityType = selectedCity.getFirst();
					String cityName = selectedCity.getSecond();
					after = before.copyWith((String) artPackComboBox.getSelectedItem(), cityType, cityName, getSelectedIconTypeColor());
				}
				else if (decorationsButton.isSelected())
				{
					Tuple2<String, String> selectedDecoration = decorationButtons.getSelectedButton();
					if (selectedDecoration == null)
					{
						continue;
					}

					String type = selectedDecoration.getFirst();
					String iconName = selectedDecoration.getSecond();
					after = before.copyWith((String) artPackComboBox.getSelectedItem(), type, iconName, getSelectedIconTypeColor());
				}
				else
				{
					assert false;
					continue;
				}

				if (after != null)
				{
					mainWindow.edits.freeIcons.replace(before, after);
					iconsBeforeAndAfter.add(after);
					if (isSelected(e.getPoint(), after))
					{
						iconsSelectedAfter.add(after);
					}
				}
			}

			return iconsBeforeAndAfter;
		});

		mapEditingPanel.setHighlightedAreasFromIcons(iconsSelectedAfter, updater.mapParts.iconDrawer, true);

		if (iconsBeforeAndAfterOuter != null && !iconsBeforeAndAfterOuter.isEmpty())
		{
			updater.createAndShowMapIncrementalUsingIcons(iconsBeforeAndAfterOuter);
		}
	}

	private void handleEraseIcons(MouseEvent e)
	{
		if (treesCheckbox.isSelected())
		{
			eraseTreesThatFailedToDrawDueToLowDensity(e);
		}

		List<FreeIcon> icons = mainWindow.edits.freeIcons.doWithLockAndReturnResult(() ->
		{
			List<FreeIcon> iconsInner = getSelectedIcons(e.getPoint());
			if (iconsInner.isEmpty())
			{
				return iconsInner;
			}

			mainWindow.edits.freeIcons.removeAll(iconsInner);
			return iconsInner;
		});


		mapEditingPanel.clearHighlightedAreas();
		mapEditingPanel.repaint();

		if (icons != null && !icons.isEmpty())
		{
			Set<RotatedRectangle> processingAreas = icons.stream()
					.map(icon -> new RotatedRectangle(updater.mapParts.iconDrawer.toIconDrawTask(icon).createBounds()))
					.collect(Collectors.toSet());
			mapEditingPanel.addProcessingAreas(processingAreas);
			mapEditingPanel.repaint();
			updater.createAndShowMapIncrementalUsingIcons(icons, () ->
			{
				mapEditingPanel.removeProcessingAreas(processingAreas);
				mapEditingPanel.repaint();
			});
		}
	}

	private void handleEditIcons(MouseEvent e, boolean isPress)
	{
		if (isPress)
		{
			if (iconsToEdit != null && !iconsToEdit.isEmpty())
			{
				isMoving = mapEditingPanel.isInMoveTool(e.getPoint());
				isScaling = mapEditingPanel.isInScaleTool(e.getPoint());
				if (isMoving || isScaling)
				{
					editStart = e.getPoint();
				}
				else
				{
					editStart = null;
				}
			}
			else
			{
				isMoving = false;
				isScaling = false;
				editStart = null;
			}
		}


		if (isMoving || isScaling)
		{
			if (iconsToEdit != null && !iconsToEdit.isEmpty())
			{
				Point graphPointMouseLocation = getPointOnGraph(e.getPoint());
				Point graphPointMousePressedLocation = getPointOnGraph(editStart);

				List<FreeIcon> updated = new ArrayList<>();

				if (isMoving)
				{
					double deltaX = (int) (graphPointMouseLocation.x - graphPointMousePressedLocation.x);
					double deltaY = (int) (graphPointMouseLocation.y - graphPointMousePressedLocation.y);

					for (FreeIcon iconToEdit : iconsToEdit)
					{
						Point scaledOldLocation = iconToEdit.getScaledLocation(mainWindow.displayQualityScale);
						updated.add(iconToEdit.copyWithLocation(mainWindow.displayQualityScale,
								new Point(scaledOldLocation.x + deltaX, scaledOldLocation.y + deltaY)));
					}

				}
				else if (isScaling)
				{
					Rectangle iconEditBounds = mapEditingPanel.getIconEditBounds(iconsToEdit);
					double scale = calcScale(graphPointMouseLocation, graphPointMousePressedLocation, iconEditBounds);

					for (FreeIcon iconToEdit : iconsToEdit)
					{
						Rectangle imageBounds = updater.mapParts.iconDrawer.toIconDrawTask(iconToEdit).createBounds();
						updated.add(iconToEdit.copyWithScale(iconToEdit.scale * floorWithMinScale(scale, imageBounds)));
					}
				}

				if (!updated.isEmpty())
				{
					mapEditingPanel.setHighlightedAreasFromIcons(updated, updater.mapParts.iconDrawer, false);
					boolean isValidPosition = updated.stream().anyMatch(
							icon -> icon.type == IconType.decorations || !updater.mapParts.iconDrawer.isContentBottomTouchingWater(icon));
					mapEditingPanel.showIconEditToolsAt(updated, isValidPosition);
				}
			}

			showOrHideEditComponents(true);
		}
		else
		{
			// Not moving or scaling.

			if (!e.isControlDown() && isPress)
			{
				mapEditingPanel.clearHighlightedAreas();
				iconsToEdit.clear();
				mapEditingPanel.hideIconEditTools();
			}


			if (e.isControlDown())
			{
				List<FreeIcon> selectedIcons = getSelectedIcons(e.getPoint(), controlClickBehavior.isSelectMode() ? null : iconsToEdit);
				Set<FreeIcon> intersection = new HashSet<>();
				intersection.addAll(iconsToEdit);
				intersection.removeAll(selectedIcons);
				iconsToEdit.clear();
				iconsToEdit.addAll(intersection);
				if (controlClickBehavior.isSelectMode())
				{
					iconsToEdit.addAll(selectedIcons);
				}
			}
			else
			{
				List<FreeIcon> selectedIcons = getSelectedIcons(e.getPoint());
				iconsToEdit.addAll(selectedIcons);
			}

			handleIconSelectionChange(false, false);
		}

		mapEditingPanel.repaint();
	}

	private double calcScale(Point graphPointMouseLocation, Point graphPointMousePressedLocation, Rectangle iconEditBounds)
	{
		double scale = graphPointMouseLocation.distanceTo(iconEditBounds.getCenter())
				/ graphPointMousePressedLocation.distanceTo(iconEditBounds.getCenter());

		return floorWithMinScale(scale, iconEditBounds);
	}

	private double floorWithMinScale(double scale, Rectangle imageBounds)
	{
		final double minWidth = 8 * mainWindow.displayQualityScale;
		final double minHeight = 6 * mainWindow.displayQualityScale;
		final double minWidthByHeight = IconDrawer.getDimensionsWhenScaledByHeight(imageBounds.size().toIntDimension(), minHeight).width;

		double minScale = Math.min(1.0, Math.max(minWidth, minWidthByHeight) / imageBounds.width);
		return Math.max(scale, minScale);
	}

	private void handleFinishSelectingOrEditingIconsIfNeeded(MouseEvent e)
	{
		if (iconsToEdit != null && !iconsToEdit.isEmpty())
		{
			if (isMoving || isScaling)
			{
				Point graphPointMouseLocation = getPointOnGraph(e.getPoint());
				Point graphPointMousePressedLocation = getPointOnGraph(editStart);
				List<FreeIcon> updated = new ArrayList<>();
				Rectangle iconEditBounds = mapEditingPanel.getIconEditBounds(iconsToEdit);

				for (FreeIcon iconToEdit : iconsToEdit)
				{
					if (isMoving)
					{
						double deltaX = (int) (graphPointMouseLocation.x - graphPointMousePressedLocation.x);
						double deltaY = (int) (graphPointMouseLocation.y - graphPointMousePressedLocation.y);
						Point scaledOldLocation = iconToEdit.getScaledLocation(mainWindow.displayQualityScale);
						FreeIcon updatedIcon = iconToEdit.copyWithLocation(mainWindow.displayQualityScale,
								new Point(scaledOldLocation.x + deltaX, scaledOldLocation.y + deltaY)).copyUnanchored();
						updated.add(updatedIcon);
						mainWindow.edits.freeIcons.doWithLock(() ->
						{
							mainWindow.edits.freeIcons.replace(iconToEdit, updatedIcon);
						});

						if (iconToEdit.centerIndex != null && !mainWindow.edits.freeIcons.hasTrees(iconToEdit.centerIndex))
						{
							// The user moved the last tree out of the polygon it was anchored to. Remove the invisible CenterTree so that
							// if
							// someone resizes all trees later, trees don't appear out of nowhere on this Center.
							mainWindow.edits.centerEdits.put(iconToEdit.centerIndex,
									mainWindow.edits.centerEdits.get(iconToEdit.centerIndex).copyWithTrees(null));
						}
					}
					else if (isScaling)
					{
						double scale = calcScale(graphPointMouseLocation, graphPointMousePressedLocation, iconEditBounds);
						Rectangle imageBounds = updater.mapParts.iconDrawer.toIconDrawTask(iconToEdit).createBounds();
						FreeIcon updatedIcon = iconToEdit.copyWithScale(iconToEdit.scale * floorWithMinScale(scale, imageBounds));
						updated.add(updatedIcon);
						mainWindow.edits.freeIcons.doWithLock(() ->
						{
							mainWindow.edits.freeIcons.replace(iconToEdit, updatedIcon);
						});
					}
				}

				if (updated != null)
				{
					undoer.setUndoPoint(UpdateType.Incremental, this);
					Set<FreeIcon> beforeAndAfter = new HashSet<>();
					beforeAndAfter.addAll(iconsToEdit);
					beforeAndAfter.addAll(updated);
					updater.createAndShowMapIncrementalUsingIcons(new ArrayList<>(beforeAndAfter));

					iconsToEdit.clear();
					iconsToEdit.addAll(updated);

					boolean isValidPosition = updated.stream().anyMatch(
							icon -> icon.type == IconType.decorations || !updater.mapParts.iconDrawer.isContentBottomTouchingWater(icon));
					mapEditingPanel.showIconEditToolsAt(updated, isValidPosition);
					if (e.isControlDown())
					{
						mapEditingPanel.setHighlightedAreasFromIcons(updated, updater.mapParts.iconDrawer, false);
					}
					else
					{
						mapEditingPanel.clearHighlightedAreas();
					}
					isMoving = false;
					isScaling = false;
				}
				mapEditingPanel.repaint();
			}
			else
			{
				if (!e.isControlDown())
				{
					boolean isValidPosition = iconsToEdit.stream().anyMatch(
							icon -> icon.type == IconType.decorations || !updater.mapParts.iconDrawer.isContentBottomTouchingWater(icon));
					mapEditingPanel.showIconEditToolsAt(iconsToEdit, isValidPosition);
					mapEditingPanel.clearHighlightedAreas();
					mapEditingPanel.repaint();
				}
				showOrHideEditComponents(true);
			}
		}
	}

	public void unselectAnyIconsBeingEdited()
	{
		iconsToEdit.clear();
		isMoving = false;
		isScaling = false;
		editStart = null;
		mapEditingPanel.hideIconEditTools();
		mapEditingPanel.clearHighlightedAreas();
		mapEditingPanel.repaint();
		showOrHideEditComponents(false);
	}

	private void eraseTreesThatFailedToDrawDueToLowDensity(MouseEvent e)
	{
		Set<Center> selected = getSelectedLandCenters(e.getPoint());
		for (Center center : selected)
		{
			mainWindow.edits.centerEdits.put(center.index, mainWindow.edits.centerEdits.get(center.index).copyWithTrees(null));
		}
	}

	private void replaceTreesThatFailedToDrawDueToLowDensity(MouseEvent e)
	{
		Set<Center> selected = getSelectedLandCenters(e.getPoint());
		for (Center center : selected)
		{
			CenterTrees currentTrees = mainWindow.edits.centerEdits.get(center.index).trees;
			if (currentTrees != null)
			{
				String treeType = treeTypes.getSelectedOption();
				if (!StringUtils.isEmpty(treeType))
				{
					CenterTrees newTrees = currentTrees.copyWithTreeType(treeType);
					mainWindow.edits.centerEdits.put(center.index, mainWindow.edits.centerEdits.get(center.index).copyWithTrees(newTrees));
				}
			}
		}
	}

	private Set<Center> getSelectedLandCenters(java.awt.Point point)
	{
		Set<Center> selected = getSelectedCenters(point);
		return selected.stream().filter(c -> !c.isWater).collect(Collectors.toSet());
	}

	@Override
	protected void handleMousePressedOnMap(MouseEvent e)
	{
		handleMousePressOrDrag(e, true);
	}

	@Override
	protected void handleMouseReleasedOnMap(MouseEvent e)
	{
		if (modeWidget.isEditMode())
		{
			handleFinishSelectingOrEditingIconsIfNeeded(e);
		}
		else
		{
			undoer.setUndoPoint(UpdateType.Incremental, this);
		}
	}

	@Override
	protected void handleMouseMovedOnMap(MouseEvent e)
	{
		innerHandleMouseMovedOnMap(e.getPoint(), e.isControlDown());
	}

	private void innerHandleMouseMovedOnMap(java.awt.Point mouseLocation, boolean isControlDown)
	{
		if (mouseLocation == null)
		{
			return;
		}

		if (modeWidget.isDrawMode() && !decorationsButton.isSelected())
		{
			highlightHoverCenters(mouseLocation);
		}
		else
		{
			highlightHoverIconsAndShowBrush(mouseLocation, isControlDown);
		}
		mapEditingPanel.repaint();
	}

	private void highlightHoverCenters(java.awt.Point mouseLocation)
	{
		mapEditingPanel.clearHighlightedAreas();
		mapEditingPanel.clearHighlightedCenters();

		Set<Center> selected = getSelectedCenters(mouseLocation);
		mapEditingPanel.addHighlightedCenters(selected);
		mapEditingPanel.setCenterHighlightMode(HighlightMode.outlineEveryCenter);
		mapEditingPanel.repaint();
	}

	private void highlightHoverIconsAndShowBrush(java.awt.Point mouseLocation, boolean isControlDown)
	{
		mapEditingPanel.clearHighlightedCenters();

		showOrHideBrush(mouseLocation);

		if (modeWidget.isEditMode())
		{
			if (iconsToEdit != null && !iconsToEdit.isEmpty())
			{
				addOrRemoveIconHoverHighlightSelection(isControlDown);
			}
			else
			{
				mapEditingPanel.clearHighlightedAreas();
				List<FreeIcon> selected = getSelectedIcons(mouseLocation);
				if (selected != null && selected.size() > 0)
				{
					mapEditingPanel.setHighlightedAreasFromIcons(selected, updater.mapParts.iconDrawer, false);
				}
			}
		}
		else if (!(modeWidget.isDrawMode() && decorationsButton.isSelected()))
		{
			mapEditingPanel.clearHighlightedAreas();
			List<FreeIcon> icons = getSelectedIcons(mouseLocation);
			mapEditingPanel.setHighlightedAreasFromIcons(icons, updater.mapParts.iconDrawer, true);
		}
		mapEditingPanel.repaint();
	}

	private void addOrRemoveIconHoverHighlightSelection(boolean isControlDown)
	{
		Set<FreeIcon> toHighlight = new HashSet<>();
		if (isControlDown)
		{
			toHighlight.addAll(iconsToEdit);

			java.awt.Point pointOnMap = mapEditingPanel.getMousePosition();
			if (pointOnMap != null)
			{
				if (controlClickBehavior.isSelectMode())
				{
					List<FreeIcon> selected = getSelectedIcons(pointOnMap);
					if (selected != null && selected.size() > 0)
					{
						toHighlight.addAll(selected);
					}
				}
				else
				{
					List<FreeIcon> selected = getSelectedIcons(pointOnMap, iconsToEdit);
					if (selected != null && selected.size() > 0)
					{
						toHighlight.removeAll(selected);
					}
				}
			}
		}
		mapEditingPanel.setHighlightedAreasFromIcons(new ArrayList<>(toHighlight), updater.mapParts.iconDrawer, false);
		mapEditingPanel.repaint();
	}

	@Override
	protected void handleMouseDraggedOnMap(MouseEvent e)
	{
		if (modeWidget.isDrawMode() && !decorationsButton.isSelected())
		{
			highlightHoverCenters(e.getPoint());
		}
		else
		{
			highlightHoverIconsAndShowBrush(e.getPoint(), e.isControlDown());
		}
		handleMousePressOrDrag(e, false);
	}

	@Override
	protected void handleMouseExitedMap(MouseEvent e)
	{
		mapEditingPanel.clearHighlightedCenters();
		if (iconsToEdit != null && !iconsToEdit.isEmpty())
		{
			if (e.isControlDown() || e.getButton() == MouseEvent.BUTTON1)
			{
				mapEditingPanel.setHighlightedAreasFromIcons(new ArrayList<>(iconsToEdit), updater.mapParts.iconDrawer, false);
			}
		}
		else
		{
			mapEditingPanel.clearHighlightedAreas();
		}
		mapEditingPanel.hideBrush();
		mapEditingPanel.repaint();
	}

	@Override
	protected void onAfterShowMap()
	{
		innerHandleMouseMovedOnMap(mapEditingPanel.getMousePosition(), false);

		if (modeWidget.isEditMode() && iconsToEdit != null && !iconsToEdit.isEmpty())
		{
			handleIconSelectionChange(true, true);
		}
		mapEditingPanel.repaint();
	}

	@Override
	protected void onAfterUndoRedo()
	{
		mapEditingPanel.clearHighlightedCenters();
		unselectAnyIconsBeingEdited();
		mapEditingPanel.repaint();
	}

	private Set<Center> getSelectedCenters(java.awt.Point mouseLocation)
	{
		return getSelectedCenters(mouseLocation, getBrushDiameter());
	}

	private List<FreeIcon> getSelectedIcons(java.awt.Point mouseLocation)
	{
		return getSelectedIcons(mouseLocation, null);
	}

	private List<FreeIcon> getSelectedIcons(java.awt.Point mouseLocation, Collection<FreeIcon> allowList)
	{
		int brushDiameter = getBrushDiameter();

		if (brushDiameter <= 1)
		{
			FreeIcon selected = getLowestSelectedIcon(mouseLocation, allowList);
			if (selected != null)
			{
				return Arrays.asList(selected);
			}
			return Collections.emptyList();
		}
		else
		{
			return getMultipleSelectedIcons(mouseLocation, allowList);
		}
	}

	private List<FreeIcon> getMultipleSelectedIcons(java.awt.Point mouseLocation, Collection<FreeIcon> allowList)
	{
		List<FreeIcon> selected = new ArrayList<>();
		mainWindow.edits.freeIcons.doWithLock(() ->
		{
			Iterable<FreeIcon> iterable = allowList == null ? mainWindow.edits.freeIcons : allowList;

			for (FreeIcon icon : iterable)
			{
				if (isSelected(mouseLocation, icon))
				{
					selected.add(icon);
				}
			}
		});

		return selected;
	}

	private boolean isSelected(java.awt.Point mouseLocation, FreeIcon icon)
	{
		int brushDiameter = getBrushDiameter();
		Point graphPoint = getPointOnGraph(mouseLocation);

		if (brushDiameter <= 1)
		{
			if (!isSelectedType(icon))
			{
				return false;
			}

			IconDrawTask task = updater.mapParts.iconDrawer.toIconDrawTask(icon);
			if (task == null)
			{
				return false;
			}

			Rectangle rect = task.createBounds();
			return rect.contains(graphPoint);
		}
		else
		{
			int brushRadius = (int) ((double) ((brushDiameter / mainWindow.zoom)) * mapEditingPanel.osScale) / 2;
			if (!isSelectedType(icon))
			{
				return false;
			}
			RotatedRectangle rect = new RotatedRectangle(updater.mapParts.iconDrawer.toIconDrawTask(icon).createBounds());
			return rect.overlapsCircle(graphPoint, brushRadius);
		}
	}

	protected FreeIcon getLowestSelectedIcon(java.awt.Point mouseLocation, Collection<FreeIcon> allowList)
	{
		List<FreeIcon> underMouse = getMultipleSelectedIcons(mouseLocation, allowList);
		if (underMouse.isEmpty())
		{
			return null;
		}

		FreeIcon lowest = null;
		double lowestTop = Double.NEGATIVE_INFINITY;

		for (FreeIcon icon : underMouse)
		{
			IconDrawTask task = updater.mapParts.iconDrawer.toIconDrawTask(icon);
			if (task != null)
			{
				double top = task.createBounds().getTop();
				if (lowest == null || top > lowestTop)
				{
					lowest = icon;
					lowestTop = top;
				}
			}
		}
		return lowest;
	}

	private int getBrushDiameter()
	{
		if (brushSizeHider.isVisible())
		{
			return brushSizes.get(brushSizeComboBox.getSelectedIndex());
		}

		return brushSizes.get(0);
	}

	private boolean isSelectedType(FreeIcon icon)
	{
		if (modeWidget.isDrawMode() || modeWidget.isReplaceMode())
		{
			if (mountainsButton.isSelected() && icon.type == IconType.mountains)
			{
				return true;
			}

			if (hillsButton.isSelected() && icon.type == IconType.hills)
			{
				return true;
			}

			if (dunesButton.isSelected() && icon.type == IconType.sand)
			{
				return true;
			}

			if (treesButton.isSelected() && icon.type == IconType.trees)
			{
				return true;
			}

			if (decorationsButton.isSelected() && icon.type == IconType.decorations)
			{
				return true;
			}

			if (citiesButton.isSelected() && icon.type == IconType.cities)
			{
				return true;
			}
		}
		else
		{
			if (mountainsCheckbox.isSelected() && icon.type == IconType.mountains)
			{
				return true;
			}

			if (hillsCheckbox.isSelected() && icon.type == IconType.hills)
			{
				return true;
			}

			if (dunesCheckbox.isSelected() && icon.type == IconType.sand)
			{
				return true;
			}

			if (treesCheckbox.isSelected() && icon.type == IconType.trees)
			{
				return true;
			}

			if (decorationsCheckbox.isSelected() && icon.type == IconType.decorations)
			{
				return true;
			}

			if (citiesCheckbox.isSelected() && icon.type == IconType.cities)
			{
				return true;
			}
		}

		return false;
	}

	@Override
	public void loadSettingsIntoGUI(MapSettings settings, boolean isUndoRedoOrAutomaticChange, boolean changeEffectsBackgroundImages,
			boolean willDoImagesRefresh)
	{
		String customImagesPath = settings == null ? null : settings.customImagesPath;
		String artPack = settings == null ? Assets.installedArtPack : settings.artPack;
		String artPackToSelect = isUndoRedoOrAutomaticChange && !StringUtils.isEmpty((String) artPackComboBox.getSelectedItem())
				? (String) artPackComboBox.getSelectedItem()
				: (settings != null ? settings.artPack : Assets.installedArtPack);
		try
		{
			disableImageRefreshes = willDoImagesRefresh;
			updateArtPackOptions(artPackToSelect, customImagesPath);
		}
		finally
		{
			disableImageRefreshes = false;
		}

		if (!Objects.equals(artPackComboBox.getSelectedItem(), artPack) && !isUndoRedoOrAutomaticChange)
		{
			if (Assets.artPackExists(artPack, customImagesPath))
			{
				artPackComboBox.setSelectedItem(artPack);
			}
			else
			{
				artPackComboBox.setSelectedItem(Assets.installedArtPack);
				// Setting this fixes a bug where icon previews for shuffled icons don't show up right after an images refresh when the
				// selected art pack no longer exists. It seems to be because the call to updateIconTypeButtonPreviewImages below was given
				// the wrong art pack when it changed here and wasn't updated.
				settings.artPack = Assets.installedArtPack;
			}
		}

		iconColorsByType.clear();
		for (IconType iconType : IconType.values())
		{
			iconColorsByType.put(iconType, settings.getIconColorForType(iconType));
		}

		updateTypePanels();
		// Skip updating icon previews now if there will be an images refresh in
		// a moment, because that will handle it, and because the
		// ImageCache hasn't been cleared yet.
		if (changeEffectsBackgroundImages && !willDoImagesRefresh)
		{
			updateIconTypeButtonPreviewImages(settings);
		}
	}

	@Override
	public void getSettingsFromGUI(MapSettings settings)
	{
		settings.artPack = (String) artPackComboBox.getSelectedItem();
		assert !StringUtils.isEmpty(settings.artPack);

		// Selected colors per icon type
		for (Map.Entry<IconType, Color> entry : iconColorsByType.entrySet())
		{
			settings.setIconColorForType(entry.getKey(), entry.getValue());
		}
	}

	@Override
	public void handleEnablingAndDisabling(MapSettings settings)
	{
	}

	@Override
	public void onBeforeLoadingNewMap()
	{
	}

	@Override
	protected void onBeforeUndoRedo()
	{
	}

	@Override
	public void handleCustomImagesPathChanged(String customImagesPath)
	{
		if (!StringUtils.isEmpty(customImagesPath))
		{
			artPackComboBox.setSelectedItem(Assets.customArtPack);
		}
	}

}
