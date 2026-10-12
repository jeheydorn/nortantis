package nortantis.swing;

import nortantis.TextBackground;
import nortantis.TextBackgroundEffect;
import nortantis.TextStyle;
import nortantis.TextStylePart;
import nortantis.platform.Color;
import nortantis.platform.Font;
import nortantis.platform.awt.AwtBridge;
import nortantis.swing.translation.Translation;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The controls for a text style: font, size, color, and background. They show one or more styles, and each change the user makes is
 * reported as an edit of only the setting they changed, so that it can be applied to several styles without changing the settings that
 * differ between them.
 */
class TextStyleControls
{
	/**
	 * A change to one setting of a text style, which belongs to the given part of the style.
	 */
	record StyleEdit(TextStylePart part, Consumer<TextStyle> change)
	{
		void apply(TextStyle style)
		{
			change.accept(style);
		}
	}

	static final Integer[] fontSizes = { 6, 7, 8, 9, 10, 11, 12, 14, 16, 18, 20, 22, 24, 26, 28, 36, 48, 72, 96, 120, 144, 168, 192, 216, 240 };
	private static final int fadeSliderDivider = 10;

	private final Consumer<StyleEdit> onEdit;
	private final FontChooser fontChooser;
	private final JComboBox<Integer> sizeComboBox;
	private final JPanel colorDisplay;
	private final JComboBox<TextBackgroundEffect> effectComboBox;
	private final JPanel backgroundColorDisplay;
	private final JSlider glowSizeSlider;
	private final SliderWithDisplayedValue glowSizeSliderWithDisplay;
	private final JSlider outlineWidthSlider;
	private final SliderWithDisplayedValue outlineWidthSliderWithDisplay;
	private final JPanel shapeFillColorDisplay;
	private final JPanel shapeLineColorDisplay;
	private final JSlider shapeLineWidthSlider;
	private final SliderWithDisplayedValue shapeLineWidthSliderWithDisplay;
	private final JSlider shapeJitterSlider;
	private final SliderWithDisplayedValue shapeJitterSliderWithDisplay;
	private final JSlider fadeSlider;
	private final SliderWithDisplayedValue fadeSliderWithDisplay;
	private final RowHider allRows;
	private final RowHider backgroundColorRow;
	private final RowHider glowSizeRow;
	private final RowHider outlineWidthRow;
	private final RowHider shapeRows;
	private final RowHider fadeRow;
	/**
	 * True while the controls are being set from styles, when their listeners must not report edits.
	 */
	private boolean isLoading;
	private final Map<TextStylePart, List<JLabel>> labelsByPart = new EnumMap<>(TextStylePart.class);
	/** The help each part's label shows when it isn't marked. */
	private final Map<JLabel, String> helpByLabel = new HashMap<>();
	private static final java.awt.Color ownPartFallbackColor = new java.awt.Color(26, 113, 228);

