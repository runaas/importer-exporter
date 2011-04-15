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
package de.tub.citydb.components.citygml.importer.controller;

import java.io.File;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.bind.ValidationEvent;
import javax.xml.bind.ValidationEventHandler;
import javax.xml.parsers.SAXParserFactory;

import org.citygml4j.builder.jaxb.JAXBBuilder;
import org.citygml4j.builder.jaxb.xml.io.reader.CityGMLChunk;
import org.citygml4j.builder.jaxb.xml.io.reader.JAXBChunkReader;
import org.citygml4j.model.citygml.CityGML;
import org.citygml4j.model.citygml.CityGMLClass;
import org.citygml4j.model.gml.GMLClass;
import org.citygml4j.xml.io.CityGMLInputFactory;
import org.citygml4j.xml.io.reader.CityGMLReadException;
import org.citygml4j.xml.io.reader.FeatureReadMode;

import de.tub.citydb.api.concurrent.WorkerPool;
import de.tub.citydb.api.event.Event;
import de.tub.citydb.api.event.EventDispatcher;
import de.tub.citydb.api.event.EventHandler;
import de.tub.citydb.api.log.LogLevelType;
import de.tub.citydb.api.log.Logger;
import de.tub.citydb.components.citygml.common.database.cache.CacheManager;
import de.tub.citydb.components.citygml.common.database.cache.model.CacheTableModelEnum;
import de.tub.citydb.components.citygml.common.database.gmlid.DBGmlIdLookupServerEnum;
import de.tub.citydb.components.citygml.common.database.gmlid.DBGmlIdLookupServerManager;
import de.tub.citydb.components.citygml.common.database.xlink.DBXlink;
import de.tub.citydb.components.citygml.common.io.InputFileHandler;
import de.tub.citydb.components.citygml.importer.concurrent.DBImportWorkerFactory;
import de.tub.citydb.components.citygml.importer.concurrent.DBImportXlinkResolverWorkerFactory;
import de.tub.citydb.components.citygml.importer.concurrent.DBImportXlinkWorkerFactory;
import de.tub.citydb.components.citygml.importer.concurrent.FeatureReaderWorkerFactory;
import de.tub.citydb.components.citygml.importer.database.content.AffineTransformer;
import de.tub.citydb.components.citygml.importer.database.gmlid.DBImportCache;
import de.tub.citydb.components.citygml.importer.database.xlink.resolver.DBXlinkSplitter;
import de.tub.citydb.components.common.event.CounterEvent;
import de.tub.citydb.components.common.event.CounterType;
import de.tub.citydb.components.common.event.EventType;
import de.tub.citydb.components.common.event.FeatureCounterEvent;
import de.tub.citydb.components.common.event.GeometryCounterEvent;
import de.tub.citydb.components.common.event.InterruptEvent;
import de.tub.citydb.components.common.event.StatusDialogMessage;
import de.tub.citydb.components.common.event.StatusDialogProgressBar;
import de.tub.citydb.components.common.event.StatusDialogTitle;
import de.tub.citydb.components.common.filter.FilterMode;
import de.tub.citydb.components.common.filter.ImportFilter;
import de.tub.citydb.components.common.filter.statistic.FeatureCounterFilter;
import de.tub.citydb.config.Config;
import de.tub.citydb.config.internal.Internal;
import de.tub.citydb.config.project.database.Database;
import de.tub.citydb.config.project.database.Index;
import de.tub.citydb.config.project.database.Workspace;
import de.tub.citydb.config.project.general.AffineTransformation;
import de.tub.citydb.config.project.importer.ImportGmlId;
import de.tub.citydb.config.project.importer.XMLValidation;
import de.tub.citydb.database.DBConnectionPool;
import de.tub.citydb.util.DBUtil;

public class Importer implements EventHandler {
	private final Logger LOG = Logger.getInstance();

	private final JAXBBuilder jaxbBuilder;
	private final DBConnectionPool dbPool;
	private final Config config;
	private final EventDispatcher eventDispatcher;

