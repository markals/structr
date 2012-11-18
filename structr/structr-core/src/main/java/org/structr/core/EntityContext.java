/*
 *  Copyright (C) 2010-2012 Axel Morgner
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



package org.structr.core;

import java.lang.reflect.Field;
import org.structr.core.converter.PropertyConverter;
import org.apache.commons.lang.StringUtils;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.event.PropertyEntry;
import org.neo4j.graphdb.event.TransactionData;
import org.neo4j.graphdb.event.TransactionEventHandler;

import org.structr.common.CaseHelper;
import org.structr.core.property.PropertyKey;
import org.structr.common.SecurityContext;
import org.structr.common.error.ErrorBuffer;
import org.structr.common.error.FrameworkException;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.AbstractRelationship;
import org.structr.core.entity.RelationClass;
import org.structr.core.entity.RelationClass.Cardinality;
import org.structr.core.entity.RelationshipMapping;
import org.structr.core.node.*;
import org.structr.core.node.IndexNodeCommand;
import org.structr.core.node.IndexRelationshipCommand;
import org.structr.core.node.NodeFactory;
import org.structr.core.node.RelationshipFactory;
import org.structr.core.notion.Notion;
import org.structr.core.notion.ObjectNotion;

//~--- JDK imports ------------------------------------------------------------

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.neo4j.graphdb.DynamicRelationshipType;
import org.structr.common.*;
import org.structr.common.property.GenericProperty;
import org.structr.common.property.PropertyMap;
import org.structr.core.module.ModuleService;
import org.structr.core.node.NodeService.NodeIndex;
import org.structr.core.node.NodeService.RelationshipIndex;

//~--- classes ----------------------------------------------------------------

/**
 * A global context for functional mappings between nodes and
 * relationships, property views and property validators.
 *
 * @author Axel Morgner
 * @author Christian Morgner
 */
public class EntityContext {

	private static final String COMBINED_RELATIONSHIP_KEY_SEP                                     = " ";
	private static final Logger logger                                                            = Logger.getLogger(EntityContext.class.getName());
//	private static final Map<Class, Set<PropertyKey>> globalWriteOncePropertyMap                  = new LinkedHashMap<Class, Set<PropertyKey>>();
	private static final Map<Class, Map<PropertyKey, Set<PropertyValidator>>> globalValidatorMap  = new LinkedHashMap<Class, Map<PropertyKey, Set<PropertyValidator>>>();
	private static final Map<Class, Map<String, Set<PropertyKey>>> globalSearchablePropertyMap    = new LinkedHashMap<Class, Map<String, Set<PropertyKey>>>();

	// This map contains a mapping from (sourceType, destType) -> RelationClass
	private static final Map<Class, Map<Class, RelationClass>> globalRelationClassMap      = new LinkedHashMap<Class, Map<Class, RelationClass>>();
	private static final Map<Class, Set<PropertyKey>> globalReadOnlyPropertyMap            = new LinkedHashMap<Class, Set<PropertyKey>>();
	private static final Map<Class, Map<String, Set<PropertyKey>>> globalPropertyViewMap   = new LinkedHashMap<Class, Map<String, Set<PropertyKey>>>();
	private static final Map<Class, Map<String, PropertyKey>> globalClassDBNamePropertyMap = new LinkedHashMap<Class, Map<String, PropertyKey>>();
	private static final Map<Class, Map<String, PropertyKey>> globalClassJSNamePropertyMap = new LinkedHashMap<Class, Map<String, PropertyKey>>();

	// This map contains a mapping from (sourceType, propertyKey) -> RelationClass
	private static final Map<Class, Map<PropertyKey, Class<? extends PropertyConverter>>> globalAggregatedPropertyConverterMap = new LinkedHashMap<Class, Map<PropertyKey, Class<? extends PropertyConverter>>>();
	private static final Map<Class, Map<PropertyKey, Class<? extends PropertyConverter>>> globalPropertyConverterMap           = new LinkedHashMap<Class, Map<PropertyKey, Class<? extends PropertyConverter>>>();
	private static final Map<Class, Map<PropertyKey, RelationClass>> globalPropertyRelationClassMap                            = new LinkedHashMap<Class, Map<PropertyKey, RelationClass>>();
	private static final Map<Class, Map<String, PropertyGroup>> globalAggregatedPropertyGroupMap                               = new LinkedHashMap<Class, Map<String, PropertyGroup>>();
	private static final Map<Class, Map<String, PropertyGroup>> globalPropertyGroupMap                                         = new LinkedHashMap<Class, Map<String, PropertyGroup>>();

	// This map contains view-dependent result set transformations
	private static final Map<Class, Map<String, ViewTransformation>> viewTransformations = new LinkedHashMap<Class, Map<String, ViewTransformation>>();
	
	// This set contains all known properties
	private static final Set<PropertyKey> globalKnownPropertyKeys                                           = new LinkedHashSet<PropertyKey>();
	private static final Map<Class, Set<Transformation<GraphObject>>> globalEntityCreationTransformationMap = new LinkedHashMap<Class, Set<Transformation<GraphObject>>>();
	private static final Map<Class, Map<PropertyKey, Value>> globalConversionParameterMap                   = new LinkedHashMap<Class, Map<PropertyKey, Value>>();
	private static final Map<String, String> normalizedEntityNameCache                                      = new LinkedHashMap<String, String>();
	private static final Set<StructrTransactionListener> transactionListeners                               = new LinkedHashSet<StructrTransactionListener>();
	private static final Map<String, RelationshipMapping> globalRelationshipNameMap                         = new LinkedHashMap<String, RelationshipMapping>();
	private static final Map<String, Class> globalRelationshipClassMap                                      = new LinkedHashMap<String, Class>();
	private static final EntityContextModificationListener globalModificationListener                       = new EntityContextModificationListener();
	private static final Map<Long, FrameworkException> exceptionMap                                         = new LinkedHashMap<Long, FrameworkException>();
	private static final Map<Class, Set<Class>> interfaceMap                                                = new LinkedHashMap<Class, Set<Class>>();
	private static final Map<String, Class> reverseInterfaceMap                                             = new LinkedHashMap<String, Class>();
	private static Map<String, Class> cachedEntities                                                        = new LinkedHashMap<String, Class>();

	private static final Map<Long, TransactionChangeSet> globalChangeSets                                   = new ConcurrentHashMap<Long, TransactionChangeSet>();
	private static final ThreadLocal<SecurityContext> securityContextMap                                    = new ThreadLocal<SecurityContext>();
	private static final ThreadLocal<Long> transactionKeyMap                                                = new ThreadLocal<Long>();
//	private static final ThreadLocalChangeSet globalChangeSet                                               = new ThreadLocalChangeSet();
	private static GenericFactory genericFactory                                                            = new DefaultGenericFactory();

	//~--- methods --------------------------------------------------------

	/**
	 * Initialize the entity context for the given class.
	 * This method sets defaults common for any class, f.e. registers any of its parent properties.
	 *
	 * @param type
	 */
	public static void init(Class type) {

		// 1. Register searchable keys of superclasses
		for (Enum index : NodeService.NodeIndex.values()) {

			String indexName                                           = index.name();
			Map<String, Set<PropertyKey>> searchablePropertyMapForType = getSearchablePropertyMapForType(type);
			Set<PropertyKey> searchablePropertySet                     = searchablePropertyMapForType.get(indexName);

			if (searchablePropertySet == null) {

				searchablePropertySet = new LinkedHashSet<PropertyKey>();

				searchablePropertyMapForType.put(indexName, searchablePropertySet);

			}

			Class localType = type.getSuperclass();

			while ((localType != null) &&!localType.equals(Object.class)) {

				Set<PropertyKey> superProperties = getSearchableProperties(localType, indexName);
				searchablePropertySet.addAll(superProperties);

				// include property sets from interfaces
				for(Class interfaceClass : getInterfacesForType(localType)) {
					searchablePropertySet.addAll(getSearchableProperties(interfaceClass, indexName));
				}

				// one level up :)
				localType = localType.getSuperclass();

			}
		}
	}

	public static void scanEntity(Object entity) {
		
		Map<Field, PropertyKey> allProperties = getFieldValuesOfType(PropertyKey.class, entity);
		Map<Field, View> views                = getFieldValuesOfType(View.class, entity);
		Class entityType                      = entity.getClass();

		for (Entry<Field, PropertyKey> entry : allProperties.entrySet()) {

			PropertyKey propertyKey = entry.getValue();
			Field field             = entry.getKey();
			Class declaringClass    = field.getDeclaringClass();

			if (declaringClass != null) {
				
				propertyKey.setDeclaringClassName(declaringClass.getSimpleName());
				registerProperty(declaringClass, propertyKey);
			}
			
			registerProperty(entityType, propertyKey);
		}
		
		for (Entry<Field, View> entry : views.entrySet()) {
			
			Field field = entry.getKey();
			View view   = entry.getValue();

			for (PropertyKey propertyKey : view.properties()) {

				// register field in view for entity class and declaring superclass
				registerPropertySet(field.getDeclaringClass(), view.name(), propertyKey);
				registerPropertySet(entityType, view.name(), propertyKey);
			}
		}
	}
	
