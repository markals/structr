/*
 *  Copyright (C) 2011 Axel Morgner, structr <structr@structr.org>
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
package org.structr.core.entity;

import java.util.Map;
import java.util.logging.Logger;
import org.structr.common.RenderMode;
import org.structr.common.renderer.SubnodeTemplateRenderer;
import org.structr.core.NodeRenderer;

/**
 * 
 * @author amorgner
 * 
 */
public class PlainText extends AbstractNode {

    private final static String ICON_SRC = "/images/page_white_text.png";

    @Override
    public String getIconSrc() {
        return ICON_SRC;
    }
    public final static String CONTENT_KEY = "content";
    public final static String CONTENT_TYPE_KEY = "contentType";
    public final static String SIZE_KEY = "size";

    public String getContent() {
        return (String) getProperty(CONTENT_KEY);
    }

    public void setContent(final String content) {
        setProperty(CONTENT_KEY, content);
    }

    @Override
    public String getContentType() {
        return (String) getProperty(CONTENT_TYPE_KEY);
    }

    public void setContentType(final String contentType) {
        setProperty(CONTENT_TYPE_KEY, contentType);
    }

    public String getSize() {
        return (String) getProperty(SIZE_KEY);
    }

    public void setSize(final String size) {
        setProperty(SIZE_KEY, size);
    }

    @Override
    public void initializeRenderers(Map<RenderMode, NodeRenderer> renderers)
    {
	    renderers.put(RenderMode.Default, new SubnodeTemplateRenderer());
    }

    /**
     * Stream content directly to output.
     *
     * @param out
    @Override
    public void renderNode(StructrOutputStream out, final AbstractNode startNode,
            final String editUrl, final Long editNodeId) {

        if (isVisible()) {

            try {

                StringBuilder sb = new StringBuilder();
                renderNode(	null,startNode, editUrl, editNodeId);

                // write to output stream
                IOUtils.write(sb.toString(), out);

            } catch (IOException e) {
                logger.log(Level.WARNING, "Error while rendering {0}: {1}", new String[]{getContent(), e.getMessage()});
            }
        }
    }
     */

    @Override
    public void onNodeCreation()
    {
    }

    @Override
    public void onNodeInstantiation()
    {
    }
}
