/*
 * This file is part of the 3D City Database Importer/Exporter.
 * Copyright (c) 2007 - 2013
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
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
package org.citydb.modules.citygml.exporter.database.content;

public enum DBExporterEnum {
	SURFACE_GEOMETRY,
	IMPLICIT_GEOMETRY,
	CITYOBJECT,
	CITYOBJECT_GENERICATTRIB,
	BUILDING,
	ROOM,
	BUILDING_FURNITURE,
	BUILDING_INSTALLATION,
	THEMATIC_SURFACE,
	BRIDGE,
	BRIDGE_THEMATIC_SURFACE,
	BRIDGE_CONSTR_ELEMENT,
	BRIDGE_INSTALLATION,
	BRIDGE_ROOM,
	BRIDGE_FURNITURE,
	TUNNEL,
	TUNNEL_THEMATIC_SURFACE,
	TUNNEL_INSTALLATION,
	TUNNEL_HOLLOW_SPACE,
	TUNNEL_FURNITURE,
	CITY_FURNITURE,
	LAND_USE,
	WATERBODY,
	PLANT_COVER,
	TRANSPORTATION_COMPLEX,
	SOLITARY_VEGETAT_OBJECT,
	RELIEF_FEATURE,
	LOCAL_APPEARANCE,
	GLOBAL_APPEARANCE,
	LOCAL_APPEARANCE_TEXTUREPARAM,
	GLOBAL_APPEARANCE_TEXTUREPARAM,
	GENERIC_CITYOBJECT,
	CITYOBJECTGROUP,
	GENERALIZATION,
	OTHER_GEOMETRY,
}