	private static void registerProperty(Class type, PropertyKey propertyKey) {
		
		getClassDBNamePropertyMapForType(type).put(propertyKey.dbName(),   propertyKey);
		getClassJSNamePropertyMapForType(type).put(propertyKey.jsonName(), propertyKey);
		
		registerPropertySet(type, PropertyView.All, propertyKey);
	}
	
	/**
	 * Initialize the entity context with all classes from the module service,
	 */
	public static void init() {

		cachedEntities = Services.getService(ModuleService.class).getCachedNodeEntities();
	}

	/**
	 * Register a listener to receive notifications about modified entities.
	 * 
	 * @param listener the listener to register
	 */
	public static void registerTransactionListener(StructrTransactionListener listener) {
		transactionListeners.add(listener);
	}

	/**
	 * Unregister a transaction listener.
	 * 
	 * @param listener the listener to unregister
	 */
	public static void unregisterTransactionListener(StructrTransactionListener listener) {
		transactionListeners.remove(listener);
	}

	/**
	 * Register a transformation that will be applied to every newly created entity of a given type.
	 * 
	 * @param type the type of the entities for which the transformation should be applied
	 * @param transformation the transformation to apply on every entity
	 */
	public static void registerEntityCreationTransformation(Class type, Transformation<GraphObject> transformation) {
		getEntityCreationTransformationsForType(type).add(transformation);
	}

	/**
	 * Registers a property group for the given key of the given entity type. A property group can be
	 * used to combine a set of properties into an object. {@see PropertyGroup}
	 * 
	 * @param type the type of the entities for which the property group should be registered
	 * @param key the property key under which the property group should be visible
	 * @param propertyGroup the property group
	 */
	public static void registerPropertyGroup(Class type, PropertyKey key, PropertyGroup propertyGroup) {
		getPropertyGroupMapForType(type).put(key.dbName(), propertyGroup);
	}

	// ----- named relations -----
	/**
	 * Registers a relationship entity with the given name and type to be instantiated for relationships
	 * with type <code>relType</code> from <code>sourceType</code> to <code>destType</code>. Relationship
	 * entity types registered with this method will be instantiated when a database relationship with
	 * the given parameters are encountered.
	 * 
	 * @param relationName the name of the relationship as it should appear in the REST resource
	 * @param relationshipEntityType the type of the relationship entity
	 * @param sourceType the type of the source entity
	 * @param destType the type of the destination entity
	 * @param relType the relationship type
	 */
	public static void registerNamedRelation(String relationName, Class relationshipEntityType, Class sourceType, Class destType, RelationshipType relType) {

		globalRelationshipNameMap.put(relationName, new RelationshipMapping(relationName, sourceType, destType, relType));
		globalRelationshipClassMap.put(createCombinedRelationshipType(sourceType.getSimpleName(), relType.name(), destType.getSimpleName()), relationshipEntityType);
	}

	// ----- property and entity relationships -----
	/**
	 * Defines a non-cascading property relationship with the given property key, relationship type,
	 * direction and cardinality to exist between the source type and the destination type.
	 * 
	 * @param sourceType the source type
	 * @param propertyKey the property key
	 * @param destType the destination type
	 * @param relType the relationship type
	 * @param direction the direction of the relationship
	 * @param cardinality the cardinality of the relationship
	 */
	public static void registerPropertyRelation(Class sourceType, PropertyKey propertyKey, Class destType, RelationshipType relType, Direction direction, Cardinality cardinality) {
		registerPropertyRelation(sourceType, propertyKey, destType, relType, direction, cardinality, RelationClass.DELETE_NONE);
	}

	/**
	 * Defines a property relationship with the given property key, relationship type, direction,
	 * cardinality and cascading delete behaviour to exist between the source type and the destination
	 * type.
	 * 
	 * @param sourceType the source type
	 * @param propertyKey the property key
	 * @param destType the destination type
	 * @param relType the relationship type
	 * @param direction the direction of the relationship
	 * @param cardinality the cardinality of the relationship
	 * @param cascadeDelete the cascading delete behaviour
	 */
	public static void registerPropertyRelation(Class sourceType, PropertyKey property, Class destType, RelationshipType relType, Direction direction, Cardinality cardinality, int cascadeDelete) {

		// need to set type here
		Notion objectNotion = new ObjectNotion();

		objectNotion.setType(destType);
		registerPropertyRelationInternal(sourceType, property, destType, relType, direction, cardinality, objectNotion, cascadeDelete);
	}

	/**
	 * Defines a non-cascading property relationship with the given property key, relationship
	 * type, direction and cardinality to exist between the source type and the destination type,
	 * using the given notion.
	 * 
	 * @param sourceType the source type
	 * @param propertyKey the property key
	 * @param destType the destination type
	 * @param relType the relationship type
	 * @param direction the direction of the relationship
	 * @param cardinality the cardinality of the relationship
	 * @param notion the notion 
	 */
	public static void registerPropertyRelation(Class sourceType, PropertyKey propertyKey, Class destType, RelationshipType relType, Direction direction, Cardinality cardinality, Notion notion) {
		registerPropertyRelation(sourceType, propertyKey, destType, relType, direction, cardinality, notion, RelationClass.DELETE_NONE);
	}

	/**
	 * Defines a relationship with the given property key, relationship type, direction,
	 * cardinality and cascading delete behaviour to exist between the source type
	 * and the destination type, using the given notion.
	 * 
	 * @param sourceType
	 * @param property
	 * @param destType
	 * @param relType
	 * @param direction
	 * @param cardinality
	 * @param notion
	 * @param cascadeDelete 
	 */
	public static void registerPropertyRelation(Class sourceType, PropertyKey property, Class destType, RelationshipType relType, Direction direction, Cardinality cardinality, Notion notion,
		int cascadeDelete) {

		notion.setType(destType);
		registerPropertyRelationInternal(sourceType, property, destType, relType, direction, cardinality, notion, cascadeDelete);
	}

	/**
	 * Defines a non-cascading relationship of the given relationship type, direction and
	 * cardinality between entites of type <code>sourceType</code> and entities of type
	 * <code>destType</code>.
	 * 
	 * @param sourceType the type of the source entities
	 * @param destType the type of the destination entities
	 * @param relType the relationship type
	 * @param direction the direction
	 * @param cardinality the cardinality
	 */
	public static void registerEntityRelation(Class sourceType, Class destType, RelationshipType relType, Direction direction, Cardinality cardinality) {
		registerEntityRelation(sourceType, destType, relType, direction, cardinality, RelationClass.DELETE_NONE);
	}

	/**
	 * Defines a relationship of the given relationship type, direction, cardinality and
	 * cascading delete behaviour between entites of type <code>sourceType</code> and
	 * entities of type <code>destType</code>.
	 * 
	 * @param sourceType the type of the source entities
	 * @param destType the type of the destination entities
	 * @param relType the relationship type
	 * @param direction the direction
	 * @param cardinality the cardinality
	 * @param cascadeDelete the cascading delete behaviour
	 */
	public static void registerEntityRelation(Class sourceType, Class destType, RelationshipType relType, Direction direction, Cardinality cardinality, int cascadeDelete) {

		// need to set type here
		Notion objectNotion = new ObjectNotion();

		objectNotion.setType(destType);
		registerEntityRelationInternal(sourceType, destType, relType, direction, cardinality, objectNotion, cascadeDelete);
	}

	/**
	 * Defines a non-cascading relationship of the given relationship type, direction and
	 * cardinality between entites of type <code>sourceType</code> and entities of type
	 * <code>destType</code> using the given notion.
	 * 
	 * @param sourceType
	 * @param destType
	 * @param relType
	 * @param direction
	 * @param cardinality
	 * @param notion 
	 */
	public static void registerEntityRelation(Class sourceType, Class destType, RelationshipType relType, Direction direction, Cardinality cardinality, Notion notion) {
		registerEntityRelation(sourceType, destType, relType, direction, cardinality, notion, RelationClass.DELETE_NONE);
	}

