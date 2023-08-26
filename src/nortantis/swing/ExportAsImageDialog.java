package nortantis.swing;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;
import java.util.Hashtable;

import javax.swing.DefaultFocusManager;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSlider;
import javax.swing.SwingWorker;

import nortantis.CancelledException;
import nortantis.ImageCache;
import nortantis.MapCreator;
import nortantis.MapSettings;
import nortantis.util.ImageHelper;
import nortantis.util.Logger;

@SuppressWarnings("serial")
public class ExportAsImageDialog extends JDialog
{
	private JProgressBar progressBar;
	boolean isCanceled;
	private JButton cancelButton;
	private JButton exportButton;
	private JSlider resolutionSlider;
	private MapCreator mapCreator;

	public ExportAsImageDialog(MainWindow mainWindow)
	{
		super(mainWindow, "Export as Image", Dialog.ModalityType.APPLICATION_MODAL);
		
		setSize(new Dimension(350, 170));
		getContentPane().setLayout(new BorderLayout());
		
		GridBagOrganizer organizer = new GridBagOrganizer();
		getContentPane().add(organizer.panel, BorderLayout.CENTER);
		
		resolutionSlider = new JSlider();
		resolutionSlider.setPaintLabels(true);
		resolutionSlider.setValue(100);
		resolutionSlider.setSnapToTicks(true);
		resolutionSlider.setPaintTicks(true);
		resolutionSlider.setMinorTickSpacing(25);
		resolutionSlider.setMajorTickSpacing(25);
		resolutionSlider.setMinimum(25);
		resolutionSlider.setMaximum(calcMaximumResolution());
		int labelFrequency = resolutionSlider.getMaximum() < 300 ? 50 : 100;
		{
			Hashtable<Integer, JLabel> labelTable = new Hashtable<Integer, JLabel>();
			for (int i = resolutionSlider.getMinimum(); i < resolutionSlider.getMaximum() + 1; i += resolutionSlider.getMinorTickSpacing())
			{
				if (i % labelFrequency == 0)
				{
					labelTable.put(i, new JLabel(i + "%"));
				}
			}
			resolutionSlider.setLabelTable(labelTable);
		}
		resolutionSlider.setValue((int)(mainWindow.getResolutionToSave() * 100));
		organizer.addLabelAndComponentToPanel("Resolution:", "This percentage is multiplied by the size of the map to determine "
				+ "the resolution to draw at. The maximum allowed resolution is determined by the amount of memory on this device.", 
				resolutionSlider);
		
		
		// TODO add options for opening the map in the system default viewer or export to a selected file. 
		// Store the folder path without the file name in user settings. Default the file name to the nort file name.
		
		progressBar = new JProgressBar();
		progressBar.setStringPainted(true);
		progressBar.setString("Exporting...");
		progressBar.setIndeterminate(true);
		progressBar.setVisible(false);
		organizer.addVerticalFillerRow();
		organizer.addLeftAlignedComponent(progressBar, 0, 0, false);

		
		JPanel bottomButtonsPanel = new JPanel();														
		bottomButtonsPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
		exportButton = new JButton("<html><u>E</u>xport</html>");
		exportButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				exportButton.setEnabled(false);
				resolutionSlider.setEnabled(false);
				mapCreator = new MapCreator();

				// Run the export through doWhenMapIsReadyForInteractions so that we don't risk running out of memory
				// or end up clearing the image cache while a draw is still going.
				mainWindow.updater.doWhenMapIsReadyForInteractions(() -> exportMapAndCloseDialog(mainWindow, resolutionSlider.getValue() / 100.0));
			}
		});
		bottomButtonsPanel.add(exportButton);

		cancelButton = new JButton("<html><u>C</u>ancel</html>");
		cancelButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (mapCreator != null)
				{
					mapCreator.cancel();
				}
				dispose();
			}
		});
		bottomButtonsPanel.add(cancelButton);
		getContentPane().add(bottomButtonsPanel, BorderLayout.SOUTH);
		
		KeyEventDispatcher myKeyEventDispatcher = new DefaultFocusManager()
		{
			public boolean dispatchKeyEvent(KeyEvent e)
			{
				if ((e.getKeyCode() == KeyEvent.VK_E) && e.isAltDown())
				{
					exportButton.doClick();
				}
				else if ((e.getKeyCode() == KeyEvent.VK_C) && e.isAltDown())
				{
					cancelButton.doClick();
				}
			return false;
			}
		};
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(myKeyEventDispatcher);
		
		addWindowListener(new WindowListener()
		{
			
			@Override
			public void windowOpened(WindowEvent e)
			{
			}
			
			@Override
			public void windowIconified(WindowEvent e)
			{
			}
			
			@Override
			public void windowDeiconified(WindowEvent e)
			{
			}
			
			@Override
			public void windowDeactivated(WindowEvent e)
			{
			}
			
			@Override
			public void windowClosing(WindowEvent e)
			{
				if (mapCreator != null)
				{
					mapCreator.cancel();
				}
			}
			
			@Override
			public void windowClosed(WindowEvent e)
			{
			}
			
			@Override
			public void windowActivated(WindowEvent e)
			{
			}
		});
	}
	
	private void exportMapAndCloseDialog(MainWindow mainWindow, double resolution)
	{		
		progressBar.setVisible(true);
		mainWindow.setResolutionToSave(resolution);
		final MapSettings settings = mainWindow.getSettingsFromGUI(false).deepCopy();
		

		SwingWorker<BufferedImage, Void> worker = new SwingWorker<BufferedImage, Void>()
		{
			@Override
			public BufferedImage doInBackground() throws Exception
			{
				Logger.clear();
				ImageCache.getInstance().clear();

				BufferedImage map;
				try
				{
					map = mapCreator.createMap(settings, null, null);
				}
				catch (CancelledException e)
				{
					Logger.println("Map creation cancelled.");
					return null;
				}
				
				System.gc();

				Logger.println("Opening the map in your system's default image editor.");
				String fileName = ImageHelper.openImageInSystemDefaultEditor(map, "map_" + settings.randomSeed);
				Logger.println("Map written to " + fileName);
				ImageCache.getInstance().clear();
				return map;
			}

			@Override
			public void done()
			{
				try
				{
					get();
				}
				catch (Exception ex)
				{
					SwingHelper.handleBackgroundThreadException(ex, true);
				}
				
				dispose();
			}
		};
		worker.execute();
	}
	
	private int calcMaximumResolution()
	{
		long maxBytes = Runtime.getRuntime().maxMemory();
		// The required memory is quadratic in the resolution used.
		// To generate a map at resolution 225 takes 7GB, so 7×1024^3÷(225^2)
		// = 148468.
		int maxResolution = (int) Math.sqrt(maxBytes / 148468L);

		// The FFT-based code will create arrays in powers of 2.
		int nextPowerOf2 = ImageHelper.getPowerOf2EqualOrLargerThan(maxResolution / 100.0);
		int resolutionAtNextPowerOf2 = nextPowerOf2 * 100;
		// Average with the original prediction because not all code is
		// FFT-based.
		maxResolution = (maxResolution + resolutionAtNextPowerOf2) / 2;

		if (maxResolution > 500)
		{
			// This is in case Runtime.maxMemory returns Long's max value, which
			// it says it will if it fails.
			return 1000;
		}
		if (maxResolution < 100)
		{
			return 100;
		}
		// The resolution slider uses multiples of 25.
		maxResolution -= maxResolution % 25;
		return maxResolution;
	}

}
