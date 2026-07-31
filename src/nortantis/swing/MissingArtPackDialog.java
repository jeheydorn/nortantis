package nortantis.swing;

import java.awt.Component;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import org.apache.commons.lang3.StringUtils;

import nortantis.MapSettings.MissingArtPackInfo;
import nortantis.swing.translation.Translation;
import nortantis.util.Assets;

/**
 * A modal dialog shown when a map being opened references one or more art packs that are not installed. It tells the user which art packs
 * are missing and what kinds of assets depend on them, then lets the user either substitute an installed art pack or cancel opening the map.
 */
public class MissingArtPackDialog
{
	/**
	 * The user's response to the dialog.
	 */
	public static class Result
	{
		/** True if the user chose to cancel opening the map. */
		public final boolean cancelled;
		/** The installed art pack the user chose to substitute in, or null when {@link #cancelled} is true. */
		public final String chosenArtPack;

		private Result(boolean cancelled, String chosenArtPack)
		{
			this.cancelled = cancelled;
			this.chosenArtPack = chosenArtPack;
		}
	}

	/**
	 * Shows the dialog and blocks until the user responds.
	 *
	 * @param parent
	 *            The dialog's parent component.
	 * @param mapName
	 *            The name of the map being opened (without file extension), shown in the message.
	 * @param info
	 *            Which art packs are missing and what assets they affect.
	 * @param customImagesPath
	 *            The map's custom images folder, used to decide whether the "custom" art pack is offered as an alternative.
	 * @return The user's choice.
	 */
	public static Result show(Component parent, String mapName, MissingArtPackInfo info, String customImagesPath)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		String missingPacksList = String.join(", ", info.missingArtPacks);
		String affectedAssets = buildAffectedAssetsClause(info);
		String message = Translation.get("mainWindow.missingArtPack.message", mapName, missingPacksList, affectedAssets);

		JLabel messageLabel = new JLabel("<html><body style='width: 380px'>" + SwingHelper.escapeHtml(message) + "</body></html>");
		messageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(messageLabel);

		panel.add(Box.createVerticalStrut(15));

		JComboBox<String> artPackComboBox = new JComboBox<>();
		SwingHelper.initializeComboBoxItems(artPackComboBox, Assets.listArtPacks(!StringUtils.isEmpty(customImagesPath)), Assets.installedArtPack, false);

		JPanel artPackRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		artPackRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		artPackRow.add(new JLabel(Translation.get("mainWindow.missingArtPack.artPackLabel")));
		artPackRow.add(Box.createHorizontalStrut(8));
		artPackRow.add(artPackComboBox);
		panel.add(artPackRow);

		String openButtonText = Translation.get("mainWindow.missingArtPack.openButton");
		String cancelButtonText = Translation.get("mainWindow.missingArtPack.cancel");
		Object[] options = new Object[] { openButtonText, cancelButtonText };

		int result = SwingHelper.showOptionDialog(parent, panel, Translation.get("mainWindow.missingArtPack.title"), JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options,
				openButtonText);

		if (result == 0)
		{
			return new Result(false, (String) artPackComboBox.getSelectedItem());
		}

		return new Result(true, null);
	}

	private static String buildAffectedAssetsClause(MissingArtPackInfo info)
	{
		List<String> fragments = new ArrayList<>();
		if (info.affectsBorder)
		{
			fragments.add(Translation.get("mainWindow.missingArtPack.affectsBorder"));
		}
		if (info.affectsBackgroundTexture)
		{
			fragments.add(Translation.get("mainWindow.missingArtPack.affectsBackgroundTexture"));
		}
		if (info.iconCount > 0)
		{
			fragments.add(Translation.get("mainWindow.missingArtPack.affectsIcons", Integer.toString(info.iconCount)));
		}
		return String.join(", ", fragments);
	}
}