	/**
	 * Defines a relationship of the given relationship type, direction, cardinality and
	 * cascading delete behaviour between entites of type <code>sourceType</code> and entities
	 * of type <code>destType</code> using the given notion.
	 * 
	 * @param sourceType
	 * @param destType
	 * @param relType
	 * @param direction
	 * @param cardinality
	 * @param notion
	 * @param cascadeDelete 
	 */
	public static void registerEntityRelation(Class sourceType, Class destType, RelationshipType relType, Direction direction, Cardinality cardinality, Notion notion, int cascadeDelete) {

		notion.setType(destType);
		registerEntityRelationInternal(sourceType, destType, relType, direction, cardinality, notion, cascadeDelete);
	}

	// ----- property set methods -----
	/**
	 * Registers the given set of property keys for the view with name <code>propertyView</code>
	 * and the given prefix of entities with the given type.
	 * 
	 * @param type the type of the entities for which the view will be registered
	 * @param propertyView the name of the property view for which the property set will be registered
	 * @param viewPrefix a string that will be prepended to all property keys in this view
	 * @param propertySet the set of property keys to register for the given view
	 */
	public static void registerPropertySet(Class type, String propertyView, PropertyKey... propertySet) {

		Map<String, Set<PropertyKey>> propertyViewMap = getPropertyViewMapForType(type);
		Set<PropertyKey> properties                   = propertyViewMap.get(propertyView);
		
		if (properties == null) {
			properties = new LinkedHashSet<PropertyKey>();
			propertyViewMap.put(propertyView, properties);
		}

		// add all properties from set
		properties.addAll(Arrays.asList(propertySet));
	}

	// ----- validator methods -----
	/**
	 * Registers a validator for the given property key of all entities of the given type.
	 * 
	 * @param type the type of the entities for which the validator should be registered
	 * @param propertyKey the property key under which the validator should be registered
	 * @param validatorClass the type of the validator to register
	 */
	public static void registerPropertyValidator(Class type, PropertyKey propertyKey, PropertyValidator validator) {

		Map<PropertyKey, Set<PropertyValidator>> validatorMap = getPropertyValidatorMapForType(type);

		// fetch or create validator set
		Set<PropertyValidator> validators = validatorMap.get(propertyKey);

		if (validators == null) {

			validators = new LinkedHashSet<PropertyValidator>();

			validatorMap.put(propertyKey, validators);

		}

		validators.add(validator);
		
		// Automatically register the property key as searchable key,
		// so that the uniqueness validators work
		
		if (AbstractNode.class.isAssignableFrom(type)) {
			EntityContext.registerSearchableProperty(type, NodeIndex.keyword.name(), propertyKey);
		} else if (AbstractRelationship.class.isAssignableFrom(type)) {
			EntityContext.registerSearchableProperty(type, RelationshipIndex.rel_keyword.name(), propertyKey);
		}
		
	}


	// ----- PropertyConverter methods -----
	/**
	 * Registers a property converter for the given property key of all entities with
	 * the given type.
	 * 
	 * @param type the type of the entities for which the converter should be registered
	 * @param propertyKey the property key under which the validator should be registered
	 * @param propertyConverterClass the type of the converter to register
	 */
//	public static void registerPropertyConverter(Class type, PropertyKey propertyKey, Class<? extends PropertyConverter> propertyConverterClass) {
//		registerPropertyConverter(type, propertyKey, propertyConverterClass, null);
//	}

	/**
	 * Registers a property converter for the given property key of all entities with
	 * the given type, using the given value.
	 * 
	 * @param type the type of the entities for which the converter should be registered
	 * @param propertyKey the property key under which the validator should be registered
	 * @param propertyConverterClass the type of the converter to register
	 */
//	public static void registerPropertyConverter(Class type, PropertyKey propertyKey, Class<? extends PropertyConverter> propertyConverterClass, Value value) {
//
//		getPropertyConverterMapForType(type).put(propertyKey, propertyConverterClass);
//
//		if (value != null) {
//
//			getPropertyConversionParameterMapForType(type).put(propertyKey, value);
//			globalKnownPropertyKeys.add(propertyKey);
//
//		}
//	}

	// ----- scanEntity-only property map -----
	/**
	 * Defines the given property of the given entity to be scanEntity-only.
	 * 
	 * @param type the type of the entities
	 * @param key the key that should be set scanEntity-only
	 */
//	public static void registerReadOnlyProperty(Class type, PropertyKey key) {
//		getReadOnlyPropertySetForType(type).add(key);
//	}

	// ----- searchable property map -----
	/**
	 * Registers the given set of properties of the given entity type to be stored
	 * in the given index.
	 * 
	 * @param type the entitiy type
	 * @param index the index
	 * @param keys the keys to be indexed
	 */
	public static void registerSearchablePropertySet(Class type, String index, PropertyKey... keys) {

		for (PropertyKey key : keys) {

			registerSearchableProperty(type, index, key);

		}
	}

	/**
	 * Registers the given property of the given entity type to be stored
	 * in the given index.
	 * 
	 * @param type the entitiy type
	 * @param index the index
	 * @param key the key to be indexed
	 */
	public static void registerSearchableProperty(Class type, String index, PropertyKey key) {

		Map<String, Set<PropertyKey>> searchablePropertyMapForType = getSearchablePropertyMapForType(type);
		Set<PropertyKey> searchablePropertySet                     = searchablePropertyMapForType.get(index);

		if (searchablePropertySet == null) {

			searchablePropertySet = new LinkedHashSet<PropertyKey>();

			searchablePropertyMapForType.put(index, searchablePropertySet);

		}

		key.registerSearchableProperties(searchablePropertySet);
	}

	// ----- write-once property map -----
	/**
	 * Defines the given property of the given type to be able to be written only
	 * once. This is useful for system properties like uuid etc.
	 * 
	 * @param type the entity type
	 * @param key the key to be set to write-once
	 */
//	public static void registerWriteOnceProperty(Class type, PropertyKey key) {
//		getWriteOncePropertySetForType(type).add(key);
//	}

	// ----- private methods -----
	/**
	 * Tries to normalize (and singularize) the given string so that it resolves to
	 * an existing entity type.
	 * 
	 * @param possibleEntityString
	 * @return the normalized entity name in its singular form
	 */
	public static String normalizeEntityName(String possibleEntityString) {

		if (possibleEntityString == null) {

			return null;

		}
		
		if ("/".equals(possibleEntityString)) {
			
			return "/";
			
		}

                StringBuilder result = new StringBuilder();
                
		if (possibleEntityString.contains("/")) {

			String[] names           = StringUtils.split(possibleEntityString, "/");

			for (String possibleEntityName : names) {

				// CAUTION: this cache might grow to a very large size, as it
				// contains all normalized mappings for every possible
				// property key / entity name that is ever called.
				String normalizedType = normalizedEntityNameCache.get(possibleEntityName);

				if (normalizedType == null) {

					normalizedType = StringUtils.capitalize(CaseHelper.toUpperCamelCase(possibleEntityName));

					if (normalizedType.endsWith("ies")) {

						normalizedType = normalizedType.substring(0, normalizedType.length() - 3).concat("y");

					} else if (!normalizedType.endsWith("ss") && normalizedType.endsWith("s")) {

						logger.log(Level.FINEST, "Removing trailing plural 's' from type {0}", normalizedType);

						normalizedType = normalizedType.substring(0, normalizedType.length() - 1);

					}

					// logger.log(Level.INFO, "String {0} normalized to {1}", new Object[] { possibleEntityName, normalizedType });
					normalizedEntityNameCache.put(possibleEntityName, normalizedType);

				}

				result.append(normalizedType).append("/");

			}

			return StringUtils.removeEnd(result.toString(), "/");

		} else {

//                      CAUTION: this cache might grow to a very large size, as it
			// contains all normalized mappings for every possible
			// property key / entity name that is ever called.
			String normalizedType = normalizedEntityNameCache.get(possibleEntityString);

			if (normalizedType == null) {

				normalizedType = StringUtils.capitalize(CaseHelper.toUpperCamelCase(possibleEntityString));

				if (normalizedType.endsWith("ies")) {

					normalizedType = normalizedType.substring(0, normalizedType.length() - 3).concat("y");

				} else if (!normalizedType.endsWith("ss") && normalizedType.endsWith("s")) {

					logger.log(Level.FINEST, "Removing trailing plural 's' from type {0}", normalizedType);

					normalizedType = normalizedType.substring(0, normalizedType.length() - 1);

				}

				// logger.log(Level.INFO, "String {0} normalized to {1}", new Object[] { possibleEntityName, normalizedType });
				normalizedEntityNameCache.put(possibleEntityString, normalizedType);

			}

			return normalizedType;
		}
	}
	
