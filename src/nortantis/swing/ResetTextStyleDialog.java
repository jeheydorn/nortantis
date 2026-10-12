package nortantis.swing;

import nortantis.TextStylePart;
import nortantis.swing.translation.Translation;

import javax.swing.*;
import java.awt.*;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Asks which parts of text's own style to reset, so that the text follows its type's style for them again.
 */
class ResetTextStyleDialog extends JDialog
{
	private final Map<TextStylePart, JCheckBox> partCheckboxes = new EnumMap<>(TextStylePart.class);

	/**
	 * @param ownParts
	 *            The parts the text has its own values for. Only these can be reset.
	 * @param onReset
	 *            Called with the parts to reset.
	 */
	ResetTextStyleDialog(Window owner, Set<TextStylePart> ownParts, Consumer<Set<TextStylePart>> onReset)
	{
		super(owner, Translation.get("resetTextStyle.title"), ModalityType.APPLICATION_MODAL);

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		content.add(CopyTextStyleDialog.createLeftAlignedLabel(Translation.get("resetTextStyle.whatToReset")));
		JPanel partsPanel = new JPanel(new GridLayout(0, 2));
		partsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (TextStylePart part : TextStylePart.values())
		{
			JCheckBox checkbox = new JCheckBox(Translation.get("textStylePart." + part.name()));
			boolean isOwn = ownParts.contains(part);
			checkbox.setSelected(isOwn);
			checkbox.setEnabled(isOwn);
			partCheckboxes.put(part, checkbox);
			partsPanel.add(checkbox);
		}
		content.add(partsPanel);

		content.add(Box.createVerticalStrut(10));
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
		buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
		JButton resetButton = new JButton(Translation.get("resetTextStyle.reset"));
		resetButton.addActionListener(e ->
		{
			Set<TextStylePart> parts = EnumSet.noneOf(TextStylePart.class);
			partCheckboxes.forEach((part, checkbox) ->
			{
				if (checkbox.isEnabled() && checkbox.isSelected())
				{
					parts.add(part);
				}
			});
			dispose();
			if (!parts.isEmpty())
			{
				onReset.accept(parts);
			}
		});
		JButton cancelButton = new JButton(Translation.get("common.cancel"));
		cancelButton.addActionListener(e -> dispose());
		buttons.add(resetButton);
		buttons.add(cancelButton);
		content.add(buttons);

		setContentPane(content);
		getRootPane().setDefaultButton(resetButton);
		getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
		pack();
		setLocationRelativeTo(owner);
	}
}
