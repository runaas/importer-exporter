/*
 * This file is part of the 3D City Database Importer/Exporter.
 * Copyright (c) 2007 - 2011
 * Institute for Geodesy and Geoinformation Science
 * Technische Universitaet Berlin, Germany
 * http://www.gis.tu-berlin.de/
 *
 * The 3D City Database Importer/Exporter program is free software:
 * you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program. If not, see 
 * <http://www.gnu.org/licenses/>.
 * 
 * The development of the 3D City Database Importer/Exporter has 
 * been financially supported by the following cooperation partners:
 * 
 * Business Location Center, Berlin <http://www.businesslocationcenter.de/>
 * virtualcitySYSTEMS GmbH, Berlin <http://www.virtualcitysystems.de/>
 * Berlin Senate of Business, Technology and Women <http://www.berlin.de/sen/wtf/>
 */
package de.tub.citydb.components.citygml.importer.gui.view;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.citygml4j.builder.jaxb.JAXBBuilder;

import de.tub.citydb.api.event.EventDispatcher;
import de.tub.citydb.api.log.LogLevelType;
import de.tub.citydb.api.log.Logger;
import de.tub.citydb.api.registry.ObjectRegistry;
import de.tub.citydb.components.citygml.common.gui.view.FilterPanel;
import de.tub.citydb.components.citygml.common.gui.view.FilterPanel.FilterPanelType;
import de.tub.citydb.components.citygml.importer.controller.Importer;
import de.tub.citydb.components.citygml.importer.controller.XMLValidator;
import de.tub.citydb.components.common.event.InterruptEnum;
import de.tub.citydb.components.common.event.InterruptEvent;
import de.tub.citydb.config.Config;
import de.tub.citydb.config.internal.Internal;
import de.tub.citydb.config.project.importer.ImportFilterConfig;
import de.tub.citydb.database.DBConnectionPool;
import de.tub.citydb.gui.ImpExpGui;
import de.tub.citydb.gui.components.ImportStatusDialog;
import de.tub.citydb.gui.components.XMLValidationStatusDialog;
import de.tub.citydb.util.GuiUtil;

@SuppressWarnings("serial")
public class ImportPanel extends JPanel implements DropTargetListener {
	private final ReentrantLock mainLock = new ReentrantLock();
	private final Logger LOG = Logger.getInstance();
	private final JAXBBuilder jaxbBuilder;
	private final Config config;
	private final ImpExpGui mainView;
	private final DBConnectionPool dbPool;
	
	private JList fileList;
	private DefaultListModel fileListModel;
	private JButton browseButton;
	private JButton removeButton;
	private JButton importButton;
	private JButton validateButton;
	private FilterPanel filterPanel;
	private JTextField workspaceText;

	private JPanel row2;
	private JLabel row2_1;

	public ImportPanel(JAXBBuilder jaxbBuilder, Config config, ImpExpGui mainView) {
		this.jaxbBuilder = jaxbBuilder;
		this.config = config;
		this.mainView = mainView;
		dbPool = DBConnectionPool.getInstance();
		
		initGui();
	}