	private WorkerPool<CityGML> dbWorkerPool;
	private WorkerPool<CityGMLChunk> featureWorkerPool;
	private WorkerPool<DBXlink> tmpXlinkPool;
	private WorkerPool<DBXlink> xlinkResolverPool;
	private CacheManager cacheManager;
	private DBXlinkSplitter tmpSplitter;
	private SAXParserFactory factory;

	private volatile boolean shouldRun = true;
	private AtomicBoolean isInterrupted = new AtomicBoolean(false);
	private EnumMap<CityGMLClass, Long> featureCounterMap;
	private EnumMap<GMLClass, Long> geometryCounterMap;
	private long xmlValidationErrorCounter;
	private DBGmlIdLookupServerManager lookupServerManager;

	private int runState;
	private final int PARSING = 1;
	private final int XLINK_RESOLVING = 2;

	public Importer(JAXBBuilder jaxbBuilder, 
			DBConnectionPool dbPool, 
			Config config, 
			EventDispatcher eventDispatcher) {
		this.jaxbBuilder = jaxbBuilder;
		this.dbPool = dbPool;
		this.config = config;
		this.eventDispatcher = eventDispatcher;

		factory = SAXParserFactory.newInstance();
		factory.setNamespaceAware(true);

		featureCounterMap = new EnumMap<CityGMLClass, Long>(CityGMLClass.class);
		geometryCounterMap = new EnumMap<GMLClass, Long>(GMLClass.class);
	}

