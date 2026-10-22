package nortantis.swing;

import nortantis.DebugFlags;
import nortantis.MapSettings;
import nortantis.editor.UserPreferences;
import nortantis.swing.translation.Translation;
import nortantis.util.Assets;
import nortantis.util.Logger;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * What changed in each release, shown the first time a newer version of Nortantis runs.
 */
public class ReleaseNotes
{
	/**
	 * The prefix of the translation keys holding each release's notes, followed by the release's version. Releases without notes have no key, or
	 * an empty one.
	 */
	// TODO Add the notes for version 3.25 to every language's messages file.
	private static final String notesKeyPrefix = "releaseNotes.";

	/**
	 * If this is the first run of a newer version than the one that ran before, tells the user Nortantis was updated, showing the notes of
	 * every release after that version up to this one when any of them have notes. Records this version as the one that last ran. Does
	 * nothing when running from source unless {@link DebugFlags#showReleaseNotesWhenRunningFromSource()} is on.
	 */
	public static void showIfFirstRunOfNewVersion(Window owner)
	{
		if (!Assets.isRunningFromJar() && !DebugFlags.showReleaseNotesWhenRunningFromSource())
		{
			return;
		}

		String lastVersionRun = UserPreferences.getInstance().lastVersionRun;
		UserPreferences.getInstance().lastVersionRun = MapSettings.currentVersion;
		// No preferences file means Nortantis hasn't run on this machine before, so it is a new install rather than an update.
		if (lastVersionRun == null)
		{
			return;
		}

		List<String> newVersions;
		try
		{
			if (!lastVersionRun.isEmpty() && !MapSettings.isVersionGreaterThan(MapSettings.currentVersion, lastVersionRun))
			{
				return;
			}
			newVersions = getVersionsWithNotesAfter(lastVersionRun);
		}
		catch (NumberFormatException e)
		{
			Logger.printError("Unable to compare the version that last ran, '" + lastVersionRun + "', with the versions that have release notes.", e);
			return;
		}

		if (newVersions.isEmpty())
		{
			SwingHelper.showMessageDialog(owner, Translation.get("mainWindow.updatedMessage", MapSettings.currentVersion), Translation.get("mainWindow.updatedTitle"),
					JOptionPane.INFORMATION_MESSAGE);
		}
		else
		{
			showDialog(owner, newVersions);
		}
	}

	/**
	 * The versions of the releases with notes after the given version up to the current one, newest first.
	 *
	 * @param version
	 *            Empty for a version from before the last version run was recorded, which is older than every release with notes.
	 */
	private static List<String> getVersionsWithNotesAfter(String version)
	{
		List<String> result = new ArrayList<>();
		for (String key : Translation.getKeysStartingWith(notesKeyPrefix))
		{
			String release = key.substring(notesKeyPrefix.length());
			if (!Translation.get(key).isBlank() && (version.isEmpty() || MapSettings.isVersionGreaterThan(release, version)) && !MapSettings.isVersionGreaterThan(release, MapSettings.currentVersion))
			{
				result.add(release);
			}
		}
		result.sort((first, second) -> first.equals(second) ? 0 : MapSettings.isVersionGreaterThan(first, second) ? -1 : 1);
		return result;
	}

	private static void showDialog(Window owner, List<String> versionsToShow)
	{
		JDialog dialog = new JDialog(owner, Translation.get("mainWindow.releaseNotesTitle"), Dialog.ModalityType.APPLICATION_MODAL);

		JPanel content = new JPanel(new BorderLayout(0, 10));
		content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		content.add(new JLabel(Translation.get("mainWindow.updatedMessage", MapSettings.currentVersion)), BorderLayout.NORTH);

		StringBuilder html = new StringBuilder("<html><body>");
		for (String version : versionsToShow)
		{
			html.append("<h2>").append(Translation.get("about.version", version)).append("</h2>").append(Translation.get(notesKeyPrefix + version));
		}
		html.append("</body></html>");

		JEditorPane notesPane = new JEditorPane("text/html", html.toString());
		notesPane.setEditable(false);
		// Uses the look and feel's font and colors rather than the HTML defaults.
		notesPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
		notesPane.setFont(UIManager.getFont("Label.font"));
		Color linkColor = SwingHelper.linkColor;
		((HTMLDocument) notesPane.getDocument()).getStyleSheet()
				.addRule("a { color: rgb(" + linkColor.getRed() + ", " + linkColor.getGreen() + ", " + linkColor.getBlue() + "); }");
		notesPane.addHyperlinkListener(e ->
		{
			if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED)
			{
				SwingHelper.openWebPage(e.getURL() != null ? e.getURL().toString() : e.getDescription());
			}
		});
		notesPane.setCaretPosition(0);

		JScrollPane scrollPane = new JScrollPane(notesPane);
		scrollPane.getVerticalScrollBar().setUnitIncrement(SwingHelper.sidePanelScrollSpeed);
		scrollPane.setPreferredSize(new Dimension(600, 450));
		content.add(scrollPane, BorderLayout.CENTER);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		JButton okButton = new JButton(Translation.get("common.ok"));
		okButton.addActionListener(e -> dialog.dispose());
		buttons.add(okButton);
		content.add(buttons, BorderLayout.SOUTH);

		dialog.setContentPane(content);
		dialog.getRootPane().setDefaultButton(okButton);
		dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(), KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
				JComponent.WHEN_IN_FOCUSED_WINDOW);
		dialog.pack();
		dialog.setLocationRelativeTo(owner);
		dialog.setVisible(true);
	}
}
