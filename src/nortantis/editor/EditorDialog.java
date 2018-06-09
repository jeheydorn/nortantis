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
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.border.EtchedBorder;

import nortantis.MapParts;
import nortantis.MapSettings;
import nortantis.RunSwing;
import nortantis.UserPreferences;
import util.JComboBoxFixed;

@SuppressWarnings("serial")
public class EditorDialog extends JDialog
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
	
	/**
	 * Creates a dialog for editing text.
	 * @param settings Settings for the map. The user's edits will be stored in settings.edits.
	 * 	      Other fields in settings may be modified in the editing process and will not be stored 
	 * 		  after the editor closes.
	 * @throws IOException 
	 */
	public EditorDialog(final MapSettings settings, final RunSwing runSwing)
	{
		final EditorDialog thisDialog = this;
		setBounds(100, 100, 1122, 701);
		
		runSwing.clearEditsMenuItem.setEnabled(true);

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
			currentTool = tools.get(0);
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
			JToggleButton toolButton = new JToggleButton(tool.getToolbarName());
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
		toolsOptionsPanelContainer.setBorder(BorderFactory.createTitledBorder(new EtchedBorder(EtchedBorder.LOWERED), "Tool Options"));
				
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
		    @Override
			public void windowClosing(WindowEvent e)
			{
				KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(myKeyEventDispatcher);
				UserPreferences.getInstance().lastEditorTool = currentTool.getToolbarName();
				currentTool.onSwitchingAway();
				
		    	// Undo and redo assign edits, so re-assign the pointer in RunSwing.
		    	runSwing.edits = settings.edits;
		    	runSwing.updateFieldsWhenEditsChange();
			}
		});
		
		handleToolSelected(currentTool);
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
		currentTool.setToggled(false);
		currentTool = selectedTool;
		currentTool.setToggled(true);
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
	}
	
	private double parseZoom(String zoomStr)
	{
		double zoomPercent = Double.parseDouble(zoomStr.substring(0, zoomStr.length() - 1));
		return zoomPercent / 100.0;
	}
}