	/**
	 * Adds the controls to the organizer.
	 *
	 * @param onEdit
	 *            Called with each change the user makes.
	 * @param familiesUsedByThisMap
	 *            The font families the map draws with, which the font picker lists first.
	 * @param textThatMustBeDrawable
	 *            The text the chosen font has to be able to draw.
	 */
	TextStyleControls(GridBagOrganizer organizer, Consumer<StyleEdit> onEdit, Supplier<List<String>> familiesUsedByThisMap, Supplier<String> textThatMustBeDrawable)
	{
		this.onEdit = onEdit;
		final int maxFontPreviewHeight = 90;
		fontChooser = new FontChooser(Translation.get("textTool.font.label"), 30, maxFontPreviewHeight, 40, this::handleFontChosen);
		fontChooser.setFamiliesUsedByThisMap(familiesUsedByThisMap);
		fontChooser.setTextThatMustBeDrawable(textThatMustBeDrawable);
		allRows = fontChooser.addToOrganizer(organizer);
		addPartLabel(TextStylePart.Font, allRows);

		sizeComboBox = new JComboBoxFixed<>();
		for (Integer size : fontSizes)
		{
			sizeComboBox.addItem(size);
		}
		sizeComboBox.setEditable(true);
		sizeComboBox.addActionListener(e ->
		{
			// Typing a size fires both an edit event and a selection change. Only the selection change is handled, so the edit happens once.
			if ("comboBoxEdited".equals(e.getActionCommand()))
			{
				return;
			}
			Integer size = parseSize(sizeComboBox.getSelectedItem());
			if (size != null)
			{
				reportEdit(TextStylePart.Size, style -> style.font = style.font.deriveFont(style.font.getStyle(), size));
			}
		});
		RowHider sizeRow = organizer.addLabelAndComponent(Translation.get("textTool.size.label"), Translation.get("textTool.size.help"), sizeComboBox);
		allRows.add(sizeRow);
		addPartLabel(TextStylePart.Size, sizeRow);

		colorDisplay = SwingHelper.createColorPickerPreviewPanel();
		RowHider colorRow = addColorRow(organizer, Translation.get("textTool.color.label"), Translation.get("textTool.color.help"), colorDisplay,
				Translation.get("textTool.color.title"), TextStylePart.Color, color -> style -> style.color = color);
		allRows.add(colorRow);
		addPartLabel(TextStylePart.Color, colorRow);

		effectComboBox = new JComboBoxFixed<>();
		for (TextBackgroundEffect effect : TextBackgroundEffect.values())
		{
			effectComboBox.addItem(effect);
		}
		effectComboBox.addActionListener(e ->
		{
			TextBackgroundEffect effect = (TextBackgroundEffect) effectComboBox.getSelectedItem();
			if (effect != null)
			{
				reportEdit(TextStylePart.Background, style -> style.background.effect = effect);
			}
		});
		RowHider effectRow = organizer.addLabelAndComponent(Translation.get("textTool.background.label"), Translation.get("textTool.background.help"), effectComboBox);
		allRows.add(effectRow);
		addPartLabel(TextStylePart.Background, effectRow);

		backgroundColorDisplay = SwingHelper.createColorPickerPreviewPanel();
		backgroundColorRow = addColorRow(organizer, Translation.get("textTool.backgroundColor.label"), Translation.get("textTool.backgroundColor.help"),
				backgroundColorDisplay, Translation.get("textTool.backgroundColor.title"), TextStylePart.Background, color -> style -> style.background.color = color);
		addPartLabel(TextStylePart.Background, backgroundColorRow);
		glowSizeSlider = createSlider(1, TextBackground.maxGlowSize);
		glowSizeSliderWithDisplay = new SliderWithDisplayedValue(glowSizeSlider, null, () ->
		{
			int value = glowSizeSlider.getValue();
			reportEdit(TextStylePart.Background, style -> style.background.glowSize = value);
		});
		glowSizeRow = glowSizeSliderWithDisplay.addToOrganizer(organizer, Translation.get("textTool.glowSize.label"), Translation.get("textTool.glowSize.help"));
		addPartLabel(TextStylePart.Background, glowSizeRow);
		outlineWidthSlider = createSlider(1, TextBackground.maxOutlineWidth);
		outlineWidthSliderWithDisplay = new SliderWithDisplayedValue(outlineWidthSlider, null, () ->
		{
			int value = outlineWidthSlider.getValue();
			reportEdit(TextStylePart.Background, style -> style.background.outlineWidth = value);
		});
		outlineWidthRow = outlineWidthSliderWithDisplay.addToOrganizer(organizer, Translation.get("textTool.outlineWidth.label"),
				Translation.get("textTool.outlineWidth.help"));
		addPartLabel(TextStylePart.Background, outlineWidthRow);

		shapeFillColorDisplay = SwingHelper.createColorPickerPreviewPanel();
		shapeRows = addColorRow(organizer, Translation.get("textTool.fillColor.label"), Translation.get("textTool.fillColor.help"), shapeFillColorDisplay,
				Translation.get("textTool.fillColor.title"), TextStylePart.Background, color -> style -> style.background.shapeFillColor = color);
		addPartLabel(TextStylePart.Background, shapeRows);
		shapeLineColorDisplay = SwingHelper.createColorPickerPreviewPanel();
		RowHider lineColorRow = addColorRow(organizer, Translation.get("textTool.lineColor.label"), Translation.get("textTool.lineColor.help"), shapeLineColorDisplay,
				Translation.get("textTool.lineColor.title"), TextStylePart.Background, color -> style -> style.background.shapeLineColor = color);
		shapeRows.add(lineColorRow);
		addPartLabel(TextStylePart.Background, lineColorRow);
		shapeLineWidthSlider = createSlider(0, TextBackground.maxShapeLineWidth);
		shapeLineWidthSliderWithDisplay = new SliderWithDisplayedValue(shapeLineWidthSlider, null, () ->
		{
			int value = shapeLineWidthSlider.getValue();
			reportEdit(TextStylePart.Background, style -> style.background.shapeLineWidth = value);
		});
		RowHider lineWidthRow = shapeLineWidthSliderWithDisplay.addToOrganizer(organizer, Translation.get("textTool.lineWidth.label"), Translation.get("textTool.lineWidth.help"));
		shapeRows.add(lineWidthRow);
		addPartLabel(TextStylePart.Background, lineWidthRow);
		shapeJitterSlider = createSlider(0, TextBackground.maxShapeJitter);
		shapeJitterSliderWithDisplay = new SliderWithDisplayedValue(shapeJitterSlider, null, () ->
		{
			int value = shapeJitterSlider.getValue();
			reportEdit(TextStylePart.Background, style -> style.background.shapeJitter = value);
		});
		RowHider jitterRow = shapeJitterSliderWithDisplay.addToOrganizer(organizer, Translation.get("textTool.jitter.label"), Translation.get("textTool.jitter.help"));
		shapeRows.add(jitterRow);
		addPartLabel(TextStylePart.Background, jitterRow);

		fadeSlider = createSlider(0, fadeSliderDivider);
		fadeSliderWithDisplay = new SliderWithDisplayedValue(fadeSlider, (value) -> String.format("%.1f", value / ((double) fadeSliderDivider)), () ->
		{
			double value = fadeSlider.getValue() / (double) fadeSliderDivider;
			reportEdit(TextStylePart.Background, style -> style.background.fadeBehind = value);
		}, 34);
		JButton clearFadeButton = new JButton("x");
		clearFadeButton.setToolTipText(Translation.get("textTool.clearFadeBehind.tooltip"));
		SwingHelper.addListener(clearFadeButton, () -> fadeSlider.setValue(0));
		fadeRow = fadeSliderWithDisplay.addToOrganizer(organizer, Translation.get("textTool.fadeBehind.label"), Translation.get("textTool.fadeBehind.help"),
				clearFadeButton, 0, 0);
		addPartLabel(TextStylePart.Background, fadeRow);

		allRows.add(backgroundColorRow);
		allRows.add(glowSizeRow);
		allRows.add(outlineWidthRow);
		allRows.add(shapeRows);
		allRows.add(fadeRow);
	}