	/**
	 * Converts a Java class name (entity name) into a valid REST resource name.
	 * 
	 * @param normalizedEntityName
	 * @return the given string in lowercase, with camel case occurences replaced by underscores
	 */
	public static String denormalizeEntityName(String normalizedEntityName) {
		
		StringBuilder buf = new StringBuilder();
		
		for (char c : normalizedEntityName.toCharArray()) {
			
			if (Character.isUpperCase(c) && buf.length() > 0) {
				buf.append("_");
			}
			
			buf.append(Character.toLowerCase(c));
		}
		
		return buf.toString();
	}

	private static void registerPropertyRelationInternal(Class sourceType, PropertyKey property, Class destType, RelationshipType relType, Direction direction, Cardinality cardinality, Notion notion,
		int cascadeDelete) {

		Map<PropertyKey, RelationClass> typeMap = getPropertyRelationshipMapForType(sourceType);

		typeMap.put(property, new RelationClass(destType, relType, direction, cardinality, notion, cascadeDelete));
		globalKnownPropertyKeys.add(property);
	}

	private static void registerEntityRelationInternal(Class sourceType, Class destType, RelationshipType relType, Direction direction, Cardinality cardinality, Notion notion, int cascadeDelete) {

		Map<Class, RelationClass> typeMap = getRelationClassMapForType(sourceType);
		RelationClass directedRelation    = new RelationClass(destType, relType, direction, cardinality, notion, cascadeDelete);

		typeMap.put(destType, directedRelation);
	}
	
	public static String createCombinedRelationshipType(String oldCombinedRelationshipType, Class newDestType) {

		String[] parts = getPartsOfCombinedRelationshipType(oldCombinedRelationshipType);

		return createCombinedRelationshipType(parts[0], parts[1], newDestType.getSimpleName());
	}

	public static String createCombinedRelationshipType(Class sourceType, RelationshipType relType, Class destType) {
		return createCombinedRelationshipType(sourceType.getSimpleName(), relType.name(), destType.getSimpleName());
	}

	public static String createCombinedRelationshipType(String sourceType, String relType, String destType) {

		StringBuilder buf = new StringBuilder();

		buf.append(sourceType);
		buf.append(COMBINED_RELATIONSHIP_KEY_SEP);
		buf.append(relType);
		buf.append(COMBINED_RELATIONSHIP_KEY_SEP);
		buf.append(destType);

		return buf.toString();
	}
	
	public static void registerConvertedProperty(PropertyKey propertyKey) {
		globalKnownPropertyKeys.add(propertyKey);
	}

	//~--- get methods ----------------------------------------------------

	public static Class getEntityClassForRawType(final String rawType) {

		String normalizedEntityName = normalizeEntityName(rawType);

		if (cachedEntities.containsKey(normalizedEntityName)) {

			return (Class) cachedEntities.get(normalizedEntityName);

		}
		
		if (reverseInterfaceMap.containsKey(normalizedEntityName)) {

			return (Class) reverseInterfaceMap.get(normalizedEntityName);

		}

		return null;
	}

	public static RelationshipMapping getNamedRelation(String relationName) {
		return globalRelationshipNameMap.get(relationName);
	}
	
	private static Class getSourceType(final String combinedRelationshipType) {

		String sourceType = getPartsOfCombinedRelationshipType(combinedRelationshipType)[0];
		Class realType  = getEntityClassForRawType(sourceType);

//		try {
//			realType = (Class) Services.command(null, GetEntityClassCommand.class).update(StringUtils.capitalize(sourceType));
//		} catch (FrameworkException ex) {
//			logger.log(Level.WARNING, "No real type found for {0}", sourceType);
//		}

		return realType;
	}
	
	private static RelationshipType getRelType(final String combinedRelationshipType) {
		String relType = getPartsOfCombinedRelationshipType(combinedRelationshipType)[1];
		return DynamicRelationshipType.withName(relType);
	}

	private static Class getDestType(final String combinedRelationshipType) {

		String destType = getPartsOfCombinedRelationshipType(combinedRelationshipType)[2];
		Class realType  = getEntityClassForRawType(destType);

//		try {
//			realType = (Class) Services.command(null, GetEntityClassCommand.class).update(StringUtils.capitalize(destType));
//		} catch (FrameworkException ex) {
//			logger.log(Level.WARNING, "No real type found for {0}", destType);
//		}

		return realType;
	}

	private static String[] getPartsOfCombinedRelationshipType(final String combinedRelationshipType) {
		return StringUtils.split(combinedRelationshipType, COMBINED_RELATIONSHIP_KEY_SEP);
	}

	public static Class getNamedRelationClass(String sourceType, String destType, String relType) {
		return getNamedRelationClass(createCombinedRelationshipType(sourceType, relType, destType));
	}

	public static Class getNamedRelationClass(String combinedRelationshipType) {

		Class sourceType         = getSourceType(combinedRelationshipType);
		Class destType           = getDestType(combinedRelationshipType);
		RelationshipType relType = getRelType(combinedRelationshipType);
		
		return getNamedRelationClass(sourceType, destType, relType);

	}

	public static Class getNamedRelationClass(Class sourceType, Class destType, RelationshipType relType) {

		Class namedRelationClass = null;
		Class sourceSuperClass   = sourceType;
		Class destSuperClass     = destType;

		while ((namedRelationClass == null) &&!sourceSuperClass.equals(Object.class) &&!destSuperClass.equals(Object.class)) {

			namedRelationClass = globalRelationshipClassMap.get(createCombinedRelationshipType(sourceSuperClass, relType, destSuperClass));

			// check interfaces of dest class
			if (namedRelationClass == null) {
				
				for(Class interfaceClass : getInterfacesForType(destSuperClass)) {
					
					namedRelationClass = globalRelationshipClassMap.get(createCombinedRelationshipType(sourceSuperClass, relType, interfaceClass));
					if(namedRelationClass != null) {
						break;
					}
				}
			}
			// do not check superclass for source type
			// sourceSuperClass = sourceSuperClass.getSuperclass();
			// one level up
			destSuperClass = destSuperClass.getSuperclass();

		}

		if (namedRelationClass != null) {

			return namedRelationClass;

		}

		return globalRelationshipClassMap.get(createCombinedRelationshipType(sourceType, relType, destType));
	}

	public static Collection<RelationshipMapping> getNamedRelations() {
		return globalRelationshipNameMap.values();
	}

	public static Set<StructrTransactionListener> getTransactionListeners() {
		return transactionListeners;
	}

	public static Set<Transformation<GraphObject>> getEntityCreationTransformations(Class type) {

		Set<Transformation<GraphObject>> transformations = new TreeSet<Transformation<GraphObject>>();
		Class localType                                  = type;

		// collect for all superclasses
		while (localType != null && !localType.equals(Object.class)) {

			transformations.addAll(getEntityCreationTransformationsForType(localType));

			// FIXME: include interfaces as well??
			
			localType = localType.getSuperclass();

		}

//              return new TreeSet<Transformation<AbstractNode>>(transformations).descendingSet();
		return transformations;
	}

	// ----- property notions -----
	public static PropertyGroup getPropertyGroup(Class type, PropertyKey key) {
		return getPropertyGroup(type, key.dbName());
	}

	public static PropertyGroup getPropertyGroup(Class type, String key) {

		PropertyGroup group = getAggregatedPropertyGroupMapForType(type).get(key);
		if(group == null) {
			
			Class localType     = type;

			while(group == null && localType != null && !localType.equals(Object.class)) {

				group = getPropertyGroupMapForType(localType).get(key);

				if(group == null) {

					// try interfaces as well
					for(Class interfaceClass : getInterfacesForType(localType)) {

						group = getPropertyGroupMapForType(interfaceClass).get(key);
						if(group != null) {
							break;
						}
					}
				}

				localType = localType.getSuperclass();
			}
			
			getAggregatedPropertyGroupMapForType(type).put(key, group);
		}
		
		return group;
	}

	// ----- static relationship methods -----
	public static RelationClass getRelationClass(Class sourceType, Class destType) {

		RelationClass relation = null;
		Class localType        = sourceType;

		while ((relation == null) && localType != null && !localType.equals(Object.class)) {

			relation  = getRelationClassMapForType(localType).get(destType);

			// check source type interfaces after source supertypes!
			if(relation == null) {

				// try interfaces as well
				for(Class interfaceClass : getInterfacesForType(localType)) {

					relation = getRelationClassMapForType(interfaceClass).get(destType);
					if(relation != null) {
						break;
					}
				}
			}

			localType = localType.getSuperclass();
		}

		// Check dest type superclasses
		if (relation == null) {

			localType = destType;

			while ((relation == null) && localType != null && !localType.equals(Object.class)) {

				relation  = getRelationClassMapForType(sourceType).get(localType);

				// check dest type interfaces after dest type
				if(relation == null) {

					// try interfaces as well
					for(Class interfaceClass : getInterfacesForType(localType)) {

						relation = getRelationClassMapForType(sourceType).get(interfaceClass);
						if(relation != null) {
							break;
						}
					}
				}

				localType = localType.getSuperclass();
			}
		}
			

		return relation;
	}

