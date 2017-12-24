package nortantis.editor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultFocusManager;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingWorker;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.SliderUI;

import nortantis.MapCreator;
import nortantis.MapParts;
import nortantis.MapSettings;
import nortantis.MapText;
import nortantis.RunSwing;
import nortantis.TextType;
import util.ImageHelper;
import util.JComboBoxFixed;
import util.Tuple2;
import javax.swing.JToolBar;
import javax.swing.SpringLayout;
import java.awt.Insets;
import java.awt.Component;
import java.awt.Dimension;

import javax.swing.Box;

@SuppressWarnings("serial")
public class EditorDialog extends JDialog
{
	JScrollPane scrollPane;
	EditorTool currentTool;
	List<EditorTool> tools;
	public static final int borderWidthBetweenComponents = 4;
	public static final int toolsPanelMaxWidth = 300;
	
	/**
	 * Creates a dialog for editing text.
	 * @param settings Settings for the map. The user's edits will be stored in settigns.edits.
	 * Other fields in settings may be modified in the editing process.
	 * @throws IOException 
	 */
	public EditorDialog(final MapSettings settings, final RunSwing runSwing)
	{
		final EditorDialog thisDialog = this;
		setBounds(100, 100, 935, 584);
		
		runSwing.clearEditsMenuItem.setEnabled(true);

		getContentPane().setLayout(new BorderLayout());

		// Setup tools
		tools = Arrays.asList(
				new TextTool(this, settings),
				new OceanTool());
		currentTool = tools.get(0);
		scrollPane = new JScrollPane(currentTool.getDisplayPanel());
		// Speed up the scroll speed.
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		getContentPane().add(scrollPane);
	
		JPanel toolsPanel = new JPanel();
		toolsPanel.setMaximumSize(new Dimension(toolsPanelMaxWidth, getContentPane().getHeight()));
		toolsPanel.setLayout(new BoxLayout(toolsPanel, BoxLayout.Y_AXIS));
		getContentPane().add(toolsPanel, BorderLayout.EAST);

		JPanel toolSelectPanel = new JPanel(new FlowLayout());
		toolSelectPanel.setMaximumSize(new Dimension(toolsPanelMaxWidth, 20));
		toolSelectPanel.setBorder(BorderFactory.createTitledBorder(new EtchedBorder(EtchedBorder.LOWERED), "Tools"));
		toolsPanel.add(toolSelectPanel);
		for (EditorTool tool : tools)
		{
			toolSelectPanel.add(new JToggleButton(tool.getToolbarName()));
		}
		
		// Setup tool options panel
		JPanel toolsOptionsPanel = new JPanel();
		toolsOptionsPanel.add(currentTool.getToolOptionsPanel());
		toolsPanel.add(toolsOptionsPanel);
		toolsOptionsPanel.setBorder(BorderFactory.createTitledBorder(new EtchedBorder(EtchedBorder.LOWERED), "Tool Options"));
				
		// Setup bottom panel
		JPanel bottomPanel = new JPanel();
		bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.X_AXIS));
		bottomPanel.setBorder(BorderFactory.createEmptyBorder(borderWidthBetweenComponents, borderWidthBetweenComponents,
				borderWidthBetweenComponents, borderWidthBetweenComponents));
		
		JLabel lblZoom = new JLabel("Zoom:");
		bottomPanel.add(lblZoom);
		
		final JComboBox<String> zoomComboBox = new JComboBoxFixed<>();
		zoomComboBox.addItem("25%");
		zoomComboBox.addItem("50%");
		zoomComboBox.addItem("75%");
		zoomComboBox.addItem("100%");
		zoomComboBox.setSelectedItem("50%");
		bottomPanel.add(zoomComboBox);
		zoomComboBox.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				double zoom = parseZoom((String)zoomComboBox.getSelectedItem());
				currentTool.handleZoomChange(zoom);
			}
		});	
		
		bottomPanel.add(Box.createHorizontalGlue());

		JButton doneButton = new JButton("Done");
		doneButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e){
				thisDialog.dispatchEvent(new WindowEvent(
	                    thisDialog, WindowEvent.WINDOW_CLOSING));
			}
		});
		
		bottomPanel.add(doneButton);
		
		toolsPanel.add(bottomPanel);

		
		// Using KeyEventDispatcher instead of KeyListener makes the keys work when any component is focused.
		KeyEventDispatcher myKeyEventDispatcher = new DefaultFocusManager()
		{
			private boolean isSaving;

			public boolean dispatchKeyEvent(KeyEvent e)
			{
				if ((e.getKeyCode() == KeyEvent.VK_S) && ((e.getModifiers() & KeyEvent.CTRL_MASK) != 0))
				{
					// Prevent multiple "save as" popups.
					if (isSaving)
					{
						return false;
					}
					isSaving = true;
					
					try
					{
						// Save
						runSwing.saveSettings(scrollPane);
					}
					finally
					{
						isSaving = false;
					}
				}				
				return false;
			}
		};
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(myKeyEventDispatcher);
		
		addWindowListener(new WindowAdapter()
		{
			public void windowClosing(WindowEvent e)
			{
				KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(myKeyEventDispatcher);
			}
		});
		
		currentTool.handleZoomChange(parseZoom((String)zoomComboBox.getSelectedItem()));
	}
	
	private double parseZoom(String zoomStr)
	{
		double zoomPercent = Double.parseDouble(zoomStr.substring(0, zoomStr.length() - 1));
		return zoomPercent / 100.0;
	}
		

}
