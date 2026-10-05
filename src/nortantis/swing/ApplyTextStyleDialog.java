package nortantis.swing;

import nortantis.TextBackgroundEffect;
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
 * Asks which parts of a text's style (and layout) to apply to every text of the chosen types.
 */
class ApplyTextStyleDialog extends JDialog
{
	/**
	 * The parts of a text that can be applied.
	 */
	enum Part
	{
		Font, Size, Color, Background, BackgroundFade, Curvature, Spacing;

		boolean isStyle()
		{
			return this != Curvature && this != Spacing;
		}
	}

	/**
	 * What the user chose.
	 *
	 * @param alsoUseForNewText
	 *            Whether to also use the chosen parts of the style, but not the layout, for new text of the chosen types.
	 */
	record Choice(Set<Part> parts, Set<TextType> types, boolean alsoUseForNewText)
	{
	}

	private final Map<Part, JCheckBox> partCheckboxes = new EnumMap<>(Part.class);
	private final Map<TextType, JCheckBox> typeCheckboxes = new EnumMap<>(TextType.class);
	private JCheckBox alsoUseForNewTextCheckbox;

	/**
	 * @param includeLayout
	 *            Whether curvature and spacing can be applied, which is only when the source is a piece of text rather than the style for new
	 *            text.
	 * @param offerUsingForNewText
	 *            Whether to show the checkbox for also using the style for new text.
	 * @param initialType
	 *            The type of text checked when the dialog opens.
	 */
	ApplyTextStyleDialog(Window owner, boolean includeLayout, boolean offerUsingForNewText, TextType initialType, Consumer<Choice> onApply)
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
			if (!part.isStyle() && !includeLayout)
			{
				continue;
			}
			JCheckBox checkbox = new JCheckBox(Translation.get("applyTextStyle.part." + part.name()));
			checkbox.setSelected(part == Part.Font || part == Part.Color);
			partCheckboxes.put(part, checkbox);
			partsPanel.add(checkbox);
		}
		content.add(partsPanel);
		content.add(Box.createVerticalStrut(10));

		content.add(createLeftAlignedLabel(Translation.get("applyTextStyle.whatToApplyTo")));
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

		if (offerUsingForNewText)
		{
			content.add(Box.createVerticalStrut(10));
			alsoUseForNewTextCheckbox = new JCheckBox(Translation.get("applyTextStyle.alsoUseForNewText"));
			alsoUseForNewTextCheckbox.setToolTipText(Translation.get("applyTextStyle.alsoUseForNewText.tooltip"));
			alsoUseForNewTextCheckbox.setSelected(true);
			alsoUseForNewTextCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
			content.add(alsoUseForNewTextCheckbox);
		}

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
				onApply.accept(new Choice(parts, types, alsoUseForNewTextCheckbox != null && alsoUseForNewTextCheckbox.isSelected()));
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
	 * Copies the chosen parts of a style from source to target. Background copies the effect and the settings of every effect, but not fade.
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
			for (TextBackgroundEffect family : new TextBackgroundEffect[] { TextBackgroundEffect.Glow, TextBackgroundEffect.BoldBackground, TextBackgroundEffect.Box })
			{
				target.background.copyFamilySettingsFrom(source.background, family);
			}
		}

		if (parts.contains(Part.BackgroundFade))
		{
			target.background.fade = source.background.fade;
		}
	}
}