	public static RelationClass getRelationClass(Class sourceType, PropertyKey key) {

		Class destType = getEntityClassForRawType(key.dbName());

		if (destType != null) {

			return getRelationClass(sourceType, destType);

		}

		return getRelationClassForProperty(sourceType, key);
	}

	public static RelationClass getRelationClassForProperty(Class sourceType, PropertyKey propertyKey) {

		RelationClass relation = null;
		Class localType        = sourceType;

		while ((relation == null) && localType != null && !localType.equals(Object.class)) {

			relation  = getPropertyRelationshipMapForType(localType).get(propertyKey);

			// try interfaces classes
			if (relation == null) {

				for(Class interfaceClass : getInterfacesForType(localType)) {

					relation  = getPropertyRelationshipMapForType(interfaceClass).get(propertyKey);

					if(relation != null) {

						return relation;
					}
				}
			}

			localType = localType.getSuperclass();
		}

		return relation;
	}
	
	// ----- view transformations -----
	public static void registerViewTransformation(Class type, String view, ViewTransformation transformation) {
		getViewTransformationMapForType(type).put(view, transformation);
	}
	
	public static ViewTransformation getViewTransformation(Class type, String view) {
		return getViewTransformationMapForType(type).get(view);
	}
	
	private static Map<String, ViewTransformation> getViewTransformationMapForType(Class type) {
		
		Map<String, ViewTransformation> viewTransformationMap = viewTransformations.get(type);
		if(viewTransformationMap == null) {
			viewTransformationMap = new LinkedHashMap<String, ViewTransformation>();
			viewTransformations.put(type, viewTransformationMap);
		}
		
		return viewTransformationMap;
	}
	

	// ----- property set methods -----
	public static Set<String> getPropertyViews() {

		Set<String> views = new LinkedHashSet<String>();
		
		// add all existing views
		for (Map<String, Set<PropertyKey>> view : globalPropertyViewMap.values()) {
			views.addAll(view.keySet());
		}
		
		return Collections.unmodifiableSet(views);
	}
	
	public static Set<PropertyKey> getPropertySet(Class type, String propertyView) {

		Map<String, Set<PropertyKey>> propertyViewMap = getPropertyViewMapForType(type);
		Set<PropertyKey> properties                   = propertyViewMap.get(propertyView);

		if (properties == null) {
			properties = new LinkedHashSet<PropertyKey>();
		}
		
		// read-only
		return Collections.unmodifiableSet(properties);
	}
	
	public static PropertyKey getPropertyKeyForDatabaseName(Class type, String dbName) {

		Map<String, PropertyKey> classDBNamePropertyMap = getClassDBNamePropertyMapForType(type);
		PropertyKey key                                 = classDBNamePropertyMap.get(dbName);
		
		if (key == null) {
			
			// first try: uuid
			if (GraphObject.uuid.dbName().equals(dbName)) {
				return GraphObject.uuid;
			}
			
			key = new GenericProperty(dbName);
		}
		
		return key;
	}
	
	public static PropertyKey getPropertyKeyForJSONName(Class type, String jsonName) {

		Map<String, PropertyKey> classJSNamePropertyMap = getClassJSNamePropertyMapForType(type);
		PropertyKey key                                 = classJSNamePropertyMap.get(jsonName);
		
		if (key == null) {
			
			// first try: uuid
			if (GraphObject.uuid.dbName().equals(jsonName)) {
				return GraphObject.uuid;
			}

			key = new GenericProperty(jsonName);
		}
		
		return key;
	}

	public static Set<PropertyValidator> getPropertyValidators(final SecurityContext securityContext, Class type, PropertyKey propertyKey) {

		Set<PropertyValidator> validators                     = new LinkedHashSet<PropertyValidator>();
		Map<PropertyKey, Set<PropertyValidator>> validatorMap = null;
		Class localType                                       = type;

		// try all superclasses
		while (localType != null && !localType.equals(Object.class)) {

			validatorMap = getPropertyValidatorMapForType(localType);
			
			Set<PropertyValidator> classValidators = validatorMap.get(propertyKey);
			if(classValidators != null) {
				validators.addAll(validatorMap.get(propertyKey));
			}

			// try converters from interfaces as well
			for(Class interfaceClass : getInterfacesForType(localType)) {
				Set<PropertyValidator> interfaceValidators = getPropertyValidatorMapForType(interfaceClass).get(propertyKey);
				if(interfaceValidators != null) {
					validators.addAll(interfaceValidators);
				}
			}
			
//                      logger.log(Level.INFO, "Validator class {0} found for type {1}", new Object[] { clazz != null ? clazz.getSimpleName() : "null", localType } );
			// one level up :)
			localType = localType.getSuperclass();

		}

		return validators;
	}

//	public static PropertyConverter getPropertyConverter(final SecurityContext securityContext, Class type, PropertyKey propertyKey) {
//
//		Class clazz = getAggregatedPropertyConverterMapForType(type).get(propertyKey);
//		if(clazz == null) {
//			
//			Map<PropertyKey, Class<? extends PropertyConverter>> converterMap = null;
//			Class localType                                                   = type;
//
//			while ((clazz == null) && localType != null && !localType.equals(Object.class)) {
//
//				converterMap = getPropertyConverterMapForType(localType);
//				clazz        = converterMap.get(propertyKey);
//
//				// try converters from interfaces as well
//				if(clazz == null) {
//
//					for(Class interfaceClass : getInterfacesForType(localType)) {
//						clazz = getPropertyConverterMapForType(interfaceClass).get(propertyKey);
//						if(clazz != null) {
//							break;
//						}
//					}
//				}
//
//				localType = localType.getSuperclass();
//			}
//			
//			getAggregatedPropertyConverterMapForType(type).put(propertyKey, clazz);
//		}
//
//		PropertyConverter propertyConverter = null;
//		if (clazz != null) {
//
//			try {
//
//				propertyConverter = (PropertyConverter) clazz.newInstance();
//
//				propertyConverter.setSecurityContext(securityContext);
//
//			} catch (Throwable t) {
//				logger.log(Level.WARNING, "Unable to instantiate property PropertyConverter {0}: {1}", new Object[] { clazz.getName(), t.getMessage() });
//			}
//
//		}
//
//		return propertyConverter;
//	}
//
//	public static Value getPropertyConversionParameter(Class type, PropertyKey propertyKey) {
//
//		Map<PropertyKey, Value> conversionParameterMap = null;
//		Class localType                                = type;
//		Value value                                    = null;
//
//		while ((value == null) && localType != null && !localType.equals(Object.class)) {
//
//			conversionParameterMap = getPropertyConversionParameterMapForType(localType);
//			value                  = conversionParameterMap.get(propertyKey);
//
//			// try parameters from interfaces as well
//			if(value == null) {
//
//				for(Class interfaceClass : getInterfacesForType(localType)) {
//					value = getPropertyConversionParameterMapForType(interfaceClass).get(propertyKey);
//					if(value != null) {
//						break;
//					}
//				}
//			}
//			
////                      logger.log(Level.INFO, "Conversion parameter value {0} found for type {1}", new Object[] { value != null ? value.getClass().getSimpleName() : "null", localType } );
//			localType = localType.getSuperclass();
//
//		}
//
//		return value;
//	}

	public static Set<PropertyKey> getSearchableProperties(Class type, String index) {

		Set<PropertyKey> searchablePropertyMap = getSearchablePropertyMapForType(type).get(index);
		if (searchablePropertyMap == null) {

			searchablePropertyMap = new HashSet<PropertyKey>();
		}

		return searchablePropertyMap;
	}

	private static Map<PropertyKey, RelationClass> getPropertyRelationshipMapForType(Class sourceType) {

		Map<PropertyKey, RelationClass> typeMap = globalPropertyRelationClassMap.get(sourceType);

		if (typeMap == null) {

			typeMap = new LinkedHashMap<PropertyKey, RelationClass>();

			globalPropertyRelationClassMap.put(sourceType, typeMap);

		}

		return (typeMap);
	}

	private static Map<Class, RelationClass> getRelationClassMapForType(Class sourceType) {

		Map<Class, RelationClass> typeMap = globalRelationClassMap.get(sourceType);

		if (typeMap == null) {

			typeMap = new LinkedHashMap<Class, RelationClass>();

			globalRelationClassMap.put(sourceType, typeMap);

		}

		return (typeMap);
	}

