/*
 *  Copyright (C) 2010-2013 Axel Morgner, structr <structr@structr.org>
 * 
 *  This file is part of structr <http://structr.org>.
 * 
 *  structr is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 * 
 *  structr is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 *  You should have received a copy of the GNU General Public License
 *  along with structr.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.structr.common.error;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import javax.servlet.http.HttpServletResponse;
import org.structr.core.property.PropertyKey;

/**
 * Indicates that a property is read-only.
 *
 * @author Christian Morgner
 */
public class ReadOnlyPropertyToken extends ErrorToken {

	public ReadOnlyPropertyToken(PropertyKey propertyName) {
		super(HttpServletResponse.SC_FORBIDDEN, propertyName);
	}

	@Override
	public JsonElement getContent() {
		return new JsonPrimitive(getErrorToken());
	}

	@Override
	public String getErrorToken() {
		return "is_read_only_property";
	}
}
