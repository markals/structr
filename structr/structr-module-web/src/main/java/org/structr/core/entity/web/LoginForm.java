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
package org.structr.core.entity.web;

import java.util.Map;
import org.structr.common.RenderMode;
import org.structr.core.NodeRenderer;
import org.structr.renderer.LoginFormRenderer;

/**
 * Render a login form.
 *
 * Username and password field names have to match with a corresponding
 * LoginCheck node to make login work.
 *
 * @author axel
 */
public class LoginForm extends Form {

    public final static String defaultUsernameFieldName = "loginForm_username";
    public final static String defaultPasswordFieldName = "loginForm_password";
    public final static String defaultSubmitButtonName = "loginForm_submit";
    public final static String defaultAntiRobotFieldName = "loginForm_antiRobot";
    /** Name of username field */
    public final static String USERNAME_FIELD_NAME_KEY = "usernameFieldName";
    /** Name of password field */
    public final static String PASSWORD_FIELD_NAME_KEY = "passwordFieldName";

    private final static String ICON_SRC = "/images/form.png";

    @Override
    public String getIconSrc() {
        return ICON_SRC;
    }

    /**
     * Return name of username field
     *
     * @return
     */
    public String getUsernameFieldName() {
        return getStringProperty(USERNAME_FIELD_NAME_KEY);
    }

    /**
     * Return name of password field
     *
     * @return
     */
    public String getPasswordFieldName() {
        return getStringProperty(PASSWORD_FIELD_NAME_KEY);
    }


    /**
     * Set name of username field
     *
     * @param value
     */
    public void setUsernameFieldName(final String value) {
        setProperty(USERNAME_FIELD_NAME_KEY, value);
    }

    /**
     * Set name of password field
     *
     * @param value
     */
    public void setPasswordFieldName(final String value) {
        setProperty(PASSWORD_FIELD_NAME_KEY, value);
    }

    @Override
    public void initializeRenderers(Map<RenderMode, NodeRenderer> renderers)
    {
	    renderers.put(RenderMode.Default, new LoginFormRenderer());
    }
}