	private static Map<String, Set<PropertyKey>> getPropertyViewMapForType(Class type) {

		Map<String, Set<PropertyKey>> propertyViewMap = globalPropertyViewMap.get(type);

		if (propertyViewMap == null) {

			propertyViewMap = new LinkedHashMap<String, Set<PropertyKey>>();

			globalPropertyViewMap.put(type, propertyViewMap);

		}

		return propertyViewMap;
	}

	private static Map<String, PropertyKey> getClassDBNamePropertyMapForType(Class type) {

		Map<String, PropertyKey> classDBNamePropertyMap = globalClassDBNamePropertyMap.get(type);

		if (classDBNamePropertyMap == null) {

			classDBNamePropertyMap = new LinkedHashMap<String, PropertyKey>();

			globalClassDBNamePropertyMap.put(type, classDBNamePropertyMap);

		}

		return classDBNamePropertyMap;
	}

	private static Map<String, PropertyKey> getClassJSNamePropertyMapForType(Class type) {

		Map<String, PropertyKey> classJSNamePropertyMap = globalClassJSNamePropertyMap.get(type);

		if (classJSNamePropertyMap == null) {

			classJSNamePropertyMap = new LinkedHashMap<String, PropertyKey>();

			globalClassJSNamePropertyMap.put(type, classJSNamePropertyMap);

		}

		return classJSNamePropertyMap;
	}

	private static Map<PropertyKey, Set<PropertyValidator>> getPropertyValidatorMapForType(Class type) {

		Map<PropertyKey, Set<PropertyValidator>> validatorMap = globalValidatorMap.get(type);

		if (validatorMap == null) {

			validatorMap = new LinkedHashMap<PropertyKey, Set<PropertyValidator>>();

			globalValidatorMap.put(type, validatorMap);

		}

		return validatorMap;
	}

	private static Map<PropertyKey, Class<? extends PropertyConverter>> getAggregatedPropertyConverterMapForType(Class type) {

		Map<PropertyKey, Class<? extends PropertyConverter>> PropertyConverterMap = globalAggregatedPropertyConverterMap.get(type);

		if (PropertyConverterMap == null) {

			PropertyConverterMap = new LinkedHashMap<PropertyKey, Class<? extends PropertyConverter>>();

			globalAggregatedPropertyConverterMap.put(type, PropertyConverterMap);

		}

		return PropertyConverterMap;
	}

	private static Map<PropertyKey, Class<? extends PropertyConverter>> getPropertyConverterMapForType(Class type) {

		Map<PropertyKey, Class<? extends PropertyConverter>> PropertyConverterMap = globalPropertyConverterMap.get(type);

		if (PropertyConverterMap == null) {

			PropertyConverterMap = new LinkedHashMap<PropertyKey, Class<? extends PropertyConverter>>();

			globalPropertyConverterMap.put(type, PropertyConverterMap);

		}

		return PropertyConverterMap;
	}

	private static Map<PropertyKey, Value> getPropertyConversionParameterMapForType(Class type) {

		Map<PropertyKey, Value> conversionParameterMap = globalConversionParameterMap.get(type);

		if (conversionParameterMap == null) {

			conversionParameterMap = new LinkedHashMap<PropertyKey, Value>();

			globalConversionParameterMap.put(type, conversionParameterMap);

		}

		return conversionParameterMap;
	}

//	private static Set<PropertyKey> getReadOnlyPropertySetForType(Class type) {
//
//		Set<PropertyKey> readOnlyPropertySet = globalReadOnlyPropertyMap.get(type);
//
//		if (readOnlyPropertySet == null) {
//
//			readOnlyPropertySet = new LinkedHashSet<PropertyKey>();
//
//			globalReadOnlyPropertyMap.put(type, readOnlyPropertySet);
//
//		}
//
//		return readOnlyPropertySet;
//	}

//	private static Set<PropertyKey> getWriteOncePropertySetForType(Class type) {
//
//		Set<PropertyKey> writeOncePropertySet = globalWriteOncePropertyMap.get(type);
//
//		if (writeOncePropertySet == null) {
//
//			writeOncePropertySet = new LinkedHashSet<PropertyKey>();
//
//			globalWriteOncePropertyMap.put(type, writeOncePropertySet);
//
//		}
//
//		return writeOncePropertySet;
//	}

	private static Map<String, Set<PropertyKey>> getSearchablePropertyMapForType(Class type) {

		Map<String, Set<PropertyKey>> searchablePropertyMap = globalSearchablePropertyMap.get(type);

		if (searchablePropertyMap == null) {

			searchablePropertyMap = new LinkedHashMap<String, Set<PropertyKey>>();

			globalSearchablePropertyMap.put(type, searchablePropertyMap);

		}

		return searchablePropertyMap;
	}

	private static Map<String, PropertyGroup> getAggregatedPropertyGroupMapForType(Class type) {

		Map<String, PropertyGroup> groupMap = globalAggregatedPropertyGroupMap.get(type);

		if (groupMap == null) {

			groupMap = new LinkedHashMap<String, PropertyGroup>();

			globalAggregatedPropertyGroupMap.put(type, groupMap);

		}

		return groupMap;
	}

	private static Map<String, PropertyGroup> getPropertyGroupMapForType(Class type) {

		Map<String, PropertyGroup> groupMap = globalPropertyGroupMap.get(type);

		if (groupMap == null) {

			groupMap = new LinkedHashMap<String, PropertyGroup>();

			globalPropertyGroupMap.put(type, groupMap);

		}

		return groupMap;
	}

	private static Set<Transformation<GraphObject>> getEntityCreationTransformationsForType(Class type) {

		Set<Transformation<GraphObject>> transformations = globalEntityCreationTransformationMap.get(type);

		if (transformations == null) {

			transformations = new LinkedHashSet<Transformation<GraphObject>>();

			globalEntityCreationTransformationMap.put(type, transformations);

		}

		return transformations;
	}
	
	public static Set<Class> getInterfacesForType(Class type) {
		
		Set<Class> interfaces = interfaceMap.get(type);
		if(interfaces == null) {
			
			interfaces = new LinkedHashSet<Class>();
			interfaceMap.put(type, interfaces);
			
			for(Class iface : type.getInterfaces()) {

				reverseInterfaceMap.put(iface.getSimpleName(), iface);
				interfaces.add(iface);
			}
		}
		
		return interfaces;
	}
	
	public static EntityContextModificationListener getTransactionEventHandler() {
		return globalModificationListener;
	}

	public static synchronized FrameworkException getFrameworkException(Long transactionKey) {
		return exceptionMap.get(transactionKey);
	}

	public static boolean isKnownProperty(final PropertyKey key) {
		return globalKnownPropertyKeys.contains(key);
	}

//	public static boolean isReadOnlyProperty(Class type, PropertyKey key) {
//
//		boolean isReadOnly = getReadOnlyPropertySetForType(type).contains(key);
//		Class localType    = type;
//
//		// try all superclasses
//		while (!isReadOnly && localType != null && !localType.equals(Object.class)) {
//
//			isReadOnly = getReadOnlyPropertySetForType(localType).contains(key);
//
//			// one level up :)
//			localType = localType.getSuperclass();
//
//		}
//		
//		if(!isReadOnly) {
//			
//			for(Class interfaceClass : getInterfacesForType(type)) {
//
//				if (getReadOnlyPropertySetForType(interfaceClass).contains(key)) {
//					return true;
//				}
//				
//				
//			}
//		}
//
//		return isReadOnly;
//	}

	public static boolean isSearchableProperty(Class type, String index, PropertyKey key) {
		
		boolean isSearchable = getSearchableProperties(type, index).contains(key);
		
		return isSearchable;
	}

//	public static boolean isWriteOnceProperty(Class type, PropertyKey key) {
//
//		boolean isWriteOnce = getWriteOncePropertySetForType(type).contains(key);
//		Class localType     = type;
//
//		// try all superclasses
//		while (!isWriteOnce && localType != null && !localType.equals(Object.class)) {
//
//			isWriteOnce = getWriteOncePropertySetForType(localType).contains(key);
//
//			// one level up :)
//			localType = localType.getSuperclass();
//
//		}
//		
//		if(!isWriteOnce) {
//			
//			for(Class interfaceClass : getInterfacesForType(type)) {
//
//				if (getWriteOncePropertySetForType(interfaceClass).contains(key)) {
//					return true;
//				}
//				
//				
//			}
//		}
//
//		return isWriteOnce;
//	}
	
	public static GenericFactory getGenericFactory() {
		return genericFactory;
	}
	