	public boolean doProcess() {
		// adding listeners
		eventDispatcher.addEventHandler(EventType.FEATURE_COUNTER, this);
		eventDispatcher.addEventHandler(EventType.GEOMETRY_COUNTER, this);
		eventDispatcher.addEventHandler(EventType.INTERRUPT, this);

		// get config shortcuts
		de.tub.citydb.config.project.system.System system = config.getProject().getImporter().getSystem();
		Database database = config.getProject().getDatabase();
		Index index = database.getIndexes();
		Internal intConfig = config.getInternal();
		ImportGmlId gmlId = config.getProject().getImporter().getGmlId();

		// worker pool settings 
		int minThreads = system.getThreadPool().getDefaultPool().getMinThreads();
		int maxThreads = system.getThreadPool().getDefaultPool().getMaxThreads();
		int queueSize = maxThreads * 2;

		// gml:id lookup cache update
		int lookupCacheBatchSize = database.getUpdateBatching().getGmlIdLookupServerBatchValue();

		// checking workspace... this should be improved in future...
		Workspace workspace = database.getWorkspaces().getImportWorkspace();
		if (shouldRun && !workspace.getName().toUpperCase().equals("LIVE")) {
			boolean workspaceExists = dbPool.existsWorkspace(workspace);

			if (!workspaceExists) {
				LOG.error("Database workspace '" + workspace.getName().trim() + "' is not available.");
				return false;
			} else {
				LOG.info("Switching to database workspace '" + workspace.getName().trim() + "'.");
			}
		}

		// deactivate database indexes
		if (shouldRun && (index.isSpatialIndexModeDeactivate() || index.isSpatialIndexModeDeactivateActivate() ||
				index.isNormalIndexModeDeactivate() || index.isNormalIndexModeDeactivateActivate())) {
			try {
				if (shouldRun && (index.isSpatialIndexModeDeactivate() || index.isSpatialIndexModeDeactivateActivate())) {
					LOG.info("Deactivating spatial indexes...");
					String[] result = DBUtil.dropSpatialIndexes();

					if (result != null) {
						for (String line : result) {
							String[] parts = line.split(":");

							if (!parts[4].equals("DROPPED")) {
								LOG.error("FAILED: " + parts[0] + " auf " + parts[1] + "(" + parts[2] + ")");
								String errMsg = DBUtil.errorMessage(parts[3]);
								LOG.error("Error cause: " + errMsg);
							}
						}
					}
				}

				if (shouldRun && (index.isNormalIndexModeDeactivate() || index.isNormalIndexModeDeactivateActivate())) {
					LOG.info("Deactivating normal indexes...");
					String[] result = DBUtil.dropNormalIndexes();

					if (result != null) {
						for (String line : result) {
							String[] parts = line.split(":");

							if (!parts[4].equals("DROPPED")) {
								LOG.error("FAILED: " + parts[0] + " auf " + parts[1] + "(" + parts[2] + ")");
								String errMsg = DBUtil.errorMessage(parts[3]);
								LOG.error("Error cause: " + errMsg);
							}
						}
					}
				}

			} catch (SQLException e) {
				LOG.error("Database error while deactivating indexes: " + e.getMessage());
				return false;
			}			
		}

		// build list of import files
		LOG.info("Creating list of CityGML files to be imported...");	
		InputFileHandler fileHandler = new InputFileHandler(eventDispatcher);
		List<File> importFiles = fileHandler.getFiles(intConfig.getImportFileName().trim().split("\n"));

		if (!shouldRun)
			return true;

		if (importFiles.size() == 0) {
			LOG.warn("Failed to find CityGML files at the specified locations.");
			return false;
		}

		int fileCounter = 0;
		int remainingFiles = importFiles.size();
		LOG.info("List of import files successfully created.");
		LOG.info(remainingFiles + " file(s) will be imported.");

		// import filter
		ImportFilter importFilter = new ImportFilter(config);
		
		// prepare CityGML input factory
		CityGMLInputFactory in = null;
		try {
			in = jaxbBuilder.createCityGMLInputFactory();
			in.setProperty(CityGMLInputFactory.FEATURE_READ_MODE, FeatureReadMode.SPLIT_PER_COLLECTION_MEMBER);
			in.setProperty(CityGMLInputFactory.FAIL_ON_MISSING_ADE_SCHEMA, false);
			in.setProperty(CityGMLInputFactory.PARSE_SCHEMA, false);
		} catch (CityGMLReadException e) {
			LOG.error("Failed to initialize CityGML parser. Aborting.");
			return false;
		}

		// prepare XML validation 
		XMLValidation xmlValidation = config.getProject().getImporter().getXMLValidation();
		if (xmlValidation.isSetUseXMLValidation()) {			
			LOG.info("Using XML validation during database import.");

			in.setProperty(CityGMLInputFactory.USE_VALIDATION, true);
			in.setProperty(CityGMLInputFactory.PARSE_SCHEMA, true);

			ValidationHandler validationHandler = new ValidationHandler();
			validationHandler.allErrors = !xmlValidation.isSetReportOneErrorPerFeature();
			in.setValidationEventHandler(validationHandler);
		}
		
		// affine transformation
		AffineTransformation affineTransformation = config.getProject().getImporter().getAffineTransformation();
		if (affineTransformation.isSetUseAffineTransformation()) {
			LOG.info("Applying affine coordinates transformation.");
			
			try {
				intConfig.setAffineTransformer(new AffineTransformer(config));
			} catch (Exception e) {
				LOG.error("The homogeneous transformation matrix is singular.");
				return false;
			}
		}

		// prepare counter filter
		FeatureCounterFilter counterFilter = new FeatureCounterFilter(config, FilterMode.IMPORT);
		Long counterFirstElement = counterFilter.getFilterState().get(0);
		Long counterLastElement = counterFilter.getFilterState().get(1);
		long elementCounter = 0;

		while (shouldRun && fileCounter < importFiles.size()) {
			try {
				runState = PARSING;

				File file = importFiles.get(fileCounter++);
				intConfig.setImportPath(file.getParent());
				intConfig.setCurrentImportFileName(file.getAbsolutePath());

				eventDispatcher.triggerEvent(new StatusDialogTitle(file.getName(), this));
				eventDispatcher.triggerEvent(new StatusDialogMessage(Internal.I18N.getString("import.dialog.cityObj.msg"), this));
				eventDispatcher.triggerEvent(new StatusDialogProgressBar(true, this));
				eventDispatcher.triggerEvent(new CounterEvent(CounterType.FILE, --remainingFiles, this));

				// set gml:id codespace
				if (gmlId.isSetRelativeCodeSpaceMode())
					intConfig.setCurrentGmlIdCodespace(file.getName());
				else if (gmlId.isSetAbsoluteCodeSpaceMode())
					intConfig.setCurrentGmlIdCodespace(file.toString());
				else if (gmlId.isSetUserCodeSpaceMode())
					intConfig.setCurrentGmlIdCodespace(gmlId.getCodeSpace());
				else if (!gmlId.isSetUserCodeSpaceMode())
					intConfig.setCurrentGmlIdCodespace(null);

				// create instance of temp table manager
				cacheManager = new CacheManager(dbPool, maxThreads);

				// create instance of gml:id lookup server manager...
				lookupServerManager = new DBGmlIdLookupServerManager();

				// ...and start servers
				try {
					lookupServerManager.initServer(
							DBGmlIdLookupServerEnum.GEOMETRY,
							new DBImportCache(cacheManager, 
									CacheTableModelEnum.GMLID_GEOMETRY, 
									system.getGmlIdLookupServer().getGeometry().getPartitions(), 
									lookupCacheBatchSize),
									system.getGmlIdLookupServer().getGeometry().getCacheSize(),
									system.getGmlIdLookupServer().getGeometry().getPageFactor(),
									maxThreads);

					lookupServerManager.initServer(
							DBGmlIdLookupServerEnum.FEATURE,
							new DBImportCache(cacheManager, 
									CacheTableModelEnum.GMLID_FEATURE, 
									system.getGmlIdLookupServer().getFeature().getPartitions(),
									lookupCacheBatchSize),
									system.getGmlIdLookupServer().getFeature().getCacheSize(),
									system.getGmlIdLookupServer().getFeature().getPageFactor(),
									maxThreads);
				} catch (SQLException sqlEx) {
					LOG.error("SQL error while initializing database import: " + sqlEx.getMessage());
					continue;
				}

				// creating worker pools needed for data import
				// this pool is for registering xlinks
				tmpXlinkPool = new WorkerPool<DBXlink>(
						minThreads,
						maxThreads,
						new DBImportXlinkWorkerFactory(cacheManager, config, eventDispatcher),
						queueSize,
						false);

				// this pool basically works on the data import
				dbWorkerPool = new WorkerPool<CityGML>(
						minThreads,
						maxThreads,
						new DBImportWorkerFactory(dbPool, 
								tmpXlinkPool, 
								lookupServerManager, 
								importFilter,
								config, 
								eventDispatcher),
								queueSize,
								false);

				// this worker pool parses the xml file and passes xml chunks to the dbworker pool
				featureWorkerPool = new WorkerPool<CityGMLChunk>(
						minThreads,
						maxThreads,
						new FeatureReaderWorkerFactory(dbWorkerPool, config, eventDispatcher),
						queueSize,
						false);

				// prestart threads
				tmpXlinkPool.prestartCoreWorkers();
				dbWorkerPool.prestartCoreWorkers();
				featureWorkerPool.prestartCoreWorkers();

				// ok, preparation done. inform user and  start parsing the input file
				JAXBChunkReader reader = null;
				try {
					reader = (JAXBChunkReader)in.createCityGMLReader(file);	
					LOG.info("Importing file: " + file.toString());						

					while (shouldRun && reader.hasNextChunk()) {
						CityGMLChunk chunk = reader.nextChunk();

						if (counterFilter.isActive()) {
							elementCounter++;
							
							if (counterFirstElement != null && elementCounter < counterFirstElement)
								continue;

							if (counterLastElement != null && elementCounter > counterLastElement)					
								break;							
						}

						featureWorkerPool.addWork(chunk);
					}					
				} catch (CityGMLReadException e) {
					LOG.error("Fatal CityGML parser error: " + e.getCause().getMessage());
					return false;
				}

				// we are done with parsing. so shutdown the workers
				// xlink pool is not shutdown because we need it afterwards
				try {
					featureWorkerPool.shutdownAndWait();
				} catch (InterruptedException ie) {
					//
				}

				try {
					reader.close();
				} catch (CityGMLReadException e) {
					//
				}

				try {
					dbWorkerPool.shutdownAndWait();
					tmpXlinkPool.join();
				} catch (InterruptedException ie) {
					//
				}

				if (shouldRun) {
					runState = XLINK_RESOLVING;

					// get an xlink resolver pool
					LOG.info("Resolving XLink references.");
					xlinkResolverPool = new WorkerPool<DBXlink>(
							minThreads,
							maxThreads,
							new DBImportXlinkResolverWorkerFactory(dbPool, 
									tmpXlinkPool, 
									lookupServerManager, 
									cacheManager, 
									importFilter,
									config, 
									eventDispatcher),
									queueSize,
									false);

					// and prestart its workers
					xlinkResolverPool.prestartCoreWorkers();

					// we also need a splitter which extracts the data from the temp tables
					tmpSplitter = new DBXlinkSplitter(cacheManager, 
							xlinkResolverPool, 
							tmpXlinkPool,
							eventDispatcher);

					// resolve xlinks
					try {
						if (shouldRun)
							tmpSplitter.startQuery();
					} catch (SQLException sqlE) {
						LOG.error("SQL error: " + sqlE.getMessage());
					}

					// shutdown worker pools
					try {
						xlinkResolverPool.shutdownAndWait();
						tmpXlinkPool.shutdownAndWait();
					} catch (InterruptedException iE) {
						//
					}
				} else {
					// at least shutdown tmp xlink pool
					try {
						tmpXlinkPool.shutdownAndWait();
					} catch (InterruptedException iE) {
						//
					}
				}

				eventDispatcher.triggerEvent(new StatusDialogMessage(Internal.I18N.getString("import.dialog.finish.msg"), this));
				eventDispatcher.triggerEvent(new StatusDialogProgressBar(true, this));

				// finally clean up and join eventDispatcher
				try {
					LOG.info("Cleaning temporary cache.");
					cacheManager.dropAll();
				} catch (SQLException sqlE) {
					LOG.error("SQL error: " + sqlE.getMessage());
				}

				try {
					lookupServerManager.shutdownAll();
				} catch (SQLException e) {
					LOG.error("SQL error: " + e.getMessage());
				}

				try {
					eventDispatcher.flushEvents();
				} catch (InterruptedException e) {
					// 
				}

				// show XML validation errors
				if (xmlValidation.isSetUseXMLValidation() && xmlValidationErrorCounter > 0)
					LOG.warn(xmlValidationErrorCounter + " error(s) encountered while validating the document.");

				xmlValidationErrorCounter = 0;
			} finally {
				// clean up
				if (featureWorkerPool != null && !featureWorkerPool.isTerminated())
					featureWorkerPool.shutdownNow();

				if (dbWorkerPool != null && !dbWorkerPool.isTerminated())
					dbWorkerPool.shutdownNow();

				if (tmpXlinkPool != null && !tmpXlinkPool.isTerminated())
					tmpXlinkPool.shutdownNow();

				if (xlinkResolverPool != null && !xlinkResolverPool.isTerminated())
					xlinkResolverPool.shutdownNow();

				// set to null
				cacheManager = null;
				lookupServerManager = null;
				tmpXlinkPool = null;
				dbWorkerPool = null;
				featureWorkerPool = null;
				xlinkResolverPool = null;
				tmpSplitter = null;
			}
		} 	

		// reactivate database indexes
		if (shouldRun) {
			if (index.isSpatialIndexModeDeactivateActivate() || index.isNormalIndexModeDeactivateActivate()) {
				try {
					if (index.isSpatialIndexModeDeactivateActivate()) {
						LOG.info("Activating spatial indexes. This can take long time...");
						String[] result = DBUtil.createSpatialIndexes();

						if (result != null) {
							for (String line : result) {
								String[] parts = line.split(":");

								if (!parts[4].equals("VALID")) {
									LOG.error("FAILED: " + parts[0] + " auf " + parts[1] + "(" + parts[2] + ")");
									String errMsg = DBUtil.errorMessage(parts[3]);
									LOG.error("Error cause: " + errMsg);
								}
							}
						}
					}

					if (index.isNormalIndexModeDeactivateActivate()) {
						LOG.info("Activating normal indexes. This can take long time...");
						String[] result = DBUtil.createNormalIndexes();

						if (result != null) {
							for (String line : result) {
								String[] parts = line.split(":");

								if (!parts[4].equals("VALID")) {
									LOG.error("FAILED: " + parts[0] + " auf " + parts[1] + "(" + parts[2] + ")");
									String errMsg = DBUtil.errorMessage(parts[3]);
									LOG.error("Error cause: " + errMsg);
								}
							}
						}
					}

				} catch (SQLException e) {
					LOG.error("Database error while activating indexes: " + e.getMessage());
					return false;
				}
			}
		}

		// show imported features
		if (!featureCounterMap.isEmpty()) {
			LOG.info("Imported CityGML features:");
			for (CityGMLClass type : featureCounterMap.keySet())
				LOG.info(type + ": " + featureCounterMap.get(type));
		}

		long geometryObjects = 0;
		for (GMLClass type : geometryCounterMap.keySet())
			geometryObjects += geometryCounterMap.get(type);

		if (geometryObjects != 0)
			LOG.info("Processed geometry objects: " + geometryObjects);

		// cleaning temp cache
		if (cacheManager != null) {
			try {
				LOG.info("Cleaning temporary cache.");
				cacheManager.dropAll();
				cacheManager = null;
			} catch (SQLException sqlEx) {
				LOG.error("SQL error while finishing database import: " + sqlEx.getMessage());
			}
		}

		return shouldRun;
	}

