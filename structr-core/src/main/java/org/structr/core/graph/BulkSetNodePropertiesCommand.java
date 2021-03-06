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



package org.structr.core.graph;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.tooling.GlobalGraphOperations;

import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.Result;
import org.structr.core.Services;
import org.structr.core.entity.AbstractNode;
import org.structr.core.graph.search.Search;
import org.structr.core.graph.search.SearchAttribute;
import org.structr.core.graph.search.SearchNodeCommand;

//~--- JDK imports ------------------------------------------------------------

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.structr.core.EntityContext;
import org.structr.core.property.PropertyKey;

//~--- classes ----------------------------------------------------------------

/**
 * Sets the properties found in the property set on all nodes matching the type.
 * If no type property is found, set the properties on all nodes.
 *
 * @author Axel Morgner
 */
public class BulkSetNodePropertiesCommand extends NodeServiceCommand implements MaintenanceCommand {

	private static final Logger logger = Logger.getLogger(BulkSetNodePropertiesCommand.class.getName());

	//~--- methods --------------------------------------------------------

	@Override
	public void execute(final Map<String, Object> properties) throws FrameworkException {

		final GraphDatabaseService graphDb     = (GraphDatabaseService) arguments.get("graphDb");
		final SecurityContext superUserContext = SecurityContext.getSuperUserInstance();
		final NodeFactory nodeFactory          = new NodeFactory(superUserContext);
		final SearchNodeCommand searchNode     = Services.command(superUserContext, SearchNodeCommand.class);

		if (graphDb != null) {

			Result<AbstractNode> nodes = null;

			if (properties.containsKey(AbstractNode.type.dbName())) {

				List<SearchAttribute> attrs = new LinkedList<SearchAttribute>();

				attrs.add(Search.andExactType((String) properties.get(AbstractNode.type.dbName())));

				nodes = searchNode.execute(attrs);

				properties.remove(AbstractNode.type.dbName());

			} else {

				nodes = nodeFactory.createAllNodes(GlobalGraphOperations.at(graphDb).getAllNodes());
			}


			long nodeCount = bulkGraphOperation(securityContext, nodes.getResults(), 1000, "SetNodeProperties", new BulkGraphOperation<AbstractNode>() {

				@Override
				public void handleGraphObject(SecurityContext securityContext, AbstractNode node) {

					// Treat only "our" nodes
					if (node.getProperty(AbstractNode.uuid) != null) {

						for (Entry entry : properties.entrySet()) {

							String key = (String) entry.getKey();
							Object val = entry.getValue();

							PropertyKey propertyKey = EntityContext.getPropertyKeyForDatabaseName(node.getClass(), key);
							if (propertyKey != null) {

								try {
									node.unlockReadOnlyPropertiesOnce();
									node.setProperty(propertyKey, val);
									
								} catch (FrameworkException fex) {

									logger.log(Level.WARNING, "Unable to set node property {0} of node {1} to {2}: {3}", new Object[] { propertyKey, node.getUuid(), val, fex.getMessage() } );
									
								}
							}

						}

					}
				}

				@Override
				public void handleThrowable(SecurityContext securityContext, Throwable t, AbstractNode node) {
					logger.log(Level.WARNING, "Unable to set properties of node {0}: {1}", new Object[] { node.getUuid(), t.getMessage() } );
				}

				@Override
				public void handleTransactionFailure(SecurityContext securityContext, Throwable t) {
					logger.log(Level.WARNING, "Unable to set node properties: {0}", t.getMessage() );
				}
			});


			logger.log(Level.INFO, "Fixed {0} nodes ...", nodeCount);
		}

		logger.log(Level.INFO, "Done");
	}

}
