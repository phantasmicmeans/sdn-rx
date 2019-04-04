/*
 * Copyright (c) 2019 "Neo4j,"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.neo4j.core.schema;

import org.apiguardian.api.API;

/**
 * @author Michael J. Simons
 */
@API(status = API.Status.INTERNAL, since = "1.0")
public final class PropertyDescription {

	private final String fieldName;

	private final String propertyName;

	// TODO basically all the properties from Springs PersistentProperty are needed.
	// Two options: Turn that classes ending in XXXDescription into interfaces and let Neo4jPersistentProperty extend
	// from it as well, or copy needed stuff into a format that fits our needs best.

	public PropertyDescription(String fieldName, String propertyName) {
		this.fieldName = fieldName;
		this.propertyName = propertyName;
	}

	public String getFieldName() {
		return fieldName;
	}

	public String getPropertyName() {
		return propertyName;
	}
}