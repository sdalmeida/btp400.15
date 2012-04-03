/*
 * Created by JFormDesigner on Mon Apr 21 12:50:34 EDT 2008
 */

package Provider.GoogleMapsStatic.TestUI;

import Provider.GoogleMapsStatic.*;
import Task.*;
import Task.Manager.*;
import Task.ProgressMonitor.*;
import Task.Support.CoreSupport.*;
import Task.Support.GUISupport.*;
import com.jgoodies.forms.factories.*;
import info.clearthought.layout.*;

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;

import javax.imageio.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.basic.BasicInternalFrameTitlePane.CloseAction;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.awt.datatransfer.*;
import java.beans.*;
import java.text.*;
import java.io.*;
import java.util.concurrent.*;

/** @author nazmul idris */
public class SampleApp extends JFrame {
	//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
	// data members
	//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
	/** reference to task */
	private SimpleTask _task;
	/** this might be null. holds the image to display in a popup */
	private BufferedImage _img;
	/** this might be null. holds the text in case image doesn't display */
	private String _respStr;

	//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
	// main method...
	//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX

	public static void main(String[] args) {
		Utils.createInEDT(SampleApp.class);
	}

	//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
	// constructor
	//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX

	private void doInit() {
		GUIUtils.setAppIcon(this, "burn.png");
		GUIUtils.centerOnScreen(this);
		setVisible(true);

		int W = 28, H = W;
		boolean blur = false;
		float alpha = .7f;

		try {
			btnGetMap.setIcon(ImageUtils.loadScaledBufferedIcon("ok1.png", W, H, blur, alpha));
		}
		catch (Exception e) {
			System.out.println(e);
		}

		_setupTask();
	}

