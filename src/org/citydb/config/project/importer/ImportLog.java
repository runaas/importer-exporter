/*
 * 3D City Database - The Open Source CityGML Database
 * http://www.3dcitydb.org/
 * 
 * (C) 2013 - 2015,
 * Chair of Geoinformatics,
 * Technische Universitaet Muenchen, Germany
 * http://www.gis.bgu.tum.de/
 * 
 * The 3D City Database is jointly developed with the following
 * cooperation partners:
 * 
 * virtualcitySYSTEMS GmbH, Berlin <http://www.virtualcitysystems.de/>
 * M.O.S.S. Computer Grafik Systeme GmbH, Muenchen <http://www.moss.de/>
 * 
 * The 3D City Database Importer/Exporter program is free software:
 * you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 */
package org.citydb.config.project.importer;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

@XmlType(name="ImportLogType", propOrder={
		"logImportedFeatures",
		"logPath"
})
public class ImportLog {
	@XmlElement(required=true, defaultValue="false")
	private Boolean logImportedFeatures = false;
	private String logPath;
	
	public boolean isSetLogImportedFeatures() {
		if (logImportedFeatures != null)
			return logImportedFeatures.booleanValue();

		return false;
	}

	public Boolean getLogImportedFeatures() {
		return logImportedFeatures;
	}

	public void setLogImportedFeatures(Boolean logImportedFeatures) {
		this.logImportedFeatures = logImportedFeatures;
	}
	
	public boolean isSetLogPath() {
		return logPath != null;
	}

	public String getLogPath() {
		return logPath;
	}

	public void setLogPath(String logPath) {
		if (logPath != null && !logPath.isEmpty())
			this.logPath = logPath;
	}
	
}