	public static void registerGenericFactory(GenericFactory factory) {
		genericFactory = factory;
	}

	public static synchronized TransactionChangeSet getTransactionChangeSet(long transactionKey) {
		return globalChangeSets.get(transactionKey);
	}
	
	public static synchronized void clearTransactionData(long transactionKey) {

		securityContextMap.remove();
		transactionKeyMap.remove();
		globalChangeSets.remove(transactionKey);
	}
	
	public static synchronized void setSecurityContext(SecurityContext securityContext) {
		securityContextMap.set(securityContext);
	}

	public static synchronized void setTransactionKey(Long transactionKey) {
		transactionKeyMap.set(transactionKey);
	}
	
	//~--- inner classes --------------------------------------------------

	// <editor-fold defaultstate="collapsed" desc="EntityContextModificationListener">
	public static class EntityContextModificationListener implements TransactionEventHandler<Long> {

		// ----- interface TransactionEventHandler -----
		@Override
		public Long beforeCommit(TransactionData data) throws Exception {

			Long transactionKeyValue = transactionKeyMap.get();
			
			if (transactionKeyValue == null) {
				return -1L;
			}
			
			long transactionKey = transactionKeyMap.get();
			
			// check if node service is ready
			if (!Services.isReady(NodeService.class)) {

				logger.log(Level.WARNING, "Node service is not ready yet.");

				return transactionKey;

			}

			SecurityContext securityContext                   = securityContextMap.get();
			SecurityContext superUserContext                  = SecurityContext.getSuperUserInstance();
			// TEST AM IndexNodeCommand indexNodeCommand                 = Services.command(superUserContext, IndexNodeCommand.class);
			IndexRelationshipCommand indexRelationshipCommand = Services.command(superUserContext, IndexRelationshipCommand.class);

			try {

				Map<Relationship, Map<String, Object>> removedRelProperties = new LinkedHashMap<Relationship, Map<String, Object>>();
				Map<Node, Map<String, Object>> removedNodeProperties        = new LinkedHashMap<Node, Map<String, Object>>();
				RelationshipFactory relFactory                              = new RelationshipFactory(securityContext);
				TransactionChangeSet changeSet                              = new TransactionChangeSet();
				ErrorBuffer errorBuffer                                     = new ErrorBuffer();
				NodeFactory nodeFactory                                     = new NodeFactory(securityContext);
				boolean hasError                                            = false;
				
				// store change set
				globalChangeSets.put(transactionKey, changeSet);

				// notify transaction listeners
				for (StructrTransactionListener listener : EntityContext.getTransactionListeners()) {
					listener.begin(securityContext, transactionKey);
				}

				// 1.1: collect properties of deleted nodes
				for (PropertyEntry<Node> entry : data.removedNodeProperties()) {

					Node node                       = entry.entity();
					Map<String, Object> propertyMap = removedNodeProperties.get(node);

					if (propertyMap == null) {

						propertyMap = new LinkedHashMap<String, Object>();

						removedNodeProperties.put(node, propertyMap);

					}

					propertyMap.put(entry.key(), entry.previouslyCommitedValue());

					if (!data.isDeleted(node)) {

						AbstractNode modifiedNode = nodeFactory.createNode(node, true, false);
						if (modifiedNode != null) {

							PropertyKey key = getPropertyKeyForDatabaseName(modifiedNode.getClass(), entry.key());
							
							// only send modification events for non-system properties
							if (!key.isSystemProperty()) {
								changeSet.nonSystemProperty();
							}
							
							changeSet.modify(modifiedNode);

							// notify registered listeners
							for (StructrTransactionListener listener : EntityContext.getTransactionListeners()) {
								hasError |= !listener.propertyRemoved(securityContext, transactionKey, errorBuffer, modifiedNode, key, entry.previouslyCommitedValue());
							}
						}

					}
				}

				// 1.2: collect properties of deleted relationships
				for (PropertyEntry<Relationship> entry : data.removedRelationshipProperties()) {

					Relationship rel                = entry.entity();
					Map<String, Object> propertyMap = removedRelProperties.get(rel);

					if (propertyMap == null) {

						propertyMap = new LinkedHashMap<String, Object>();

						removedRelProperties.put(rel, propertyMap);

					}

					propertyMap.put(entry.key(), entry.previouslyCommitedValue());

					if (!data.isDeleted(rel)) {

						AbstractRelationship modifiedRel = relFactory.instantiateRelationship(securityContext, rel);
						if (modifiedRel != null) {
							
							PropertyKey key = getPropertyKeyForDatabaseName(modifiedRel.getClass(), entry.key());
							
							// only send modification events for non-system properties
							if (!key.isSystemProperty()) {
								changeSet.nonSystemProperty();
							}
							
							changeSet.modify(modifiedRel);

							// notify registered listeners
							for (StructrTransactionListener listener : EntityContext.getTransactionListeners()) {
								hasError |= !listener.propertyRemoved(securityContext, transactionKey, errorBuffer, modifiedRel, key, entry.previouslyCommitedValue());
							}
						}
					}

				}
						
				// 2: notify listeners of node creation (so the modifications can later be tracked)
				for (Node node : sortNodes(data.createdNodes())) {

					AbstractNode entity = nodeFactory.createNode(node, true, false);
					if (entity != null) {
						
						hasError |= !entity.beforeCreation(securityContext, errorBuffer);
						
						changeSet.create(entity);
						
						// notify registered listeners
						for (StructrTransactionListener listener : EntityContext.getTransactionListeners()) {
							hasError |= !listener.graphObjectCreated(securityContext, transactionKey, errorBuffer, entity);
						}
					}

				}

				// 3: notify listeners of relationship creation
				for (Relationship rel : sortRelationships(data.createdRelationships())) {

					AbstractRelationship entity = relFactory.instantiateRelationship(securityContext, rel);
					if (entity != null) {
						
						hasError |= !entity.beforeCreation(securityContext, errorBuffer);
						
						changeSet.create(entity);
						
						// notify registered listeners
						for (StructrTransactionListener listener : EntityContext.getTransactionListeners()) {
							hasError |= !listener.graphObjectCreated(securityContext, transactionKey, errorBuffer, entity);
						}
						
// ****************************************************** TEST
						
						try {
							AbstractNode startNode = nodeFactory.createNode(rel.getStartNode());
							RelationshipType relationshipType = entity.getRelType();
							
							if (startNode != null && !data.isDeleted(rel.getStartNode())) {
								
								changeSet.modifyRelationshipEndpoint(startNode, relationshipType);
							}
							
							AbstractNode endNode = nodeFactory.createNode(rel.getEndNode());
							if (endNode != null && !data.isDeleted(rel.getEndNode())) {
								
								changeSet.modifyRelationshipEndpoint(endNode, relationshipType);
							}
							
						} catch(Throwable t) {}
					}

				}

				// 4: notify listeners of relationship deletion
				for (Relationship rel : data.deletedRelationships()) {

					AbstractRelationship entity = relFactory.instantiateRelationship(securityContext, rel);
					if (entity != null) {
						
						// convertFromInput properties
						PropertyMap properties = PropertyMap.databaseTypeToJavaType(securityContext, entity, removedRelProperties.get(rel));
						
						hasError |= !entity.beforeDeletion(securityContext, errorBuffer, properties);
						
						// notify registered listeners
						for (StructrTransactionListener listener : EntityContext.getTransactionListeners()) {
							hasError |= !listener.graphObjectDeleted(securityContext, transactionKey, errorBuffer, entity, properties);
						}

						changeSet.delete(entity);

// ****************************************************** TEST
						try {
							AbstractNode startNode = nodeFactory.createNode(rel.getStartNode());
							RelationshipType relationshipType = entity.getRelType();

							if (startNode != null && !data.isDeleted(rel.getStartNode())) {

								changeSet.modifyRelationshipEndpoint(startNode, relationshipType);
							}
							
							AbstractNode endNode = nodeFactory.createNode(rel.getEndNode());
							if (endNode != null && !data.isDeleted(rel.getEndNode())) {
								
								changeSet.modifyRelationshipEndpoint(endNode, relationshipType);
							}
							
						} catch(Throwable ignore) {}
					}

				}

				// 5: notify listeners of node and relationship deletion
				for (Node node : data.deletedNodes()) {
					
					logger.log(Level.FINEST, "Node deleted: {0}", node.getId());

					String type = (String)removedNodeProperties.get(node).get(AbstractNode.type.dbName());
					AbstractNode entity = nodeFactory.createDummyNode(type);
					
					if (entity != null) {
						
						PropertyMap properties = PropertyMap.databaseTypeToJavaType(securityContext, entity, removedNodeProperties.get(node));
						
						hasError |= !entity.beforeDeletion(securityContext, errorBuffer, properties);
						
						// notify registered listeners
						for(StructrTransactionListener listener : EntityContext.getTransactionListeners()) {
							hasError |= !listener.graphObjectDeleted(securityContext, transactionKey, errorBuffer, entity, properties);
						}

						changeSet.delete(entity);
					}
				}

				Node n = null;
				AbstractNode nodeEntity = null;
				
				// 6: validate property modifications and
				// notify listeners of property removal and modifications
				for (PropertyEntry<Node> entry : data.assignedNodeProperties()) {
					
					// performance optimization: don't instantiate new AbstractRelationship on each property but only if entity has changed
					Node nodeFromPropertyEntry = entry.entity();
					
					if (!(nodeFromPropertyEntry.equals(n))) {
						nodeEntity = nodeFactory.createNode(nodeFromPropertyEntry, true, false);
						n = nodeFromPropertyEntry;
					}

					
					if (nodeEntity != null) {
						
						PropertyKey key  = getPropertyKeyForDatabaseName(nodeEntity.getClass(), entry.key());
						Object value     = entry.value();
						
						// only send modification events for non-system properties
						if (!key.isSystemProperty()) {
							changeSet.nonSystemProperty();
						}

						// iterate over validators
						// FIXME: synthetic property key
						Set<PropertyValidator> validators = EntityContext.getPropertyValidators(securityContext, nodeEntity.getClass(), key);

						if (validators != null) {

							for (PropertyValidator validator : validators) {

								hasError |= !(validator.isValid(nodeEntity, key, value, errorBuffer));

							}

						}
						
						// notify registered listeners
						for (StructrTransactionListener listener : EntityContext.getTransactionListeners()) {
							hasError |= !listener.propertyModified(securityContext, transactionKey, errorBuffer, nodeEntity, key, entry.previouslyCommitedValue(), value);
						}

						// after successful validation, add node to index to make uniqueness constraints work
						
						if (!changeSet.isNewOrDeleted(nodeEntity)) {
							
							// TEST AM indexNodeCommand.update(nodeEntity, key);
							changeSet.modify(nodeEntity);
						}

					}
				}

				Relationship r = null;
				AbstractRelationship relEntity = null;
				
				for (PropertyEntry<Relationship> entry : data.assignedRelationshipProperties()) {
					
					// performance optimization: don't instantiate new AbstractRelationship on each property but only if entity has changed
					Relationship relFromPropertyEntry = entry.entity();
					
					if (!(relFromPropertyEntry.equals(r))) {
						relEntity = relFactory.instantiateRelationship(securityContext, relFromPropertyEntry);
						r = relFromPropertyEntry;
					}
					
					if (relEntity != null) {
						
						PropertyKey key = getPropertyKeyForDatabaseName(relEntity.getClass(), entry.key());
						Object value    = entry.value();

						// only send modification events for non-system properties
						if (!key.isSystemProperty()) {
							changeSet.nonSystemProperty();
						}

						// iterate over validators
						// FIXME: synthetic property key
						Set<PropertyValidator> validators = EntityContext.getPropertyValidators(securityContext, relEntity.getClass(), key);

						if (validators != null) {

							for (PropertyValidator validator : validators) {

								hasError |= !(validator.isValid(relEntity, key, value, errorBuffer));

							}

						}
						
						// notify registered listeners
						for (StructrTransactionListener listener : EntityContext.getTransactionListeners()) {
							hasError |= !listener.propertyModified(securityContext, transactionKey, errorBuffer, relEntity, key, entry.previouslyCommitedValue(), value);
						}
						
						// after successful validation, add relationship to index to make uniqueness constraints work
						//if (!createdRels.contains(entity) && !deletedRels.contains(entity)) {
							indexRelationshipCommand.execute(relEntity, key);
						//}
						
						changeSet.modify(relEntity);
					}
				}

				// 7: notify listeners of modified nodes (to check for non-existing properties etc)
				for (AbstractNode node : changeSet.getModifiedNodes()) {

					// only send UPDATE and index if node was not created or deleted in this transaction
					if (!changeSet.isNewOrDeleted(node)) {

						hasError |= !node.beforeModification(securityContext, errorBuffer);
						
						// notify registered listeners
						for (StructrTransactionListener listener : EntityContext.getTransactionListeners()) {
							hasError |= !listener.graphObjectModified(securityContext, transactionKey, errorBuffer, node);
						}
						
						// TEST AM indexNodeCommand.update(node);
					}
				}
				
				for (AbstractRelationship rel : changeSet.getModifiedRelationships()) {

					// only send UPDATE if relationship was not created or deleted in this transaction
					if (!changeSet.isNewOrDeleted(relEntity)) {

						hasError |= !rel.beforeModification(securityContext, errorBuffer);
						
						// notify registered listeners
						for (StructrTransactionListener listener : EntityContext.getTransactionListeners()) {
							hasError |= !listener.graphObjectModified(securityContext, transactionKey, errorBuffer, rel);
						}
						
						indexRelationshipCommand.execute(rel);
						
					}
					
				}

				for (AbstractNode node : changeSet.getCreatedNodes()) {

					// TEST AM indexNodeCommand.update(node);

				}

				for (AbstractRelationship rel : changeSet.getCreatedRelationships()) {

					indexRelationshipCommand.execute(rel);

				}

				if (hasError) {

					for (StructrTransactionListener listener : EntityContext.getTransactionListeners()) {
						listener.rollback(securityContext, transactionKey);
					}

					throw new FrameworkException(422, errorBuffer);

				}

				for (StructrTransactionListener listener : EntityContext.getTransactionListeners()) {
					listener.commit(securityContext, transactionKey);
				}
				
				
			} catch (FrameworkException fex) {

				exceptionMap.put(transactionKey, fex);

				throw new IllegalStateException("Rollback");
			}

			return transactionKey;
		}

