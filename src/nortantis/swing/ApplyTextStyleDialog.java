package nortantis.swing;

import nortantis.TextStyle;
import nortantis.TextType;
import nortantis.swing.translation.Translation;

import javax.swing.*;
import java.awt.*;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Asks which parts of a text style to apply to every text of the chosen types.
 */
class ApplyTextStyleDialog extends JDialog
{
	/**
	 * The parts of a text style that can be applied.
	 */
	enum Part
	{
		Font, Size, Color, Background, FadeBehind
	}

	/**
	 * What the user chose.
	 *
	 * @param appliesToNewText
	 *            Whether to apply the chosen parts to the styles for new text of the chosen types.
	 * @param appliesToExistingText
	 *            Whether to apply the chosen parts to the text of the chosen types already on the map.
	 */
	record Choice(Set<Part> parts, Set<TextType> types, boolean appliesToNewText, boolean appliesToExistingText)
	{
	}

	private final Map<Part, JCheckBox> partCheckboxes = new EnumMap<>(Part.class);
	private final Map<TextType, JCheckBox> typeCheckboxes = new EnumMap<>(TextType.class);

	/**
	 * @param isStyleForNewText
	 *            Whether the style being applied is a style for new text, which is applied to new text of the chosen types and optionally to
	 *            the text already on the map. Otherwise it is the style of a text on the map, which is applied to the text already on the map
	 *            and optionally to new text.
	 * @param initialType
	 *            The type of text checked when the dialog opens.
	 */
	ApplyTextStyleDialog(Window owner, boolean isStyleForNewText, TextType initialType, Consumer<Choice> onApply)
	{
		super(owner, Translation.get("applyTextStyle.title"), ModalityType.APPLICATION_MODAL);

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		content.add(createLeftAlignedLabel(Translation.get("applyTextStyle.whatToApply")));
		JPanel partsPanel = new JPanel(new GridLayout(0, 2));
		partsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (Part part : Part.values())
		{
			JCheckBox checkbox = new JCheckBox(Translation.get("applyTextStyle.part." + part.name()));
			checkbox.setSelected(part == Part.Font || part == Part.Color);
			partCheckboxes.put(part, checkbox);
			partsPanel.add(checkbox);
		}
		content.add(partsPanel);
		content.add(Box.createVerticalStrut(10));

		content.add(createLeftAlignedLabel(Translation.get(isStyleForNewText ? "applyTextStyle.whatToApplyToForNewText" : "applyTextStyle.whatToApplyTo")));
		JPanel typesPanel = new JPanel(new GridLayout(0, 2));
		typesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (TextType type : TextType.values())
		{
			JCheckBox checkbox = new JCheckBox(type.toString());
			checkbox.setSelected(type == initialType);
			typeCheckboxes.put(type, checkbox);
			typesPanel.add(checkbox);
		}
		content.add(typesPanel);

		content.add(Box.createVerticalStrut(10));
		String alsoKey = isStyleForNewText ? "applyTextStyle.alsoApplyToExistingText" : "applyTextStyle.alsoUseForNewText";
		JCheckBox alsoCheckbox = new JCheckBox(Translation.get(alsoKey));
		alsoCheckbox.setToolTipText(Translation.get(alsoKey + ".tooltip"));
		alsoCheckbox.setSelected(true);
		alsoCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(alsoCheckbox);

		content.add(Box.createVerticalStrut(10));
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
		buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
		JButton applyButton = new JButton(Translation.get("applyTextStyle.apply"));
		applyButton.addActionListener(e ->
		{
			Set<Part> parts = EnumSet.noneOf(Part.class);
			partCheckboxes.forEach((part, checkbox) ->
			{
				if (checkbox.isSelected())
				{
					parts.add(part);
				}
			});
			Set<TextType> types = EnumSet.noneOf(TextType.class);
			typeCheckboxes.forEach((type, checkbox) ->
			{
				if (checkbox.isSelected())
				{
					types.add(type);
				}
			});
			dispose();
			if (!parts.isEmpty() && !types.isEmpty())
			{
				boolean isAlsoChecked = alsoCheckbox.isSelected();
				onApply.accept(new Choice(parts, types, isStyleForNewText || isAlsoChecked, !isStyleForNewText || isAlsoChecked));
			}
		});
		JButton cancelButton = new JButton(Translation.get("common.cancel"));
		cancelButton.addActionListener(e -> dispose());
		buttons.add(applyButton);
		buttons.add(cancelButton);
		content.add(buttons);

		setContentPane(content);
		getRootPane().setDefaultButton(applyButton);
		getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
		pack();
		setLocationRelativeTo(owner);
	}

	private static JLabel createLeftAlignedLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	/**
	 * Copies the chosen parts of a style from source to target. Background copies the effect and the settings of every effect, but not fade
	 * behind.
	 */
	static void applyStyleParts(TextStyle source, TextStyle target, Set<Part> parts)
	{
		if (parts.contains(Part.Font) && parts.contains(Part.Size))
		{
			target.font = source.font;
		}
		else if (parts.contains(Part.Font))
		{
			target.font = target.withFamilyAndStyleOf(source.font);
		}
		else if (parts.contains(Part.Size))
		{
			target.font = target.font.deriveFont(target.font.getStyle(), source.font.getSize());
		}

		if (parts.contains(Part.Color))
		{
			target.color = source.color;
		}

		if (parts.contains(Part.Background))
		{
			target.background.effect = source.background.effect;
			target.background.copyEffectSettingsFrom(source.background);
		}

		if (parts.contains(Part.FadeBehind))
		{
			target.background.fadeBehind = source.background.fadeBehind;
		}
	}
}