	/** create a test task and wire it up with a task handler that dumps output to the textarea */
	@SuppressWarnings("unchecked")
	private void _setupTask() {

		TaskExecutorIF<ByteBuffer> functor = new TaskExecutorAdapter<ByteBuffer>() {
			public ByteBuffer doInBackground(Future<ByteBuffer> swingWorker,
					SwingUIHookAdapter hook) throws Exception
					{

				_initHook(hook);

				// set the license key
				MapLookup.setLicenseKey("");
				// get the uri for the static map
				String uri = MapLookup.getMap(Double.parseDouble(ttfLat.getText()),
						Double.parseDouble(ttfLon.getText()),
						370,
						512,
						Integer.parseInt(ttfZoom.getText())
						);
				sout("Google Maps URI=" + uri);

				// get the map from Google
				GetMethod get = new GetMethod(uri);
				new HttpClient().executeMethod(get);

				ByteBuffer data = HttpUtils.getMonitoredResponse(hook, get);

				try {
					_img = ImageUtils.toCompatibleImage(ImageIO.read(data.getInputStream()));
					sout("converted downloaded data to image...");
				}
				catch (Exception e) {
					_img = null;
					sout("The URI is not an image. Data is downloaded, can't display it as an image.");
					_respStr = new String(data.getBytes());
				}

				return data;
					}

			@Override public String getName() {
				return _task.getName();
			}
		};

		_task = new SimpleTask(
				new TaskManager(),
				functor,
				"HTTP GET Task",
				"Download an image from a URL",
				AutoShutdownSignals.Daemon
				);

		_task.addStatusListener(new PropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent evt) {
				sout(":: task status change - " + ProgressMonitorUtils.parseStatusMessageFrom(evt));
				//lblProgressStatus.setText(ProgressMonitorUtils.parseStatusMessageFrom(evt));
			}
		});

		_task.setTaskHandler(new
				SimpleTaskHandler<ByteBuffer>() {
			@Override public void beforeStart(AbstractTask task) {
				sout(":: taskHandler - beforeStart");
			}
			@Override public void started(AbstractTask task) {
				sout(":: taskHandler - started ");
			}
			/** {@link SampleApp#_initHook} adds the task status listener, which is removed here */
			@Override public void stopped(long time, AbstractTask task) {
				sout(":: taskHandler [" + task.getName() + "]- stopped");
				sout(":: time = " + time / 1000f + "sec");
				task.getUIHook().clearAllStatusListeners();
			}
			@Override public void interrupted(Throwable e, AbstractTask task) {
				sout(":: taskHandler [" + task.getName() + "]- interrupted - " + e.toString());
			}
			@Override public void ok(ByteBuffer value, long time, AbstractTask task) {
				sout(":: taskHandler [" + task.getName() + "]- ok - size=" + (value == null
						? "null"
								: value.toString()));
				if (_img != null) {
					_displayImgInFrame();
				}
				else _displayRespStrInFrame();

			}
			@Override public void error(Throwable e, long time, AbstractTask task) {
				sout(":: taskHandler [" + task.getName() + "]- error - " + e.toString());
			}
			@Override public void cancelled(long time, AbstractTask task) {
				sout(" :: taskHandler [" + task.getName() + "]- cancelled");
			}
		}
				);
	}

	private SwingUIHookAdapter _initHook(SwingUIHookAdapter hook) {

		PropertyChangeListener listener = new PropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent evt) {
				SwingUIHookAdapter.PropertyList type = ProgressMonitorUtils.parseTypeFrom(evt);
				int progress = ProgressMonitorUtils.parsePercentFrom(evt);
				String msg = ProgressMonitorUtils.parseMessageFrom(evt);
				sout(msg);
			}
		};

		hook.addRecieveStatusListener(listener);
		hook.addSendStatusListener(listener);
		hook.addUnderlyingIOStreamInterruptedOrClosed(new PropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent evt) {
				sout(evt.getPropertyName() + " fired!!!");
			}
		});

		return hook;
	}

	private void _displayImgInFrame() {
		slider1 = new JSlider();

		imgLbl = new JLabel(new ImageIcon(_img));
		imgLbl.setToolTipText(MessageFormat.format("<html>Image downloaded from URI<br>size: w={0}, h={1}</html>",
				_img.getWidth(), _img.getHeight()));

		panel1.removeAll();

		slider1.setOrientation(SwingConstants.VERTICAL);
		slider1.setMinimum(1);
		slider1.setMaximum(19);
		slider1.setMajorTickSpacing(1);
		slider1.setPaintLabels(true);
		slider1.setValue(Integer.parseInt(ttfZoom.getText()));
		slider1.addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(ChangeEvent arg0) {
				if(!slider1.getValueIsAdjusting()){
					ttfZoom.setText(Integer.toString(slider1.getValue()));
					startTaskAction();
				}
			}
		});

		panel1.add(slider1, new TableLayoutConstraints(0, 0, 0, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
		panel1.add(imgLbl, new TableLayoutConstraints(1, 0, 1, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
		panel1.updateUI();
	}

	private void _displayRespStrInFrame() {

		final JFrame frame = new JFrame("Google Static Map - Error");
		GUIUtils.setAppIcon(frame, "69.png");
		frame.setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		JTextArea response = new JTextArea(_respStr, 25, 80);
		response.addMouseListener(new MouseListener() {
			public void mouseClicked(MouseEvent e) {}
			public void mousePressed(MouseEvent e) { frame.dispose();}
			public void mouseReleased(MouseEvent e) { }
			public void mouseEntered(MouseEvent e) { }
			public void mouseExited(MouseEvent e) { }
		});

		frame.setContentPane(new JScrollPane(response));
		frame.pack();

		GUIUtils.centerOnScreen(frame);
		frame.setVisible(true);
	}

	/** simply dump status info to the textarea */
	private void sout(final String s) {
		Runnable soutRunner = new Runnable() {
			public void run() {
			}
		};

		if (ThreadUtils.isInEDT()) {
			soutRunner.run();
		}
		else {
			SwingUtilities.invokeLater(soutRunner);
		}
	}

	private void startTaskAction() {
		try {
			_task.execute();
		}
		catch (TaskException e) {
			sout(e.getMessage());
		}
	}


	public SampleApp() {
		initComponents();
		doInit();
	}

	private void quitProgram() {
		_task.shutdown();
		System.exit(0);
	}


	private void initComponents() {

		menuBar1 = new JMenuBar();
		menu1 = new JMenu();
		menuItem1 = new JMenuItem();
		panel1 = new JPanel();
		slider1 = new JSlider();
		label1 = new JLabel();
		panel2 = new JPanel();
		button1 = new JButton();
		button2 = new JButton();
		button3 = new JButton();
		button4 = new JButton();
		label2 = new JLabel();
		label3 = new JLabel();

		ttfLat		= new JTextField();
		btnGetMap	= new JButton();
		ttfLon		= new JTextField();
		ttfZoom		= new JTextField("15");
		panel1		= new JPanel();
		panel3		= new JPanel();
		panel2		= new JPanel();
		button2		= new JButton();
		button3		= new JButton();
		button4		= new JButton();
		imgLbl		= new JLabel();

		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		// JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents
		// Generated using JFormDesigner Evaluation license - Simon Almeida
		menuBar1 = new JMenuBar();
		menu1 = new JMenu();
		menuItem1 = new JMenuItem();
		panel1 = new JPanel();
		slider1 = new JSlider();
		label1 = new JLabel();
		panel2 = new JPanel();
		button1 = new JButton();
		button2 = new JButton();
		button3 = new JButton();
		button4 = new JButton();
		label2 = new JLabel();
		label3 = new JLabel();
		button5 = new JButton();
		button9 = new JButton();
		button7 = new JButton();
		button8 = new JButton();
		button6 = new JButton();

		//======== this ========
		Container contentPane = getContentPane();
		contentPane.setLayout(new TableLayout(new double[][] {
				{308, 304},
				{393}}));
		((TableLayout)contentPane.getLayout()).setHGap(5);
		((TableLayout)contentPane.getLayout()).setVGap(5);

		//======== menuBar1 ========
		{

			//======== menu1 ========
			{
				menu1.setText("File");

				//---- menuItem1 ----
				menuItem1.setText("Copy Map URL");
				menuItem1.addActionListener(new ActionListener() {

					@Override
					public void actionPerformed(ActionEvent arg0) {
						if(MapLookup.sb != null){
							TextTransfer clipboard = new TextTransfer();
							clipboard.setClipboardContents(MapLookup.sb.toString());
							System.out.println(clipboard.getClipboardContents());
							JOptionPane.showMessageDialog(getContentPane(),
								    "Copied Map to Clipboard!");
						}else{
							JOptionPane.showMessageDialog(getContentPane(),
								    "Please Get Map First!","Error!", JOptionPane.ERROR_MESSAGE);
						}
					}
				});
				menu1.add(menuItem1);
			}
			menuBar1.add(menu1);
		}
		setJMenuBar(menuBar1);

		//======== panel1 ========
		{
			panel1.setBorder(new CompoundBorder(
					new TitledBorder("Map View"),
					Borders.DLU2_BORDER));

			panel1.setLayout(new TableLayout(new double[][] {
					{TableLayout.PREFERRED, 255},
					{363}}));
			((TableLayout)panel1.getLayout()).setHGap(5);
			((TableLayout)panel1.getLayout()).setVGap(5);

			//---- slider1 ----
			slider1.setOrientation(SwingConstants.VERTICAL);
			slider1.setMinimum(1);
			slider1.setMaximum(19);
			slider1.setMajorTickSpacing(1);
			slider1.setPaintLabels(true);
			slider1.setValue(Integer.parseInt(ttfZoom.getText()));
			slider1.addChangeListener(new ChangeListener() {

				@Override
				public void stateChanged(ChangeEvent arg0) {
					if(!slider1.getValueIsAdjusting()){
						ttfZoom.setText(Integer.toString(slider1.getValue()));
						startTaskAction();
					}
				}
			});

			panel1.add(slider1, new TableLayoutConstraints(0, 0, 0, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

			//---- Image HERE! ----
			panel1.add(imgLbl, new TableLayoutConstraints(1, 0, 1, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
		}
		contentPane.add(panel1, new TableLayoutConstraints(0, 0, 0, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

		//======== panel2 ========
		{
			panel2.setBorder(new CompoundBorder(
					new TitledBorder("Map Options"),
					Borders.DLU2_BORDER));
			panel2.setLayout(new TableLayout(new double[][] {
					{TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.PREFERRED},
					{TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.PREFERRED}}));
			((TableLayout)panel2.getLayout()).setHGap(5);
			((TableLayout)panel2.getLayout()).setVGap(5);

			//---- button1 ----
			button1.setText("Normal");
			button1.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent arg0) {
					MapLookup.setType("roadmap");			
					startTaskAction();
				}
			});
			panel2.add(button1, new TableLayoutConstraints(0, 0, 0, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

			//---- button2 ----
			button2.setText("Terrain");
			button2.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent arg0) {
					MapLookup.setType("terrain");			
					startTaskAction();
				}
			});
			panel2.add(button2, new TableLayoutConstraints(1, 0, 1, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

			//---- button3 ----
			button3.setText("Satellite");
			button3.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent arg0) {
					MapLookup.setType("satellite");			
					startTaskAction();
				}
			});
			panel2.add(button3, new TableLayoutConstraints(2, 0, 2, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

			//---- button4 ----
			button4.setText("Hybrid");
			button4.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent arg0) {
					MapLookup.setType("hybrid");			
					startTaskAction();
				}
			});
			panel2.add(button4, new TableLayoutConstraints(3, 0, 3, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

			//---- label2 ----
			label2.setText("Latitude:");
			panel2.add(label2, new TableLayoutConstraints(0, 2, 0, 2, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
			ttfLat.setText("38.931099");
			panel2.add(ttfLat, new TableLayoutConstraints(1, 2, 3, 2, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

			//---- label3 ----
			label3.setText("Longitude:");
			panel2.add(label3, new TableLayoutConstraints(0, 3, 0, 3, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
			ttfLon.setText("-77.3489");
			panel2.add(ttfLon, new TableLayoutConstraints(1, 3, 3, 3, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

			//---- GetMap ----
			btnGetMap.setText("Get Map");
			btnGetMap.setHorizontalAlignment(SwingConstants.LEFT);
			btnGetMap.setMnemonic('G');
			btnGetMap.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					startTaskAction();
				}
			});
			panel2.add(btnGetMap, new TableLayoutConstraints(0, 5, 1, 5, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

			//---- button9 ----
			button9.setText("Up");
			button9.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent arg0) {
					double getValue = Double.parseDouble(ttfLat.getText()) + 0.001;
					ttfLat.setText(Double.toString(getValue));
					startTaskAction();
				}
			});
			panel2.add(button9, new TableLayoutConstraints(1, 7, 1, 7, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

			//---- button7 ----
			button7.setText("Left");
			button7.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent arg0) {
					double getValue = Double.parseDouble(ttfLon.getText()) - 0.001;
					ttfLon.setText(Double.toString(getValue));
					startTaskAction();
				}
			});
			panel2.add(button7, new TableLayoutConstraints(0, 8, 0, 8, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

			//---- button8 ----
			button8.setText("Down");
			button8.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent arg0) {
					double getValue = Double.parseDouble(ttfLat.getText()) - 0.001;
					ttfLat.setText(Double.toString(getValue));
					startTaskAction();
				}
			});
			panel2.add(button8, new TableLayoutConstraints(1, 8, 1, 8, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

			//---- button6 ----
			button6.setText("Right");
			button6.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent arg0) {
					double getValue = Double.parseDouble(ttfLon.getText()) + 0.001;
					ttfLon.setText(Double.toString(getValue));
					startTaskAction();
				}
			});
			panel2.add(button6, new TableLayoutConstraints(2, 8, 2, 8, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
		}
		contentPane.add(panel2, new TableLayoutConstraints(1, 0, 1, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
		pack();
		setLocationRelativeTo(getOwner());
		// JFormDesigner - End of component initialization  //GEN-END:initComponents
	}

	// JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables
	// Generated using JFormDesigner non-commercial license
	private JPanel panel1;
	private JTextField ttfLat;
	private JButton btnGetMap;
	private JTextField ttfLon;
	private JTextField ttfZoom;
	private JPanel panel2;
	private JPanel panel3;
	private JButton button2;
	private JButton button3;
	private JButton button4;
	private JLabel imgLbl;

	private JSlider slider1;
	private JLabel label1;
	private JButton button1;

	private JMenuBar menuBar1;
	private JMenu menu1;
	private JMenuItem menuItem1;
	private JLabel label2;
	private JLabel label3;
	private JButton button5;
	private JButton button9;
	private JButton button7;
	private JButton button8;
	private JButton button6;
	// JFormDesigner - End of variables declaration  //GEN-END:variables
}
