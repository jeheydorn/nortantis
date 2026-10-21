package nortantis.swing;

import nortantis.IconType;
import nortantis.swing.translation.Translation;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Asks which parts of an icon color to copy to the colors of which types of icons.
 */
class CopyIconColorDialog extends JDialog
{
	enum Part
	{
		/** The hue, saturation, brightness, and transparency sliders, and Maximize opacity. */
		FilterColor,
		/** Fill with color, the fill color, and whether the fill shows the background texture. */
		FillColor
	}

	/**
	 * What the user chose.
	 *
	 * @param recolorsIconsOnMap
	 *            Whether icons of the chosen types that are already on the map take the copied colors, rather than only icons added later.
	 */
	record Choice(Set<Part> parts, Set<IconType> types, boolean recolorsIconsOnMap)
	{
	}

	private final Map<Part, JCheckBox> partCheckboxes = new EnumMap<>(Part.class);
	private final Map<IconType, JCheckBox> typeCheckboxes = new EnumMap<>(IconType.class);

	/**
	 * @param initialTypes
	 *            The types of icons checked when the dialog opens.
	 */
	CopyIconColorDialog(Window owner, Set<IconType> initialTypes, Consumer<Choice> onCopy)
	{
		super(owner, Translation.get("copyIconColor.title"), ModalityType.APPLICATION_MODAL);

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		content.add(CopyTextStyleDialog.createLeftAlignedLabel(Translation.get("copyIconColor.whatToCopy")));
		for (Part part : Part.values())
		{
			JCheckBox checkbox = new JCheckBox(Translation.get("copyIconColor.part." + part.name()));
			checkbox.setToolTipText(Translation.get("copyIconColor.part." + part.name() + ".tooltip"));
			checkbox.setSelected(true);
			partCheckboxes.put(part, checkbox);
		}
		JPanel partsPanel = CopyTextStyleDialog.createRowMajorGridTabbingDownColumns(new ArrayList<>(partCheckboxes.values()), 2);
		partsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(partsPanel);
		content.add(Box.createVerticalStrut(10));

		content.add(CopyTextStyleDialog.createLeftAlignedLabel(Translation.get("copyIconColor.copyTo")));
		for (IconType type : IconType.values())
		{
			JCheckBox checkbox = new JCheckBox(type.getNameForGUI());
			checkbox.setSelected(initialTypes.contains(type));
			typeCheckboxes.put(type, checkbox);
		}
		JPanel typesPanel = CopyTextStyleDialog.createColumnMajorGrid(new ArrayList<>(typeCheckboxes.values()), 2);
		typesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(typesPanel);

		content.add(Box.createVerticalStrut(10));
		JCheckBox recolorCheckbox = new JCheckBox(Translation.get("copyIconColor.alsoRecolor"));
		recolorCheckbox.setToolTipText(Translation.get("copyIconColor.alsoRecolor.tooltip"));
		recolorCheckbox.setSelected(true);
		recolorCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(recolorCheckbox);

		content.add(Box.createVerticalStrut(10));
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
		buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
		JButton copyButton = new JButton(Translation.get("copyIconColor.copy"));
		copyButton.addActionListener(e ->
		{
			Set<Part> parts = EnumSet.noneOf(Part.class);
			partCheckboxes.forEach((part, checkbox) ->
			{
				if (checkbox.isSelected())
				{
					parts.add(part);
				}
			});
			Set<IconType> types = EnumSet.noneOf(IconType.class);
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
				onCopy.accept(new Choice(parts, types, recolorCheckbox.isSelected()));
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
}