	// react on events we are receiving via the eventDispatcher
	@Override
	public void handleEvent(Event e) throws Exception {
		if (e.getEventType() == EventType.FEATURE_COUNTER) {
			HashMap<CityGMLClass, Long> counterMap = ((FeatureCounterEvent)e).getCounter();

			for (CityGMLClass type : counterMap.keySet()) {
				Long counter = featureCounterMap.get(type);
				Long update = counterMap.get(type);

				if (counter == null)
					featureCounterMap.put(type, update);
				else
					featureCounterMap.put(type, counter + update);
			}
		}

		else if (e.getEventType() == EventType.GEOMETRY_COUNTER) {
			HashMap<GMLClass, Long> counterMap = ((GeometryCounterEvent)e).getCounter();

			for (GMLClass type : counterMap.keySet()) {
				Long counter = geometryCounterMap.get(type);
				Long update = counterMap.get(type);

				if (counter == null)
					geometryCounterMap.put(type, update);
				else
					geometryCounterMap.put(type, counter + update);
			}
		}

		else if (e.getEventType() == EventType.INTERRUPT) {
			if (isInterrupted.compareAndSet(false, true)) {
				switch (((InterruptEvent)e).getInterruptType()) {
				case ADE_SCHEMA_READ_ERROR:
				case USER_ABORT:
					shouldRun = false;
					break;
				}

				String log = ((InterruptEvent)e).getLogMessage();
				if (log != null)
					LOG.log(((InterruptEvent)e).getLogLevelType(), log);

				if (runState ==  XLINK_RESOLVING && tmpSplitter != null)
					tmpSplitter.shutdown();
			}
		}
	}

	private final class ValidationHandler implements ValidationEventHandler {
		boolean allErrors = false;

		@Override
		public boolean handleEvent(ValidationEvent event) {
			if (!event.getMessage().startsWith("cvc"))
				return true;

			StringBuilder msg = new StringBuilder();
			LogLevelType type;

			switch (event.getSeverity()) {
			case ValidationEvent.FATAL_ERROR:
			case ValidationEvent.ERROR:
				msg.append("Invalid content");
				type = LogLevelType.ERROR;
				break;
			case ValidationEvent.WARNING:
				msg.append("Warning");
				type = LogLevelType.WARN;
				break;
			default:
				return allErrors;
			}

			if (event.getLocator() != null) {
				msg.append(" at [")
				.append(event.getLocator().getLineNumber())
				.append(", ")
				.append(event.getLocator().getColumnNumber())
				.append("]");
			}

			msg.append(": ");
			msg.append(event.getMessage());
			LOG.log(type, msg.toString());

			xmlValidationErrorCounter++;
			return allErrors;
		}

	}

}