	private void initGui() {
		fileList = new JList();		
		browseButton = new JButton();
		removeButton = new JButton();
		filterPanel = new FilterPanel(config, FilterPanelType.IMPORT);
		importButton = new JButton();
		validateButton = new JButton();
		workspaceText = new JTextField();

		fileListModel = new DefaultListModel();
		fileList.setModel(fileListModel);
		fileList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

		DropTarget dropTarget = new DropTarget(fileList, this);
		fileList.setDropTarget(dropTarget);
		setDropTarget(dropTarget);

		browseButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				loadFile(Internal.I18N.getString("main.tabbedPane.import"));
			}
		});

		Action remove = new RemoveAction();
		fileList.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "remove");
		fileList.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "remove");
		fileList.getActionMap().put("remove", remove);
		removeButton.addActionListener(remove);

		importButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Thread thread = new Thread() {
					public void run() {
						doImport();
					}
				};
				thread.start();
			}
		});
		
		validateButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Thread thread = new Thread() {
					public void run() {
						doValidate();
					}
				};
				thread.start();
			}
		});
		
		//layout
		setLayout(new GridBagLayout());
		{
			JPanel row1 = new JPanel();
			JPanel buttons = new JPanel();
			add(row1,GuiUtil.setConstraints(0,0,1.0,.3,GridBagConstraints.BOTH,10,5,5,5));
			row1.setLayout(new GridBagLayout());
			{
				row1.add(new JScrollPane(fileList), GuiUtil.setConstraints(0,0,1.0,1.0,GridBagConstraints.BOTH,5,5,5,5));
				row1.add(buttons, GuiUtil.setConstraints(1,0,0.0,0.0,GridBagConstraints.BOTH,5,5,5,5));
				buttons.setLayout(new GridBagLayout());
				{
					buttons.add(browseButton, GuiUtil.setConstraints(0,0,0.0,0.0,GridBagConstraints.HORIZONTAL,0,0,0,0));
					GridBagConstraints c = GuiUtil.setConstraints(0,1,0.0,1.0,GridBagConstraints.HORIZONTAL,5,0,5,0);
					c.anchor = GridBagConstraints.NORTH;
					buttons.add(removeButton, c);
				}
			}
		}
		{
			row2 = new JPanel();
			add(row2, GuiUtil.setConstraints(0,1,0.0,0.0,GridBagConstraints.HORIZONTAL,0,5,5,5));
			row2.setBorder(BorderFactory.createTitledBorder(""));
			row2.setLayout(new GridBagLayout());
			row2_1 = new JLabel();
			{
				row2.add(row2_1, GuiUtil.setConstraints(0,0,0.0,0.0,GridBagConstraints.NONE,0,5,5,5));
				row2.add(workspaceText, GuiUtil.setConstraints(1,0,1.0,0.0,GridBagConstraints.HORIZONTAL,0,5,5,5));
			}
		}
		{
			add(filterPanel, GuiUtil.setConstraints(0,2,1.0,1.0,GridBagConstraints.BOTH,0,5,0,5));
		}
		{
			JPanel row3 = new JPanel();
			add(row3, GuiUtil.setConstraints(0,3,0.0,0.0,GridBagConstraints.HORIZONTAL,0,5,5,5));	
			row3.setLayout(new GridBagLayout());
			{
				GridBagConstraints c = GuiUtil.setConstraints(0,0,1.0,0.0,GridBagConstraints.NONE,5,5,5,5);
				c.gridwidth = 2;
				row3.add(importButton, c);				

				c = GuiUtil.setConstraints(1,0,0.0,0.0,GridBagConstraints.NONE,5,5,5,0);
				c.anchor = GridBagConstraints.EAST;
				row3.add(validateButton, c);
			}
		}
	}

	public void doTranslation() {
		browseButton.setText(Internal.I18N.getString("common.button.browse"));
		removeButton.setText(Internal.I18N.getString("import.button.remove"));
		importButton.setText(Internal.I18N.getString("import.button.import"));
		validateButton.setText(Internal.I18N.getString("import.button.validate"));
		row2.setBorder(BorderFactory.createTitledBorder(Internal.I18N.getString("common.border.versioning")));
		row2_1.setText(Internal.I18N.getString("common.label.workspace"));

		filterPanel.doTranslation();
	}

	//public-methoden
	public void loadSettings() {
		workspaceText.setText(config.getProject().getDatabase().getWorkspaces().getImportWorkspace().getName());
		filterPanel.loadSettings();
	}

	public void setSettings() {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < fileListModel.size(); ++i) {
			builder.append(fileListModel.get(i).toString());
			builder.append("\n");
		}

		config.getInternal().setImportFileName(builder.toString());		

		String workspace = workspaceText.getText().trim();
		if (!workspace.equals(Internal.ORACLE_DEFAULT_WORKSPACE) && 
				(workspace.length() == 0 || workspace.toUpperCase().equals(Internal.ORACLE_DEFAULT_WORKSPACE)))
			workspaceText.setText(Internal.ORACLE_DEFAULT_WORKSPACE);

		config.getProject().getDatabase().getWorkspaces().getImportWorkspace().setName(workspaceText.getText());
		filterPanel.setSettings();
	}

	private void doImport() {
		final ReentrantLock lock = this.mainLock;
		lock.lock();

		try {
			mainView.clearConsole();
			setSettings();

			ImportFilterConfig filter = config.getProject().getImporter().getFilter();

			// check all input values...
			if (config.getInternal().getImportFileName().trim().equals("")) {
				mainView.errorMessage(Internal.I18N.getString("import.dialog.error.incompleteData"), 
						Internal.I18N.getString("import.dialog.error.incompleteData.dataset"));
				return;
			}

			// gmlId
			if (filter.isSetSimpleFilter() &&
					filter.getSimpleFilter().getGmlIdFilter().getGmlIds().isEmpty()) {
				mainView.errorMessage(Internal.I18N.getString("import.dialog.error.incorrectData"), 
						Internal.I18N.getString("common.dialog.error.incorrectData.gmlId"));
				return;
			}

			// cityObject
			if (filter.isSetComplexFilter() &&
					filter.getComplexFilter().getFeatureCount().isSet()) {
				Long coStart = filter.getComplexFilter().getFeatureCount().getFrom();
				Long coEnd = filter.getComplexFilter().getFeatureCount().getTo();
				String coEndValue = String.valueOf(filter.getComplexFilter().getFeatureCount().getTo());

				if (coStart == null || (!coEndValue.trim().equals("") && coEnd == null)) {
					mainView.errorMessage(Internal.I18N.getString("import.dialog.error.incorrectData"), 
							Internal.I18N.getString("import.dialog.error.incorrectData.range"));
					return;
				}

				if ((coStart != null && coStart <= 0) || (coEnd != null && coEnd <= 0)) {
					mainView.errorMessage(Internal.I18N.getString("import.dialog.error.incorrectData"),
							Internal.I18N.getString("import.dialog.error.incorrectData.range"));
					return;
				}

				if (coEnd != null && coEnd < coStart) {
					mainView.errorMessage(Internal.I18N.getString("import.dialog.error.incorrectData"),
							Internal.I18N.getString("import.dialog.error.incorrectData.range"));
					return;
				}
			}

			// gmlName
			if (filter.isSetComplexFilter() &&
					filter.getComplexFilter().getGmlName().isSet() &&
					filter.getComplexFilter().getGmlName().getValue().trim().equals("")) {
				mainView.errorMessage(Internal.I18N.getString("import.dialog.error.incorrectData"),
						Internal.I18N.getString("common.dialog.error.incorrectData.gmlName"));
				return;
			}

			// BoundingBox
			if (filter.isSetComplexFilter() &&
					filter.getComplexFilter().getBoundingBox().isSet()) {
				Double xMin = filter.getComplexFilter().getBoundingBox().getLowerLeftCorner().getX();
				Double yMin = filter.getComplexFilter().getBoundingBox().getLowerLeftCorner().getY();
				Double xMax = filter.getComplexFilter().getBoundingBox().getUpperRightCorner().getX();
				Double yMax = filter.getComplexFilter().getBoundingBox().getUpperRightCorner().getY();

				if (xMin == null || yMin == null || xMax == null || yMax == null) {
					mainView.errorMessage(Internal.I18N.getString("import.dialog.error.incorrectData"),
							Internal.I18N.getString("common.dialog.error.incorrectData.bbox"));
					return;
				}
			}
			
			// affine transformation
			if (config.getProject().getImporter().getAffineTransformation().isSetUseAffineTransformation()) {
				if (JOptionPane.showConfirmDialog(
						this, 
						Internal.I18N.getString("import.dialog.warning.affineTransformation"),
						Internal.I18N.getString("common.dialog.warning.title"), 
						JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION)
					return;				
			}

			if (!dbPool.isConnected()) {
				mainView.connectToDatabase();

				if (!dbPool.isConnected())
					return;
			}

			mainView.setStatusText(Internal.I18N.getString("main.status.import.label"));
			LOG.info("Initializing database import...");

			// initialize event dispatcher
			final EventDispatcher eventDispatcher = ObjectRegistry.getInstance().getEventDispatcher();
			final ImportStatusDialog importDialog = new ImportStatusDialog(mainView, 
					Internal.I18N.getString("import.dialog.window"), 
					Internal.I18N.getString("import.dialog.msg"), 
					eventDispatcher);

			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					importDialog.setLocationRelativeTo(mainView);
					importDialog.setVisible(true);
				}
			});

			Importer importer = new Importer(jaxbBuilder, dbPool, config, eventDispatcher);

			importDialog.getCancelButton().addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							eventDispatcher.triggerEvent(new InterruptEvent(
									InterruptEnum.USER_ABORT, 
									"User abort of database import.", 
									LogLevelType.INFO, 
									this));
						}
					});
				}
			});

			boolean success = importer.doProcess();

			try {
				eventDispatcher.flushEvents();
			} catch (InterruptedException e1) {
				//
			}

			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					importDialog.dispose();
				}
			});

			if (success) {
				LOG.info("Database import successfully finished.");
			} else {
				LOG.warn("Database import aborted.");
			}

			mainView.setStatusText(Internal.I18N.getString("main.status.ready.label"));
		} finally {
			lock.unlock();
		}
	}
	
	private void doValidate() {
		final ReentrantLock lock = this.mainLock;
		lock.lock();

		try {
			mainView.clearConsole();
			setSettings();

			// check for input files...
			if (config.getInternal().getImportFileName().trim().equals("")) {
				mainView.errorMessage(Internal.I18N.getString("validate.dialog.error.incompleteData"),
						Internal.I18N.getString("validate.dialog.error.incompleteData.dataset"));
				return;
			}

			mainView.setStatusText(Internal.I18N.getString("main.status.validate.label"));
			LOG.info("Initializing XML validation...");

			// initialize event dispatcher
			final EventDispatcher eventDispatcher = ObjectRegistry.getInstance().getEventDispatcher();
			final XMLValidationStatusDialog validatorDialog = new XMLValidationStatusDialog(mainView, 
					Internal.I18N.getString("validate.dialog.window"), 
					Internal.I18N.getString("validate.dialog.title"), 
					" ", 
					Internal.I18N.getString("validate.dialog.details") , 
					true, 
					eventDispatcher);

			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					validatorDialog.setLocationRelativeTo(mainView);
					validatorDialog.setVisible(true);
				}
			});

			XMLValidator validator = new XMLValidator(jaxbBuilder, config, eventDispatcher);

			validatorDialog.getButton().addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							eventDispatcher.triggerEvent(new InterruptEvent(
									InterruptEnum.USER_ABORT, 
									"User abort of XML validation.", 
									LogLevelType.INFO, 
									this));
						}
					});
				}
			});

			boolean success = validator.doProcess();

			try {
				eventDispatcher.flushEvents();
			} catch (InterruptedException e1) {
				//
			}

			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					validatorDialog.dispose();
				}
			});

			if (success) {
				LOG.info("XML validation finished.");
			} else {
				LOG.warn("XML validation aborted.");
			}

			mainView.setStatusText(Internal.I18N.getString("main.status.ready.label"));
		} finally {
			lock.unlock();
		}
	}

	private void loadFile(String title) {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle(title);
		chooser.setMultiSelectionEnabled(true);
		chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);

		FileNameExtensionFilter filter = new FileNameExtensionFilter("CityGML Files (*.gml, *.xml)", "xml", "gml");
		chooser.addChoosableFileFilter(filter);
		chooser.addChoosableFileFilter(chooser.getAcceptAllFileFilter());
		chooser.setFileFilter(filter);

		if (fileListModel.isEmpty()) {
			if (config.getProject().getImporter().getPath().isSetLastUsedMode()) {
				chooser.setCurrentDirectory(new File(config.getProject().getImporter().getPath().getLastUsedPath()));
			} else {
				chooser.setCurrentDirectory(new File(config.getProject().getImporter().getPath().getStandardPath()));
			}
		} else
			chooser.setCurrentDirectory(new File(fileListModel.get(0).toString()));

		int result = chooser.showOpenDialog(getTopLevelAncestor());
		if (result == JFileChooser.CANCEL_OPTION) 
			return;

		fileListModel.clear();
		for (File file : chooser.getSelectedFiles())
			fileListModel.addElement(file.toString());

		config.getProject().getImporter().getPath().setLastUsedPath(chooser.getCurrentDirectory().getAbsolutePath());
	}

	@Override
	public void dragEnter(DropTargetDragEvent dtde) {
		dtde.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
	}

	@Override
	public void dragExit(DropTargetEvent dte) {
		// nothing to do here
	}

	@Override
	public void dragOver(DropTargetDragEvent dtde) {
		// nothing to do here
	}

	@SuppressWarnings("unchecked")
	@Override
	public void drop(DropTargetDropEvent dtde) {
		for (DataFlavor dataFlover : dtde.getCurrentDataFlavors()) {
			if (dataFlover.isFlavorJavaFileListType()) {
				try {
					dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);

					List<String> fileNames = new ArrayList<String>();
					for (File file : (List<File>)dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor))
						if (file.canRead())
							fileNames.add(file.getCanonicalPath());

					if (!fileNames.isEmpty()) {
						if (dtde.getDropAction() != DnDConstants.ACTION_COPY)
							fileListModel.clear();

						for (String fileName : fileNames)
							fileListModel.addElement(fileName); 

						config.getProject().getImporter().getPath().setLastUsedPath(
								new File(fileListModel.getElementAt(fileListModel.size() - 1).toString()).getAbsolutePath());
					}

					dtde.getDropTargetContext().dropComplete(true);	
				} catch (UnsupportedFlavorException e1) {
					//
				} catch (IOException e2) {
					//
				}
			}
		}
	}

	@Override
	public void dropActionChanged(DropTargetDragEvent dtde) {
		// nothing to do here
	}

	private final class RemoveAction extends AbstractAction {
		public void actionPerformed(ActionEvent e) {
			if (fileList.getSelectedIndices().length > 0) {
				int[] selectedIndices = fileList.getSelectedIndices();
				int firstSelected = selectedIndices[0];		

				for (int i = selectedIndices.length - 1; i >= 0; --i) 
					fileListModel.removeElementAt(selectedIndices[i]);

				if (firstSelected > fileListModel.size() - 1)
					firstSelected = fileListModel.size() - 1;

				fileList.setSelectedIndex(firstSelected);
			}
		}		
	}
}