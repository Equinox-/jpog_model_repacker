package org.equinoxscripts.ojpog.repacker;

import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.equinoxscripts.ojpog.io.tmd.TMD_File;
import org.equinoxscripts.ojpog.repacker.model.export.ModelBuilder_DAE;
import org.equinoxscripts.ojpog.repacker.model.merge.ModelMerger_DAE;

public class SimpleGUI {
	static {

		System.loadLibrary("64".equals(System.getProperty("sun.arch.data.model")) ? "gdx64" : "gdx");
	}

	private static final FileNameExtensionFilter TMD_EXTENSION = new FileNameExtensionFilter("Toshi Model Driver",
			"tmd");
	private static final FileNameExtensionFilter DAE_EXTENSION = new FileNameExtensionFilter("Collada Asset Exchange",
			"dae");

	static File tmdFileInNorm = null, daeFileNorm = null, tmdFileOutNorm = null;

	private static void alert(String s) {
		JDialog jd = new JDialog();
		jd.setTitle("Alert");
		JTextPane pane = new JTextPane();
		pane.setText(s);
		jd.setSize(500, 500);
		jd.add(new JScrollPane(pane));
		jd.setVisible(true);
	}

	public static void main(String[] args) {

		JFileChooser chooser = new JFileChooser();
		chooser.setMultiSelectionEnabled(false);
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

		JFrame modeSetup = new JFrame();
		modeSetup.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		modeSetup.setSize(200, 200);
		modeSetup.setTitle("Choose an Option");
		modeSetup.getContentPane().setLayout(new GridLayout(2, 2));
		{
			JButton repackAsDAE = new JButton("Export to DAE");
			repackAsDAE.addActionListener((a) -> {
				chooser.setFileFilter(TMD_EXTENSION);
				chooser.setDialogTitle("Choose a TMD file");
				if (tmdFileInNorm != null)
					chooser.setSelectedFile(tmdFileInNorm);
				if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
					File tmdFile = tmdFileInNorm = chooser.getSelectedFile();
					chooser.setFileFilter(DAE_EXTENSION);
					chooser.setDialogTitle("Save as... [DAE]");
					if (daeFileNorm != null)
						chooser.setSelectedFile(daeFileNorm);
					if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
						File outFile = daeFileNorm = chooser.getSelectedFile();

						try {
							TMD_File tmd = new TMD_File(tmdFile);

							ModelBuilder_DAE.write(outFile, tmd);
							alert("Saved to " + outFile.getAbsolutePath() + "\n" + tmd.summary());
						} catch (Throwable t) {
							StringWriter sw = new StringWriter();
							PrintWriter pw = new PrintWriter(sw);
							t.printStackTrace(pw);
							alert("Failed:\n" + sw.toString());
						}
					}
				}
			});
			modeSetup.add(repackAsDAE);
		}
		{
			JButton mergeWithDAE = new JButton("Merge with DAE");
			mergeWithDAE.addActionListener((a) -> {
				chooser.setFileFilter(TMD_EXTENSION);
				chooser.setDialogTitle("Choose a base file");
				if (tmdFileInNorm != null)
					chooser.setSelectedFile(tmdFileInNorm);
				if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
					File tmdFile = tmdFileInNorm = chooser.getSelectedFile();
					chooser.setFileFilter(DAE_EXTENSION);
					chooser.setDialogTitle("Choose a file to merge from");
					if (daeFileNorm != null)
						chooser.setSelectedFile(daeFileNorm);
					if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
						File daeFile = daeFileNorm = chooser.getSelectedFile();
						chooser.setFileFilter(TMD_EXTENSION);
						chooser.setDialogTitle("Save as... [TMD]");
						if (tmdFileOutNorm != null)
							chooser.setSelectedFile(tmdFileOutNorm);
						if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
							File outFile = tmdFileOutNorm = chooser.getSelectedFile();

							try {
								TMD_File tmd = new TMD_File(tmdFile);
								ModelMerger_DAE merge = new ModelMerger_DAE(tmd, ModelMerger_DAE.loadScene(daeFile));
								merge.apply();
								tmd.updateIntegrity();
								ByteBuffer output = ByteBuffer.allocate(tmd.length()).order(ByteOrder.LITTLE_ENDIAN);
								tmd.write(output);
								if (output.hasRemaining())
									throw new IOException("Length wasn't equal to write");
								output.position(0);
								Utils.write(outFile, output);
								alert("Saved to " + outFile.getAbsolutePath());
							} catch (Throwable t) {
								StringWriter sw = new StringWriter();
								PrintWriter pw = new PrintWriter(sw);
								t.printStackTrace(pw);
								alert(sw.toString());
							}
						}
					}
				}
			});
			modeSetup.add(mergeWithDAE);
		}
		modeSetup.setVisible(true);
	}
}