		@Override
		public void afterCommit(TransactionData data, Long transactionKey) {}

		@Override
		public void afterRollback(TransactionData data, Long transactionKey) {

			Throwable t = exceptionMap.get(transactionKey);

			if (t != null) {

				// thow
				throw new IllegalArgumentException(t);
			}
		}
	}
	// </editor-fold>
	
	private static ArrayList<Node> sortNodes(final Iterable<Node> it) {
		
		ArrayList<Node> list = new ArrayList<Node>();
		
		for (Node p : it) {
			
			list.add(p);
			
		}
		
		
		Collections.sort(list, new Comparator<Node>() {

			@Override
			public int compare(Node o1, Node o2) {
				Long id1 = o1.getId();
				Long id2 = o2.getId();
				return id1.compareTo(id2);
			}
		});
		
		return list;
		
	}
	
	private static ArrayList<Relationship> sortRelationships(final Iterable<Relationship> it) {
		
		ArrayList<Relationship> list = new ArrayList<Relationship>();
		
		for (Relationship p : it) {
			
			list.add(p);
			
		}
		
		
		Collections.sort(list, new Comparator<Relationship>() {

			@Override
			public int compare(Relationship o1, Relationship o2) {
				Long id1 = o1.getId();
				Long id2 = o2.getId();
				return id1.compareTo(id2);
			}
		});
		
		return list;
		
	}
	
	private static <T> Map<Field, T> getFieldValuesOfType(Class<T> entityType, Object entity) {
		
		Map<Field, T> fields   = new LinkedHashMap<Field, T>();
		Set<Class<?>> allTypes = getAllTypes(entity.getClass());
		
		for (Class<?> type : allTypes) {
			
			for (Field field : type.getDeclaredFields()) {

				if (entityType.isAssignableFrom(field.getType())) {

					try {
						fields.put(field, (T)field.get(entity));

					} catch(Throwable t) { }
				}
			}
		}
		
		return fields;
	}
	
	private static Set<Class<?>> getAllTypes(Class<?> type) {

		Set<Class<?>> types = new LinkedHashSet<Class<?>>();
		Class localType     = type;
			
		do {
			
			collectAllInterfaces(localType, types);
			types.add(localType);
			
			localType = localType.getSuperclass();

		} while (!localType.equals(Object.class));
		
		return types;
	}
	
	private static void collectAllInterfaces(Class<?> type, Set<Class<?>> interfaces) {

		if (interfaces.contains(type)) {
			return;
		}
		
		for (Class iface : type.getInterfaces()) {
			
			collectAllInterfaces(iface, interfaces);
			interfaces.add(iface);
		}
	}
	
	private static class ThreadLocalChangeSet extends ThreadLocal<TransactionChangeSet> {
		@Override
		protected TransactionChangeSet initialValue() {
			return new TransactionChangeSet();
		}
	}
}
