package nortantis.swing;

import nortantis.TextStylePart;
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
 * Asks which parts of a text style to copy to the styles of which types of text.
 */
class CopyTextStyleDialog extends JDialog
{
	/**
	 * What the user chose.
	 *
	 * @param resetsOwnStyles
	 *            Whether text of the chosen types that has its own values for the chosen parts goes back to following its type's style for
	 *            them.
	 */
	record Choice(Set<TextStylePart> parts, Set<TextType> types, boolean resetsOwnStyles)
	{
	}

	private final Map<TextStylePart, JCheckBox> partCheckboxes = new EnumMap<>(TextStylePart.class);
	private final Map<TextType, JCheckBox> typeCheckboxes = new EnumMap<>(TextType.class);

	/**
	 * @param initialType
	 *            The type of text checked when the dialog opens.
	 */
	CopyTextStyleDialog(Window owner, TextType initialType, Consumer<Choice> onCopy)
	{
		super(owner, Translation.get("copyTextStyle.title"), ModalityType.APPLICATION_MODAL);

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		content.add(createLeftAlignedLabel(Translation.get("copyTextStyle.whatToCopy")));
		JPanel partsPanel = new JPanel(new GridLayout(0, 2));
		partsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (TextStylePart part : TextStylePart.values())
		{
			JCheckBox checkbox = new JCheckBox(Translation.get("textStylePart." + part.name()));
			checkbox.setSelected(part == TextStylePart.Font || part == TextStylePart.Color);
			partCheckboxes.put(part, checkbox);
			partsPanel.add(checkbox);
		}
		content.add(partsPanel);
		content.add(Box.createVerticalStrut(10));

		content.add(createLeftAlignedLabel(Translation.get("copyTextStyle.copyTo")));
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
		JCheckBox resetCheckbox = new JCheckBox(Translation.get("copyTextStyle.alsoReset"));
		resetCheckbox.setToolTipText(Translation.get("copyTextStyle.alsoReset.tooltip"));
		resetCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(resetCheckbox);

		content.add(Box.createVerticalStrut(10));
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
		buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
		JButton copyButton = new JButton(Translation.get("copyTextStyle.copy"));
		copyButton.addActionListener(e ->
		{
			Set<TextStylePart> parts = EnumSet.noneOf(TextStylePart.class);
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
				onCopy.accept(new Choice(parts, types, resetCheckbox.isSelected()));
			}
		});
		JButton cancelButton = new JButton(Translation.get("common.cancel"));
		cancelButton.addActionListener(e -> dispose());
		buttons.add(copyButton);
		buttons.add(cancelButton);
		content.add(buttons);

		setContentPane(content);
		getRootPane().setDefaultButton(copyButton);
		getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
		pack();
		setLocationRelativeTo(owner);
	}

	static JLabel createLeftAlignedLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}
}