	private void handleFontChosen()
	{
		Font chosen = AwtBridge.fromAwtFont(fontChooser.getFont());
		reportEdit(TextStylePart.Font, style -> style.font = style.withFamilyAndStyleOf(chosen));
	}

	private static JSlider createSlider(int min, int max)
	{
		JSlider slider = new JSlider();
		slider.setPaintLabels(false);
		slider.setMinimum(min);
		slider.setMaximum(max);
		return slider;
	}

	private RowHider addColorRow(GridBagOrganizer organizer, String label, String help, JPanel display, String pickerTitle, TextStylePart part,
			Function<Color, Consumer<TextStyle>> createChange)
	{
		Runnable applyDisplayedColor = () ->
		{
			SwingHelper.clearMixedColorsInColorPickerPreview(display);
			reportEdit(part, createChange.apply(AwtBridge.fromAwtColor(display.getBackground())));
		};
		JButton chooseButton = new JButton(Translation.get("common.choose"));
		chooseButton.addActionListener(e -> SwingHelper.showColorPicker(organizer.panel, display, pickerTitle, applyDisplayedColor));
		SwingHelper.addColorCopyAndPasteMenu(display, display::getBackground, color ->
		{
			display.setBackground(color);
			applyDisplayedColor.run();
		});
		return organizer.addLabelAndComponentsHorizontal(label, help, Arrays.asList(display, chooseButton), SwingHelper.colorPickerLeftPadding);
	}

	private static Integer parseSize(Object item)
	{
		if (item instanceof Integer size)
		{
			return size > 0 ? size : null;
		}
		if (item instanceof String text)
		{
			try
			{
				int size = Integer.parseInt(text.trim());
				return size > 0 ? size : null;
			}
			catch (NumberFormatException e)
			{
				return null;
			}
		}
		return null;
	}

	private void reportEdit(TextStylePart part, Consumer<TextStyle> change)
	{
		if (isLoading)
		{
			return;
		}
		onEdit.accept(new StyleEdit(part, change));
	}

	private void addPartLabel(TextStylePart part, RowHider row)
	{
		JLabel label = row.getLabel();
		if (label != null)
		{
			labelsByPart.computeIfAbsent(part, key -> new ArrayList<>()).add(label);
			helpByLabel.put(label, label.getToolTipText());
		}
	}

