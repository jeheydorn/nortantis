package nortantis.editor;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultFocusManager;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

import hoten.voronoi.Center;
import hoten.voronoi.Edge;
import hoten.voronoi.VoronoiGraph;
import nortantis.ImageCache;
import nortantis.MapParts;
import nortantis.MapSettings;
import nortantis.RunSwing;
import nortantis.UserPreferences;
import nortantis.util.JComboBoxFixed;

@SuppressWarnings("serial")
public class EditorFrame extends JFrame
{
	JScrollPane scrollPane;
	EditorTool currentTool;
	List<EditorTool> tools;
	public static final int borderWidthBetweenComponents = 4;
	public static final int toolsPanelWidth = 280;
	private JPanel toolsOptionsPanelContainer;
	private JPanel currentToolOptionsPanel;
	private JComboBox<String> zoomComboBox;
	public MapEditingPanel mapEditingPanel;
	boolean areToolToggleButtonsEnabled = true;
	private JMenuItem undoButton;
	private JMenuItem redoButton;
	private JLabel mapIsDrawingLabel;
	private TitledBorder toolOptionsPanelBorder;
	private JMenuItem clearEntireMapButton;
	private boolean hadEditsAtStartup;
	
	/**
	 * Creates a dialog for editing text.
	 * @param settings Settings for the map. The user's edits will be stored in settings.edits.
	 * 	      Other fields in settings may be modified in the editing process and will not be stored 
	 * 		  after the editor closes.
	 * @throws IOException 
	 */
	public EditorFrame(final MapSettings settings, final RunSwing runSwing)
	{
		hadEditsAtStartup = !settings.edits.isEmpty();
		if (settings.edits.isEmpty())
		{
			// This is in case the user closes the editor before it finishes generating the first time. This
			// assignment causes the edits being created by the generator to be discarded. This is necessary
			// to prevent a case where there are edits but the UI doesn't realize it.
			settings.edits = new MapEdits();
		}
		
		final EditorFrame thisFrame = this;
		setBounds(100, 100, 1122, 701);

		getContentPane().setLayout(new BorderLayout());
		
		mapEditingPanel = new MapEditingPanel(null);
		mapEditingPanel.setLayout(new BorderLayout());
		
		mapEditingPanel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (currentTool.isMapVisible)
				{
					currentTool.handleMouseClickOnMap(e);
				}
			}
			
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (currentTool.isMapVisible)
				{
					currentTool.handleMousePressedOnMap(e);
				}
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (currentTool.isMapVisible)
				{
					currentTool.handleMouseReleasedOnMap(e);
				}
			}
		});
		
		mapEditingPanel.addMouseMotionListener(new MouseMotionListener()
		{
			
			@Override
			public void mouseMoved(MouseEvent e)
			{
				if (currentTool.isMapVisible)
				{
					currentTool.handleMouseMovedOnMap(e);
				}
			}
			
			@Override
			public void mouseDragged(MouseEvent e)
			{
				if (currentTool.isMapVisible)
				{
					currentTool.handleMouseDraggedOnMap(e);
				}
			}
		});
		
		mapEditingPanel.addMouseListener(new MouseListener()
		{
			
			@Override
			public void mouseReleased(MouseEvent e)
			{
			}
			
			@Override
			public void mousePressed(MouseEvent e)
			{
			}
			
			@Override
			public void mouseExited(MouseEvent e)
			{
				if (currentTool.isMapVisible)
				{
					currentTool.handleMouseExitedMap(e);
				}
			}
			
			@Override
			public void mouseEntered(MouseEvent e)
			{
			}
			
			@Override
			public void mouseClicked(MouseEvent e)
			{
			}
		});


		// Setup tools
		tools = Arrays.asList(
				new LandWaterTool(settings, this),
				new IconTool(settings, this),
				new TextTool(settings, this)
				);
		if (UserPreferences.getInstance().lastEditorTool != "")
		{
			for (EditorTool tool : tools)
			{
				if (tool.getToolbarName().equals(UserPreferences.getInstance().lastEditorTool))
				{
					currentTool = tool;
				}
			}
		}
		if (currentTool == null)
		{
			currentTool = tools.get(2);
		}
		scrollPane = new JScrollPane(currentTool.getDisplayPanel());
		// Speed up the scroll speed.
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		getContentPane().add(scrollPane);
		
		setupMenuBar(runSwing);
	
		JPanel toolsPanel = new JPanel();
		toolsPanel.setPreferredSize(new Dimension(toolsPanelWidth, getContentPane().getHeight()));
		toolsPanel.setLayout(new BoxLayout(toolsPanel, BoxLayout.Y_AXIS));
		getContentPane().add(toolsPanel, BorderLayout.EAST);

		JPanel toolSelectPanel = new JPanel(new FlowLayout());
		toolSelectPanel.setMaximumSize(new Dimension(toolsPanelWidth, 20));
		toolSelectPanel.setBorder(BorderFactory.createTitledBorder(new EtchedBorder(EtchedBorder.LOWERED), "Tools"));
		toolsPanel.add(toolSelectPanel);
		for (EditorTool tool : tools)
		{
			JToggleButton toolButton = new JToggleButton();
			try
			{
				toolButton.setIcon(new ImageIcon(tool.getImageIconFilePath()));
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			toolButton.setMaximumSize(new Dimension(50, 50));
			tool.setToggleButton(toolButton);
			toolButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					handleToolSelected(tool);
				}
			});
			toolSelectPanel.add(toolButton);
		}
		
		toolsOptionsPanelContainer = new JPanel();
		currentToolOptionsPanel = currentTool.getToolOptionsPanel();
		toolsOptionsPanelContainer.add(currentToolOptionsPanel);
		toolsPanel.add(toolsOptionsPanelContainer);
		toolOptionsPanelBorder = BorderFactory.createTitledBorder(new EtchedBorder(EtchedBorder.LOWERED), currentTool.getToolbarName() + " Options");
		toolsOptionsPanelContainer.setBorder(toolOptionsPanelBorder);
				
		// Setup bottom panel
		JPanel bottomPanel = new JPanel();
		bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.X_AXIS));
		bottomPanel.setBorder(BorderFactory.createEmptyBorder(borderWidthBetweenComponents, borderWidthBetweenComponents,
				borderWidthBetweenComponents, borderWidthBetweenComponents));
		
		JLabel lblZoom = new JLabel("Zoom:");
		bottomPanel.add(lblZoom);
		
		zoomComboBox = new JComboBoxFixed<>();
		zoomComboBox.addItem("25%");
		zoomComboBox.addItem("50%");
		zoomComboBox.addItem("75%");
		zoomComboBox.addItem("100%");
		zoomComboBox.addItem("150%");
		zoomComboBox.addItem("200%");
		if (UserPreferences.getInstance().zoomLevel != "")
		{
			zoomComboBox.setSelectedItem(UserPreferences.getInstance().zoomLevel);
		}
		else
		{
			zoomComboBox.setSelectedItem("50%");
			UserPreferences.getInstance().zoomLevel = "50%";
		}
		bottomPanel.add(zoomComboBox);
		zoomComboBox.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				UserPreferences.getInstance().zoomLevel = (String)zoomComboBox.getSelectedItem();
				double zoom = parseZoom((String)zoomComboBox.getSelectedItem());
				ImageCache.clear();
				currentTool.handleZoomChange(zoom);
			}
		});
		
		bottomPanel.add(Box.createHorizontalGlue());
		mapIsDrawingLabel = new JLabel("Drawing...");
		mapIsDrawingLabel.setVisible(false);
		bottomPanel.add(mapIsDrawingLabel);
		bottomPanel.add(Box.createHorizontalGlue());

		JButton doneButton = new JButton("Done");
		doneButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e){
				thisFrame.dispatchEvent(new WindowEvent(
	                    thisFrame, WindowEvent.WINDOW_CLOSING));
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
		    @Override
			public void windowClosing(WindowEvent e)
			{
				KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(myKeyEventDispatcher);
				UserPreferences.getInstance().lastEditorTool = currentTool.getToolbarName();
				currentTool.onSwitchingAway();
				
				if (settings.edits.isEmpty())
				{
					// This happens if the user closes the editor before it finishes generating the first time. This
					// assignment causes the edits being created by the generator to be discarded. This is necessary
					// to prevent a case where there are edits but the UI doesn't realize it.
					settings.edits = new MapEdits();
				}
				else
				{
			    	// Undo and redo assign edits, so re-assign the pointer in RunSwing.
			    	runSwing.edits = settings.edits;				
				}
				
				runSwing.frame.setEnabled(true);
				runSwing.updateFieldsWhenEditsChange();	
				if (!hadEditsAtStartup && !settings.edits.isEmpty())
				{
					showMapChangesMessage(runSwing);
				}
			}
		});
		
		handleToolSelected(currentTool);
	}
	
	private void showMapChangesMessage(RunSwing runSwing)
	{
		if (!UserPreferences.getInstance().hideMapChangesWarning)
		{
			JPanel panel = new JPanel();
			panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
			panel.add(new JLabel("<html>Some fields in the generator are now disabled to ensure your map remains"
					+ "<br>compatible with your edits. If a field is disabled for this reason, a message is added"
					+ "<br>to the field's tool tip. If you wish to enable those fields, you can either clear your "
					+ "<br>edits (Editor > Clear Edits), or create a new random map by going to File > New.</html>"));
			panel.add(Box.createVerticalStrut(18));
			JCheckBox checkBox = new JCheckBox("Don't show this message again.");
			panel.add(checkBox);
			JOptionPane.showMessageDialog(runSwing.frame, panel, "", JOptionPane.INFORMATION_MESSAGE);
			UserPreferences.getInstance().hideMapChangesWarning = checkBox.isSelected();
		}
	}
	
	private void setupMenuBar(RunSwing runSwing)
	{
		JMenuBar menuBar = new JMenuBar();
		this.getContentPane().add(menuBar, BorderLayout.NORTH);
		
		JMenu mnFile = new JMenu("File");
		menuBar.add(mnFile);
		
		JMenuItem mntmSave = new JMenuItem("Save");
		mnFile.add(mntmSave);
		mntmSave.setAccelerator(KeyStroke.getKeyStroke(
		        java.awt.event.KeyEvent.VK_S, 
		        java.awt.Event.CTRL_MASK));
		mntmSave.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent arg0)
			{
				runSwing.saveSettings(mntmSave);
			}
		});
		
		JMenuItem mntmSaveAs = new JMenuItem("Save As...");
		mnFile.add(mntmSaveAs);
		mntmSaveAs.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent arg0)
			{
				runSwing.saveSettingsAs(mntmSaveAs);
			}			
		});
		
		JMenu mnEdit = new JMenu("Edit");
		menuBar.add(mnEdit);
		
		undoButton = new JMenuItem("Undo");
		mnEdit.add(undoButton);
		undoButton.setAccelerator(KeyStroke.getKeyStroke(
		        java.awt.event.KeyEvent.VK_Z, 
		        java.awt.Event.CTRL_MASK));
		undoButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (currentTool != null)
				{
					currentTool.undo();
				}
				updateUndoRedoEnabled();
			}
		});


		redoButton = new JMenuItem("Redo");
		mnEdit.add(redoButton);
		redoButton.setAccelerator(KeyStroke.getKeyStroke(
		        java.awt.event.KeyEvent.VK_Z, 
		        ActionEvent.CTRL_MASK | ActionEvent.SHIFT_MASK));
		redoButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (currentTool != null)
				{
					currentTool.redo();
				}
				updateUndoRedoEnabled();
			}
		});
		
		clearEntireMapButton = new JMenuItem("Clear Entire Map");
		mnEdit.add(clearEntireMapButton);
		clearEntireMapButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (currentTool != null)
				{
					currentTool.clearEntireMap();
				}
			}
		});
		clearEntireMapButton.setEnabled(false);
		
		updateUndoRedoEnabled();
	}
	
	public void updateUndoRedoEnabled()
	{		
		boolean undoEnabled = currentTool != null && currentTool.undoStack.size() > 0;
		undoButton.setEnabled(undoEnabled);
		boolean redoEnabled = currentTool != null && currentTool.redoStack.size() > 0;
		redoButton.setEnabled(redoEnabled);
	}
	
	private void handleToolSelected(EditorTool selectedTool)
	{
		enableOrDisableToolToggleButtonsAndZoom(false);
		
		MapParts mapParts = currentTool.getMapParts(); // This is moved to the new tool so that only the first tool that runs has to certain parts of the map.
		mapEditingPanel.clearHighlightedCenters();
		mapEditingPanel.clearAreasToDraw();
		mapEditingPanel.clearProcessingCenters();
		mapEditingPanel.clearProcessingEdges();
		mapEditingPanel.clearHighlightedEdges();
		currentTool.onSwitchingAway();
		currentTool.clearUndoRedoStacks();
		currentTool.resetFurthestUndoPoint();
		updateUndoRedoEnabled();
		currentTool.setToggled(false);
		currentTool = selectedTool;
		currentTool.setToggled(true);
		toolOptionsPanelBorder.setTitle(currentTool.getToolbarName() + " Options");
		if (mapParts != null)
		{
			currentTool.setMapParts(mapParts);
		}
		toolsOptionsPanelContainer.remove(currentToolOptionsPanel);
		currentToolOptionsPanel = currentTool.getToolOptionsPanel();
		toolsOptionsPanelContainer.add(currentToolOptionsPanel);
		toolsOptionsPanelContainer.repaint();
		currentTool.handleZoomChange(parseZoom((String)zoomComboBox.getSelectedItem()));
	}
	
	public void enableOrDisableToolToggleButtonsAndZoom(boolean enable)
	{
		areToolToggleButtonsEnabled = enable;
		for (EditorTool tool: tools)
		{
			tool.setToggleButtonEnabled(enable);
		}
		zoomComboBox.setEnabled(enable);
		mapIsDrawingLabel.setVisible(!enable);
		clearEntireMapButton.setEnabled(enable);
	}
	
	private double parseZoom(String zoomStr)
	{
		double zoomPercent = Double.parseDouble(zoomStr.substring(0, zoomStr.length() - 1));
		return zoomPercent / 100.0;
	}
	

}

