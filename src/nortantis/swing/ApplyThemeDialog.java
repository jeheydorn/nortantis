package nortantis.swing;

import nortantis.ThemeCatalog;
import nortantis.swing.translation.Translation;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * Lists every theme the user has, so they can choose one to apply to the open map.
 */
class ApplyThemeDialog extends JDialog
{
	ApplyThemeDialog(Window owner, String customImagesFolder, Consumer<ThemeCatalog.Entry> onApply)
	{
		super(owner, Translation.get("applyTheme.title"), ModalityType.APPLICATION_MODAL);

		DefaultListModel<ThemeCatalog.Entry> model = new DefaultListModel<>();
		for (ThemeCatalog.Entry entry : ThemeCatalog.listAllThemes(customImagesFolder))
		{
			model.addElement(entry);
		}
		JList<ThemeCatalog.Entry> list = new JList<>(model);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
			{
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				ThemeCatalog.Entry entry = (ThemeCatalog.Entry) value;
				setText(entry.source == ThemeCatalog.Source.ArtPack ? Translation.get("newSettingsDialog.theme.inArtPack", entry.name, entry.artPack) : entry.name);
				return this;
			}
		});
		if (!model.isEmpty())
		{
			list.setSelectedIndex(0);
		}

		JPanel content = new JPanel(new BorderLayout(0, 8));
		content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		JLabel explanation = new JLabel("<html>" + Translation.get("applyTheme.explanation") + "</html>");
		content.add(explanation, BorderLayout.NORTH);
		JScrollPane scrollPane = new JScrollPane(list);
		scrollPane.getVerticalScrollBar().setUnitIncrement(SwingHelper.sidePanelScrollSpeed);
		scrollPane.setPreferredSize(new Dimension(380, 260));
		content.add(scrollPane, BorderLayout.CENTER);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
		JButton applyButton = new JButton(Translation.get("applyTheme.apply"));
		applyButton.addActionListener(e ->
		{
			ThemeCatalog.Entry entry = list.getSelectedValue();
			dispose();
			if (entry != null)
			{
				onApply.accept(entry);
			}
		});
		JButton cancelButton = new JButton(Translation.get("common.cancel"));
		cancelButton.addActionListener(e -> dispose());
		buttons.add(applyButton);
		buttons.add(cancelButton);
		content.add(buttons, BorderLayout.SOUTH);

		list.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() == 2 && list.getSelectedValue() != null)
				{
					applyButton.doClick();
				}
			}
		});

		setContentPane(content);
		getRootPane().setDefaultButton(applyButton);
		getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
		pack();
		setLocationRelativeTo(owner);
	}
}