	/**
	 * Marks the labels of the given parts, to show that the text shown has its own values for them rather than following its type's style.
	 *
	 * @param tooltips
	 *            The tooltip of each marked part's labels.
	 */
	void showOwnParts(Set<TextStylePart> parts, Map<TextStylePart, String> tooltips)
	{
		for (Map.Entry<TextStylePart, List<JLabel>> entry : labelsByPart.entrySet())
		{
			boolean isOwn = parts.contains(entry.getKey());
			for (JLabel label : entry.getValue())
			{
				label.setForeground(isOwn ? getOwnPartColor() : null);
				label.setToolTipText(isOwn ? tooltips.get(entry.getKey()) : helpByLabel.get(label));
			}
		}
	}

	private static java.awt.Color getOwnPartColor()
	{
		java.awt.Color accent = UIManager.getColor("Component.accentColor");
		return accent != null ? accent : ownPartFallbackColor;
	}

	RowHider getRows()
	{
		return allRows;
	}

	/**
	 * Shows the given styles. Where they differ, a control shows that rather than one of their values.
	 */
	void showStyles(List<TextStyle> styles)
	{
		if (styles.isEmpty())
		{
			return;
		}

		isLoading = true;
		try
		{
			TextStyle first = styles.get(0);

			fontChooser.setFont(AwtBridge.toAwtFont(first.font));
			if (!allSame(styles, style -> style.font.getName().toLowerCase() + "/" + style.font.getStyle()))
			{
				fontChooser.setMixed();
			}

			if (allSame(styles, style -> Math.round(style.font.getSize())))
			{
				sizeComboBox.setSelectedItem(Math.round(first.font.getSize()));
			}
			else
			{
				sizeComboBox.setSelectedItem(null);
			}

			showColor(colorDisplay, styles, style -> style.color);

			boolean isEffectSame = allSame(styles, style -> style.background.effect);
			effectComboBox.setSelectedItem(isEffectSame ? first.background.effect : null);

			showColor(backgroundColorDisplay, styles, style -> style.background.color);
			showSlider(glowSizeSliderWithDisplay, styles, style -> style.background.glowSize);
			showSlider(outlineWidthSliderWithDisplay, styles, style -> style.background.outlineWidth);
			showColor(shapeFillColorDisplay, styles, style -> style.background.shapeFillColor);
			showColor(shapeLineColorDisplay, styles, style -> style.background.shapeLineColor);
			showSlider(shapeLineWidthSliderWithDisplay, styles, style -> style.background.shapeLineWidth);
			showSlider(shapeJitterSliderWithDisplay, styles, style -> style.background.shapeJitter);
			showSlider(fadeSliderWithDisplay, styles, style -> (int) Math.round(style.background.fadeBehind * fadeSliderDivider));
		}
		finally
		{
			isLoading = false;
		}
		updateBackgroundRows();
	}

	/**
	 * Shows the background settings that apply to the effect shown, and hides the rest.
	 */
	private void updateBackgroundRows()
	{
		if (!allRows.isVisible())
		{
			return;
		}
		TextBackgroundEffect effect = (TextBackgroundEffect) effectComboBox.getSelectedItem();
		backgroundColorRow.setVisible(effect != null && (effect.isHalo() || effect == TextBackgroundEffect.BoldBackground));
		glowSizeRow.setVisible(effect == TextBackgroundEffect.Glow);
		outlineWidthRow.setVisible(effect == TextBackgroundEffect.Outline);
		shapeRows.setVisible(effect != null && effect.isShape());
		// With mixed effects, fade is shown, since it applies to some of them.
		fadeRow.setVisible(effect == null || effect.allowsFadeBehind());
	}

	void setVisible(boolean isVisible)
	{
		allRows.setVisible(isVisible);
		if (isVisible)
		{
			updateBackgroundRows();
		}
	}

	private static <T> boolean allSame(List<TextStyle> styles, Function<TextStyle, T> getValue)
	{
		T first = getValue.apply(styles.get(0));
		for (TextStyle style : styles)
		{
			if (!Objects.equals(first, getValue.apply(style)))
			{
				return false;
			}
		}
		return true;
	}

	private static void showColor(JPanel display, List<TextStyle> styles, Function<TextStyle, Color> getColor)
	{
		List<java.awt.Color> colors = new ArrayList<>(styles.size());
		for (TextStyle style : styles)
		{
			colors.add(AwtBridge.toAwtColor(getColor.apply(style)));
		}
		SwingHelper.showColorsInColorPickerPreview(display, colors);
	}

	private static void showSlider(SliderWithDisplayedValue sliderWithDisplay, List<TextStyle> styles, Function<TextStyle, Integer> getValue)
	{
		List<Integer> values = new ArrayList<>(styles.size());
		for (TextStyle style : styles)
		{
			values.add(getValue.apply(style));
		}
		sliderWithDisplay.showValues(values);
	}
}
